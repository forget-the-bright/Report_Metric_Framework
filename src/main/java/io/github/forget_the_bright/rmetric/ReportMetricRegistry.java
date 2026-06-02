
package io.github.forget_the_bright.rmetric;

import cn.hutool.core.util.ReflectUtil;
import io.github.forget_the_bright.rmetric.model.Tuple;
import io.github.forget_the_bright.rmetric.exception.ReportMetricException;
import io.github.forget_the_bright.rmetric.annotation.ReportMetric;
import io.github.forget_the_bright.rmetric.annotation.ReportMetricOperation;
import io.github.forget_the_bright.rmetric.common.MetricDataManager;
import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 报表指标注册器
 *
 * <p>自动扫描@ReportMetric标注的Bean,注册指标配置并提供查询功能</p>
 *
 * @author wanghao(helloworlwh @ 163.com)
 * @since 2026/4/24 09:48
 */
@Configuration
public class ReportMetricRegistry {

    private MetricDataManager metricDataManager;
    private Map<String, Object> reportMetricBeans;

    /**
     * 构造函数 - 扫描并注册所有指标
     *
     * @param applicationContext Spring应用上下文
     */
    @Autowired
    public ReportMetricRegistry(ApplicationContext applicationContext, MetricDataManager metricDataManager) {
        // 获取所有被@ReportMetric标注的Bean实例
        reportMetricBeans = applicationContext.getBeansWithAnnotation(ReportMetric.class);
        this.metricDataManager = metricDataManager;
        this.scan(reportMetricBeans);
    }

    /**
     * 重新扫描并注册所有报表指标
     *
     * <p>清空现有的指标注册表、重名检查提供者和搜索映射，然后重新扫描所有标注了@ReportMetric的Bean，
     * 重新构建指标配置并注册到系统中。</p>
     *
     * <p>该方法适用于需要动态刷新指标配置的场景，例如在运行时更新了指标定义后调用。</p>
     */
    public void reScan() {
        metricDataManager.getRegistry().clear();
        metricDataManager.getCheckSameNameProvider().clear();
        metricDataManager.getSearchMap().clear();
        this.scan(reportMetricBeans);
    }

    /**
     * 扫描Bean并注册指标配置
     *
     * @param beans 标注了@ReportMetric的Bean映射表
     */
    private void scan(Map<String, Object> beans) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Map<Object, List<ReportMetricConfig>> registry = metricDataManager.getRegistry();
        Map<String, Object> checkSameNameProvider = metricDataManager.getCheckSameNameProvider();
        for (Object provider : beans.values()) {
            // 检查报表名称是否重复
            ReportMetric reportMetricAnnotation = provider.getClass().getAnnotation(ReportMetric.class);
            Object existProvider = checkSameNameProvider.get(reportMetricAnnotation.reportName());
            ReportMetricException.throwByFlag(existProvider != null,
                    "报表指标提供者-报表名称 {} 重复, {} - {} 的报表名称相同,请检查!!!",
                    reportMetricAnnotation.reportName(),
                    existProvider == null ? "" : existProvider.getClass().getName(),
                    provider.getClass().getName()
            );
            checkSameNameProvider.put(reportMetricAnnotation.reportName(), provider);

            // 注册指标配置
            List<ReportMetricConfig> configs = new ArrayList<>();
            registry.put(provider, configs);

            // 遍历所有标注了@ReportMetricOperation的函数
            Arrays.stream(ReflectUtil.getMethods(provider.getClass()))
                    .filter(method -> method.isAnnotationPresent(ReportMetricOperation.class))
                    .map(method -> Tuple.newTuple(method, method.getAnnotation(ReportMetricOperation.class)))
                    .sorted(Comparator.comparingLong(tuple -> tuple.getSecond().sort()))
                    .forEach(tuple -> {
                        //解构Tuple
                        Method method = tuple.getFirst();
                        ReportMetricOperation annotation = tuple.getSecond();

                        //校验hanlder函数返回参数类型
                        ReportMetricException.throwByFlag(!isReturnListReportMetricData(method),
                                "手动指标 {} , {}.{} 返回参数类型不正确, 不是 List<ReportMetricData> 类型,请检查",
                                annotation.code(),
                                provider.getClass().getSimpleName(),
                                method.getName()
                        );

                        //校验hanlder函数参数列表参数数量
                        int parameterCount = method.getParameterCount();
                        ReportMetricException.throwByFlag(parameterCount != 2,
                                "手动指标 {} 查询参数个数不等于2个,请检查",
                                annotation.code());

                        // 获取hanlder函数参数列表
                        MethodHandle methodHandle = getMethodHandle(lookup, method);

                        //校验报表指标编码是否重复
                        ReportMetricConfig reportMetricConfigExist = metricDataManager.get(annotation.code());
                        ReportMetricException.throwByFlag(
                                reportMetricConfigExist != null,
                                "指标编码 {} 出现重复 , {}.{} - {}.{} 指标编码相同,请检查！！！",
                                annotation.code(),
                                reportMetricConfigExist == null ? "" : reportMetricConfigExist.getSourceClass().getName(),
                                reportMetricConfigExist == null ? "" : reportMetricConfigExist.getQueryMethod().getName(),
                                provider.getClass().getName(),
                                method.getName()
                        );

                        //包装指标hanlerConfig
                        ReportMetricConfig reportMetricConfig = metricDataManager
                                .buildConfigByAnnon(reportMetricAnnotation, annotation, method, methodHandle, provider);
                        configs.add(reportMetricConfig);
                        metricDataManager.put(reportMetricConfig.getCode(), reportMetricConfig);
                    });
        }
    }

    /**
     * 通过反射方法创建方法句柄
     *
     * <p>使用提供的 {@link MethodHandles.Lookup} 对象将反射 {@link Method} 转换为
     * {@link MethodHandle}，用于后续高性能的方法调用。</p>
     *
     * @param lookup 方法查找对象，具有调用者的访问权限
     * @param method 需要转换的反射方法对象
     * @return 转换后的方法句柄，可用于直接调用目标方法
     * @throws RuntimeException 当方法的访问权限不足时抛出（如私有方法无法访问）
     */
    private static MethodHandle getMethodHandle(MethodHandles.Lookup lookup, Method method) {
        MethodHandle methodHandle = null;
        try {
            methodHandle = lookup.unreflect(method);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return methodHandle;
    }


    /**
     * 判断方法返回值 是否是 List<ReportMetricData>
     *
     * @param method 反射方法
     * @return true=精确匹配 List<ReportMetricData>
     */
    private static boolean isReturnListReportMetricData(Method method) {
        // 1. 先判断原始返回类型是不是 List
        Class<?> returnType = method.getReturnType();
        if (!List.class.isAssignableFrom(returnType)) {
            return false;
        }
        // 2. 获取带泛型的完整返回Type
        Type genericReturnType = method.getGenericReturnType();
        // 3. 必须是参数化泛型类型（List<T>）
        if (!(genericReturnType instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterizedType = (ParameterizedType) genericReturnType;
        // 4. 获取泛型里的实际元素类型
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (actualTypeArguments.length == 0) {
            return false;
        }
        // 5. 强转Class，判断是不是 ReportMetricData
        Type itemType = actualTypeArguments[0];
        if (!(itemType instanceof Class<?>)) {
            return false;
        }
        Class<?> itemCls = (Class<?>) itemType;
        return itemCls == ReportMetricData.class;
    }
}