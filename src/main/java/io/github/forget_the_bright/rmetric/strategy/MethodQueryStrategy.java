package io.github.forget_the_bright.rmetric.strategy;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import io.github.forget_the_bright.rmetric.ReportMetricMonitor;
import io.github.forget_the_bright.rmetric.cache.MetricCacheManager;
import io.github.forget_the_bright.rmetric.cache.MetricLockManager;
import io.github.forget_the_bright.rmetric.common.MetricDataManager;
import io.github.forget_the_bright.rmetric.common.MetricThreadManager;
import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricData;
import io.github.forget_the_bright.rmetric.model.ReportMetricResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 方法查询策略实现
 *
 * <p>通过反射或 MethodHandle 调用开发者自定义的指标查询方法，
 * 支持并行查询、缓存管理、细粒度锁控制。</p>
 *
 * <p>适用场景：复杂业务逻辑需要自定义 Java 方法实现的指标查询。</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/28 10:24
 */
@Slf4j
@Component
public class MethodQueryStrategy implements QueryStrategy {

    @Autowired
    private MetricCacheManager cacheManager;

    @Autowired
    private MetricLockManager lockManager;

    @Autowired
    private MetricDataManager metricDataManager;

    @Autowired
    private MetricThreadManager metricThreadManager;

    @Autowired
    private ReportMetricMonitor metricMonitor;

    /**
     * 判断是否支持该配置
     *
     * @param config 指标配置
     * @return true=支持（未配置 dbTableName 时走方法查询）
     */
    @Override
    public boolean supports(ReportMetricConfig config) {
        return StrUtil.isEmpty(config.getDbTableName());
    }

    /**
     * 执行方法查询
     *
     * <p>利用自定义 ForkJoinPool 的线程继承特性，使并行流在隔离线程池中执行，
     * 避免污染 Tomcat 公共线程池。</p>
     *
     * @param configs  指标配置列表
     * @param start    开始时间
     * @param end      结束时间
     * @param useCache 是否使用缓存
     * @return 指标查询结果映射（Key: 指标编码, Value: 查询结果）
     */
    @Override
    public Map<String, ReportMetricResult> query(List<ReportMetricConfig> configs, Date start, Date end, Boolean useCache) {
        /* 空列表快速返回 */
        if (configs == null || configs.isEmpty()) {
            return new HashMap<>();
        }

        /*
         * 利用 ForkJoinPool 线程继承特性：
         * 外层 execute() 提交任务到 metricQueryPool，
         * 内层 parallelStream() 自动继承该线程池的工作线程。
         */
        Map<String, ReportMetricResult> methodResults = metricThreadManager.execute(() ->
                configs.parallelStream().collect(
                        Collectors.toMap(
                                ReportMetricConfig::getCode,
                                config -> {
                                    try {
                                        /* 执行单个指标查询 */
                                        return querySingle(config, start, end, useCache);
                                    } catch (Exception e) {
                                        /* 记录查询失败 */
                                        metricMonitor.recordQueryError();
                                        /* 兜底捕获：处理 querySingle() 方法体之外的异常 */
                                        String message = StrUtil.format("方法指标 {} 查询异常: {}", config.getCode(), e.getMessage());
                                        log.error(message, e);
                                        return metricDataManager.buildErrorResult(config, e);
                                    }
                                }
                        )
                )
        );
        return methodResults;
    }

    /**
     * 查询单个指标
     *
     * <p>执行流程：
     * <ol>
     *   <li>检查缓存（无锁）</li>
     *   <li>获取细粒度锁</li>
     *   <li>双重检查缓存（有锁，防击穿）</li>
     *   <li>执行方法调用（MethodHandle 优先）</li>
     *   <li>写入缓存并返回结果</li>
     * </ol>
     * </p>
     *
     * @param config   指标配置
     * @param start    开始时间
     * @param end      结束时间
     * @param useCache 是否使用缓存
     * @return 指标查询结果
     */
    private ReportMetricResult querySingle(ReportMetricConfig config, Date start, Date end, Boolean useCache) {
        String code = config.getCode();
        String redisKey = cacheManager.buildRedisKey(code, start, end);

        /* 第一次缓存检查（无锁，快速路径） */
        if (useCache != null && useCache) {
            ReportMetricResult cacheResult = cacheManager.get(redisKey);
            if (cacheResult != null) {
                return cacheResult;
            }
        }

        /* 获取指标级细粒度锁（基于 Caffeine 缓存的锁对象） */
        Object lock = lockManager.getMethodLock(code);

        synchronized (lock) {
            /* 双重检查锁：防止并发请求重复查询 */
            if (useCache != null && useCache) {
                ReportMetricResult cacheResult = cacheManager.get(redisKey);
                if (cacheResult != null) {
                    return cacheResult;
                }
            }

            /* 获取目标实例和方法句柄 */
            Object sourceInstance = config.getSourceInstance();
            Method queryMethod = config.getQueryMethod();
            MethodHandle queryMethodHandle = config.getQueryMethodHandle();
            List<ReportMetricData> invoke;

            try {
                /* DEBUG 级别日志：避免生产环境日志泛滥 */
                log.debug("{}执行指标方法,当前查询指标编码：({})", (useCache != null && useCache) ? "缓存未查询到数据," : "", code);
                /* 记录 Method 查询次数 */
                metricMonitor.recordMethodQuery();
                /* 优先使用 MethodHandle（性能优于反射） */
                if (queryMethodHandle != null) {
                    invoke = (List<ReportMetricData>) queryMethodHandle.invoke(sourceInstance,
                            DateUtil.format(start, config.getTimeFormat()),
                            DateUtil.format(end, config.getTimeFormat()));
                } else {
                    /* 降级使用 Hutool 反射工具 */
                    invoke = ReflectUtil.invoke(sourceInstance, queryMethod,
                            DateUtil.format(start, config.getTimeFormat()),
                            DateUtil.format(end, config.getTimeFormat()));
                }
            } catch (Throwable e) {
                /*
                 * 捕获 Throwable 而非 Exception：
                 * 1. MethodHandle.invoke() 声明抛出 Throwable
                 * 2. 用户自定义方法可能抛出 Error（如 StackOverflowError、NoClassDefFoundError）
                 * 3. ForkJoinPool 并行场景需最大化容错，避免单任务崩溃影响全局
                 */
                /* 记录查询失败 */
                metricMonitor.recordQueryError();
                String message = StrUtil.format("执行查询指标方法出现错误,当前错误CODE值: {} ,错误信息: {} ", code, e.getMessage());
                log.error(message, e);
                return metricDataManager.buildErrorResult(config, message);
            }

            /* 构建标准结果对象 */
            ReportMetricResult result = metricDataManager.buildValueStruct(config, invoke);

            /* 写入缓存（使用默认过期时间） */
            cacheManager.set(redisKey, result);
            return result;
        }
    }

    /**
     * 预览方法查询信息
     *
     * <p>生成指标配置对应的方法调用信息，用于预览该指标是通过哪个类的哪个方法进行查询的。</p>
     *
     * @param value 指标配置列表
     * @param start 开始时间（此方法中未使用）
     * @param end   结束时间（此方法中未使用）
     * @return 方法预览映射（Key: 指标编码, Value: 类名.方法名）
     */
    @Override
    public Map<String, Object> previewProcessQuery(List<ReportMetricConfig> value, Date start, Date end) {
        Map<String, Object> collect = value.stream()
                .collect(
                        Collectors.toMap(ReportMetricConfig::getCode,
                                config ->
                                        StrUtil.format("{}.{}", config.getSourceClass().getName(), config.getQueryMethod().getName())
                        ));
        return collect;
    }
}
