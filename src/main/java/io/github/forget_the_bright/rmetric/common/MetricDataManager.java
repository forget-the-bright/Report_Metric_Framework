package io.github.forget_the_bright.rmetric.common;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import io.github.forget_the_bright.rmetric.annotation.ReportMetric;
import io.github.forget_the_bright.rmetric.annotation.ReportMetricOperation;
import io.github.forget_the_bright.rmetric.exception.ReportMetricException;
import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricData;
import io.github.forget_the_bright.rmetric.model.ReportMetricResult;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表指标数据管理器
 *
 * <p>负责管理启动时注册的所有指标配置，提供以下功能：</p>
 * <ul>
 *   <li><b>配置存储</b>：维护指标编码到配置的映射关系</li>
 *   <li><b>快速检索</b>：通过指标编码 O(1) 时间复杂度获取配置</li>
 *   <li><b>元数据导出</b>：将指标配置转换为可读格式，支持导出 Excel</li>
 *   <li><b>分组统计</b>：按报表名称分组，便于分类展示</li>
 * </ul>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/28 11:45
 */
@Component
public class MetricDataManager {


    private Set<String> dataSourceNames;
    private String primaryDataSource;

    public MetricDataManager(DynamicRoutingDataSource dynamicRoutingDataSource) {
        this.dataSourceNames = dynamicRoutingDataSource.getDataSources().keySet();
        this.primaryDataSource = ReflectUtil.invoke(dynamicRoutingDataSource, "getPrimary");
    }

    /**
     * 指标注册表（Key: Bean 实例, Value: 指标配置列表）
     *
     * <p>用于记录每个指标提供者（Provider Bean）关联的所有指标配置，
     * 便于后续按提供者维度进行管理和维护。</p>
     */
    private final Map<Object, List<ReportMetricConfig>> registry = new LinkedHashMap<>();

    /**
     * 指标搜索映射表（Key: 指标编码, Value: 指标配置）
     *
     * <p>核心索引结构，支持通过指标编码快速检索配置信息。</p>
     */
    private final Map<String, ReportMetricConfig> searchMap = new LinkedHashMap<>();

    /**
     * 同名提供者检查映射表（Key: 提供者名称, Value: 提供者实例）
     *
     * <p>用于检测和管理具有相同名称的指标提供者，确保在注册过程中不会出现命名冲突。</p>
     */
    private final Map<String, Object> checkSameNameProvider = new HashMap<>();


    /**
     * 获取指标注册表
     *
     * @return 注册表映射（Key: Bean 实例, Value: 指标配置列表）
     */
    public Map<Object, List<ReportMetricConfig>> getRegistry() {
        return registry;
    }

    /**
     * 获取指标搜索映射表
     *
     * @return 搜索映射表（Key: 指标编码, Value: 指标配置）
     */
    public Map<String, ReportMetricConfig> getSearchMap() {
        return searchMap;
    }

    /**
     * 获取同名提供者检查映射表
     *
     * <p>返回用于检测命名冲突的提供者实例映射表，便于外部进行提供者名称的唯一性校验。</p>
     *
     * @return 同名提供者检查映射表（Key: 提供者名称, Value: 提供者实例）
     */
    public Map<String, Object> getCheckSameNameProvider() {
        return checkSameNameProvider;
    }

    /**
     * 获取所有指标配置
     *
     * @return 所有指标配置集合
     */
    public Collection<ReportMetricConfig> getAll() {
        return searchMap.values();
    }

    /**
     * 根据编码获取指标配置
     *
     * @param code 指标编码
     * @return 指标配置对象（不存在时返回 null）
     */
    public ReportMetricConfig get(String code) {
        return searchMap.get(code);
    }

    public ReportMetricConfig put(String code, ReportMetricConfig config) {
        return searchMap.put(code, config);
    }


    /**
     * 按报表名称分组构建指标元数据
     *
     * <p>将所有指标按来源报表进行分组，便于按报表维度导出多 Sheet Excel。</p>
     *
     * @return 分组后的指标元数据（Key: 报表名称, Value: 该报表下的指标列表）
     */
    public Map<String, List<Map<String, Object>>> buildMutilReportDataToMap() {
        /* 先构建所有指标的扁平列表，再按"来源报表"分组 */
        Map<String, List<Map<String, Object>>> groupBySourceReport = buildAllDataToMap()
                .stream()
                .collect(Collectors.groupingBy(map -> map.get("来源报表").toString()));
        return groupBySourceReport;
    }

    /**
     * 构建所有指标的元数据列表（用于导出）
     *
     * <p>将指标配置转换为键值对格式，便于生成 Excel 表格。</p>
     *
     * @return 指标元数据列表（每个元素为 LinkedHashMap，Key 为列名，Value 为值）
     */
    public List<LinkedHashMap<String, Object>> buildAllDataToMap() {
        List<LinkedHashMap<String, Object>> allData = getAll().stream().map(reportMetricConfig -> {
            LinkedHashMap<String, Object> configMap = new LinkedHashMap<>();

            /* 填充基础信息 */
            configMap.put("编码", reportMetricConfig.getCode());
            configMap.put("名称", reportMetricConfig.getName());
            configMap.put("单位", reportMetricConfig.getUnit());
            configMap.put("车间", reportMetricConfig.getWorkShop());
            configMap.put("值类型", reportMetricConfig.getValueType());
            configMap.put("值来源", reportMetricConfig.getValueSource());

            configMap.put("时间粒度", reportMetricConfig.getGranularity() != null ? reportMetricConfig.getGranularity().getValue() : "-");
            configMap.put("指标图表类型", reportMetricConfig.getChartType());
            configMap.put("时间格式", reportMetricConfig.getTimeFormat());
            configMap.put("缓存时间", reportMetricConfig.getCacheTime());
            configMap.put("来源报表", reportMetricConfig.getSourceReport());
            configMap.put("来源报表类", reportMetricConfig.getSourceClass() != null ? reportMetricConfig.getSourceClass().getName() : "-");

            /* 判断查询方式：SQL 驱动模式 vs 自定义方法模式 */
            boolean driverMode = StrUtil.isNotEmpty(reportMetricConfig.getDbTableName());
            configMap.put("查询方式", driverMode ? "SQL自动构建" : "自定义方法");
            configMap.put("处理方法", driverMode ? "-" : (reportMetricConfig.getQueryMethod() != null ? reportMetricConfig.getQueryMethod().getName() : "-"));

            /* 填充 SQL 驱动模式相关配置 */
            configMap.put("数据库表名", driverMode ? reportMetricConfig.getDbTableName() : "-");
            configMap.put("值列名", driverMode ? reportMetricConfig.getDbTableValueColumn() : "-");
            configMap.put("时间列名", driverMode ? reportMetricConfig.getDbTableTimeColumn() : "-");
            configMap.put("过滤字段", driverMode ? (StrUtil.isNotEmpty(reportMetricConfig.getDbFilterColumn()) ? reportMetricConfig.getDbFilterColumn() : "无") : "-");
            configMap.put("过滤值", driverMode ? (StrUtil.isNotEmpty(reportMetricConfig.getDbFilterColumnValue()) ? reportMetricConfig.getDbFilterColumnValue() : "无") : "-");

            return configMap;
        }).collect(Collectors.toList());
        return allData;
    }

    /**
     * 通过注解构建指标配置对象
     *
     * <p>解析 {@link ReportMetric} 和 {@link ReportMetricOperation} 注解信息，
     * 构造完整的指标配置对象。支持两种查询模式：</p>
     * <ul>
     *   <li><b>自定义方法模式</b>：通过反射方法执行指标数据查询</li>
     *   <li><b>SQL 驱动模式</b>：配置数据库表名后，自动构建 SQL 查询语句</li>
     * </ul>
     *
     * <p><b>校验规则</b>：当配置 SQL 驱动模式时，必须提供时间列和值列名称；
     * 若设置过滤字段，则必须同时提供过滤值。</p>
     *
     * @param reportMetricAnnotation 报表指标类级别注解，提供来源报表等全局配置
     * @param annotation             方法级别操作注解，包含指标的核心属性定义
     * @param method                 指标查询方法的反射对象（用于自定义方法模式）
     * @param methodHandle           指标查询方法的方法句柄（用于高性能调用）
     * @param provider               指标提供者 Bean 实例，作为配置的数据源
     * @return 构建完成的指标配置对象
     * @throws ReportMetricException 当 SQL 驱动模式的必填属性缺失时抛出异常
     */
    public ReportMetricConfig buildConfigByAnnon(ReportMetric reportMetricAnnotation,
                                                 ReportMetricOperation annotation,
                                                 Method method,
                                                 MethodHandle methodHandle,
                                                 Object provider) {
        ReportMetricConfig reportMetricConfig = new ReportMetricConfig()
                .setCode(annotation.code())
                .setName(annotation.name())
                .setUnit(annotation.unit())
                .setGranularity(annotation.granularity())
                .setChartType(annotation.chartType())
                .setTimeFormat(annotation.timeFormat())
                .setCacheTime(annotation.cacheTime())
                .setQueryMethod(method)
                .setQueryMethodHandle(methodHandle)
                .setSourceReport(reportMetricAnnotation.reportName())
                .setSourceClass(provider.getClass())
                .setSourceInstance(provider)
                .setWorkShop(ObjectUtil.defaultIfEmpty(annotation.workShop(), reportMetricAnnotation.workShop()))
                .setValueSource(annotation.valueSource())
                .setValueType(annotation.valueType());

        /* 填充并校验数据库驱动模式相关属性 */
        if (!StrUtil.isEmpty(annotation.dbTableName().trim())) {
            String reportDataSource = reportMetricAnnotation.dataSource();
            String reportOperationDataSource = annotation.dataSource();
            String dataSource = ObjectUtil.defaultIfBlank(reportOperationDataSource, reportDataSource);
            String dbTableName = annotation.dbTableName();
            String dbDbFilterColumn = annotation.dbFilterColumn();
            String dbDbFilterColumnValue = annotation.dbFilterColumnValue();
            String dbTableTimeColumn = annotation.dbTableTimeColumn();
            String dbTableTimeAsColumn = annotation.dbTableTimeAsColumn();
            String dbTableValueColumn = annotation.dbTableValueColumn();
            ReportMetricException.throwByFlag(StrUtil.isNotBlank(dataSource) && !dataSourceNames.contains(dataSource.trim()),
                    "指标编码 {} @ReportMetricOperation填写dataSource属性后,dataSource[{}] 不是已有数据源 {} ,请检查!!!!",
                    reportMetricConfig.getCode(), dataSource, dataSourceNames
            );
            ReportMetricException.throwByFlag(StrUtil.isEmpty(dbTableTimeColumn),
                    "指标编码 {} @ReportMetricOperation填写dbTableName属性后,dbTableTimeColumn属性不可为空",
                    reportMetricConfig.getCode());
            ReportMetricException.throwByFlag(StrUtil.isEmpty(dbTableValueColumn),
                    "指标编码 {} @ReportMetricOperation填写dbTableName属性后,dbTableValueColumn属性不可为空",
                    reportMetricConfig.getCode());
            ReportMetricException.throwByFlag(
                    StrUtil.isNotEmpty(dbDbFilterColumn.trim()) && StrUtil.isEmpty(dbDbFilterColumnValue.trim()),
                    "指标编码 {} @ReportMetricOperation填写dbDbFilterColumn属性后,dbDbFilterColumnValue属性不可为空",
                    reportMetricConfig.getCode());

            reportMetricConfig.setDbTableName(dbTableName)
                    .setDataSource(StrUtil.isNotBlank(dataSource) ? dataSource : primaryDataSource)
                    .setDbTableTimeColumn(dbTableTimeColumn)
                    .setDbTableTimeAsColumn(dbTableTimeAsColumn)
                    .setDbTableValueColumn(dbTableValueColumn)
                    .setDbFilterColumn(dbDbFilterColumn)
                    .setDbFilterColumnValue(dbDbFilterColumnValue)
                    .trimDbStr()
                    .checkColumnLength();
        }
        return reportMetricConfig;
    }


    /**
     * 构建成功的查询结果
     *
     * <p>将指标配置和查询数据封装为标准的 {@link ReportMetricResult} 对象。</p>
     *
     * @param config 指标配置（提供编码、名称、单位等元信息）
     * @param data   查询数据（指标时序数据列表）
     * @return 封装好的查询结果对象（状态标记为 Success）
     */
    public ReportMetricResult buildValueStruct(ReportMetricConfig config, List<ReportMetricData> data) {
        ReportMetricResult result = new ReportMetricResult();

        /* 填充指标元信息 */
        result.setCode(config.getCode())
                .setName(config.getName())
                .setSourceReport(config.getSourceReport())
                .setUnit(config.getUnit())
                .setGranularity(config.getGranularity())
                .setChartType(config.getChartType())
                .setCacheTime(config.getCacheTime())
                .setData(data)
                /* 设置成功状态 */
                .setStatus("Success")
                .setMessage("查询成功");

        return result;
    }

    /**
     * 构建未定义指标的结果
     *
     * <p>当请求的指标编码在配置中心不存在时，返回此结构，避免前端解析异常。</p>
     *
     * @param code 未定义的指标编码
     * @return 封装好的未定义结果对象（状态标记为 Undefined，数据为空）
     */
    public ReportMetricResult buildUndefinedStruct(String code) {
        /* 构造临时的配置对象用于封装 */
        ReportMetricConfig config = new ReportMetricConfig()
                .setCode(code)
                .setName("未定义")
                .setUnit("-");

        ReportMetricResult result = buildValueStruct(config, new ArrayList<>());

        /* 修改为未定义状态 */
        result.setStatus("Undefined");
        result.setMessage("指标编码未定义");
        return result;
    }

    /**
     * 构建异常状态的结果对象
     *
     * <p>捕获异常后，提取异常信息并封装为错误结果，确保异常不会中断整体查询流程。</p>
     *
     * @param config 指标配置
     * @param e      捕获的异常对象
     * @return 封装好的错误结果对象（状态标记为 Error，消息为异常信息）
     */
    public ReportMetricResult buildErrorResult(ReportMetricConfig config, Exception e) {
        ReportMetricResult result = buildValueStruct(config, new ArrayList<>());

        /* 修改为错误状态 */
        result.setStatus("Error");
        result.setMessage(e.getMessage());
        return result;
    }

    /**
     * 构建错误消息的结果对象
     *
     * <p>适用于非异常场景的错误提示（如配置存在但查询无数据）。</p>
     *
     * @param config  指标配置
     * @param message 错误描述信息
     * @return 封装好的错误结果对象（状态标记为 Error）
     */
    public ReportMetricResult buildErrorResult(ReportMetricConfig config, String message) {
        ReportMetricResult result = buildValueStruct(config, new ArrayList<>());

        /* 修改为错误状态 */
        result.setStatus("Error");
        result.setMessage(message);
        return result;
    }


}
