package io.github.forget_the_bright.rmetric;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import io.github.forget_the_bright.rmetric.common.Maps;
import io.github.forget_the_bright.rmetric.model.Tuple;
import io.github.forget_the_bright.rmetric.cache.MetricCacheManager;
import io.github.forget_the_bright.rmetric.common.MetricDataManager;
import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricResult;
import io.github.forget_the_bright.rmetric.strategy.QueryStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表指标统一查询执行器
 *
 * <p>作为指标查询的核心入口，提供以下功能：</p>
 * <ul>
 *   <li><b>缓存优先查询</b>：先批量检查 Redis 缓存，仅对未命中的指标执行查询</li>
 *   <li><b>策略路由</b>：根据指标配置自动选择 SQL 或 Method 查询策略</li>
 *   <li><b>结果聚合</b>：按请求顺序返回结果，保持原始编码顺序</li>
 *   <li><b>容错处理</b>：未定义指标返回空结构，查询失败返回错误信息</li>
 * </ul>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/28 10:23
 */
@Slf4j
@Component
public class ReportMetricExecutor {

    @Autowired
    private MetricDataManager metricDataManager;

    @Autowired
    private List<QueryStrategy> queryStrategies;

    @Autowired
    private MetricCacheManager cacheManager;

    @Autowired
    private ReportMetricMonitor metricMonitor;

    /**
     * 带缓存的指标查询（推荐使用）
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>解析编码列表，去重并过滤无效编码</li>
     *   <li>批量查询 Redis 缓存，分离命中和未命中的指标</li>
     *   <li>对未命中缓存的指标调用 {@link #getValueByTimeInterval(String, Date, Date, Boolean)} 查询</li>
     *   <li>按原始请求顺序组装结果（缓存优先，其次查询结果，最后未定义/错误）</li>
     * </ol>
     *
     * @param codes 指标编码列表，多个用分号分隔（如 "D001;D002;D003"）
     * @param date1 开始时间（必填）
     * @param date2 结束时间（选填，为空时视为精确查询）
     * @return 指标查询结果映射（Key: 指标编码, Value: 查询结果，按请求顺序排列）
     */
    public Map<String, ReportMetricResult> getValueByTimeIntervalCache(String codes, Date date1, Date date2) {
        long startTime = System.currentTimeMillis();

        try {
            /* 解析并去重编码列表 */
            String[] splitCodes = codes.split(";");
            List<String> codeLists = Arrays.stream(splitCodes).distinct().collect(Collectors.toList());

            /* 使用 LinkedHashMap 保持插入顺序 */
            LinkedHashMap<String, ReportMetricResult> result = new LinkedHashMap<>();
            Map<String, ReportMetricResult> cacheHitMap = new HashMap<>();

            /* 过滤出有效的指标编码（存在于配置中心） */
            List<String> validCodes = codeLists.stream()
                    .map(metricDataManager::get)
                    .filter(ObjectUtil::isNotNull)
                    .map(ReportMetricConfig::getCode)
                    .collect(Collectors.toList());

            /* 批量查询 Redis 缓存 */
            Tuple<Map<String, ReportMetricResult>, List<String>> mapListTuple = cacheManager.batchGetByCodes(validCodes, date1, date2);
            cacheHitMap.putAll(mapListTuple.getFirst());
            List<String> needSearchCodes = mapListTuple.getSecond();

            /* 仅对未命中缓存的指标执行查询 */
            Map<String, ReportMetricResult> searchResultMap = new HashMap<>();
            if (!needSearchCodes.isEmpty()) {
                String joinedCodes = StrUtil.join(";", needSearchCodes);
                searchResultMap = getValueByTimeInterval(joinedCodes, date1, date2 == null ? date1 : date2, true);
            }

            /* 按原始顺序组装结果 */
            for (String code : codeLists) {
                /* 优先使用缓存结果 */
                if (cacheHitMap.containsKey(code)) {
                    result.put(code, cacheHitMap.get(code));
                    continue;
                }

                /* 其次使用查询结果 */
                if (searchResultMap.containsKey(code)) {
                    result.put(code, searchResultMap.get(code));
                    continue;
                }

                /* 配置不存在：返回未定义结构 */
                ReportMetricConfig config = metricDataManager.get(code);
                if (config == null) {
                    result.put(code, metricDataManager.buildUndefinedStruct(code));
                    continue;
                }

                /* 配置存在但查询无结果：返回错误信息 */
                String message = StrUtil.format("指标 {} 配置存在但未返回任何结果", code);
                log.warn(message);
                result.put(code, metricDataManager.buildErrorResult(config, message));
            }

            return result;
        } finally {
            /* 记录查询耗时 */
            long duration = System.currentTimeMillis() - startTime;
            metricMonitor.recordQueryDuration(duration);
        }
    }

    /**
     * 不带缓存的指标查询（内部调用或强制刷新时使用）
     *
     * @param codes 指标编码列表，多个用分号分隔
     * @param date1 开始时间
     * @param date2 结束时间
     * @return 指标查询结果映射
     */
    public Map<String, ReportMetricResult> getValueByTimeInterval(String codes, Date date1, Date date2) {
        return getValueByTimeInterval(codes, date1, date2, false);
    }

    /**
     * 指标查询核心方法
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>解析编码列表，去重并加载配置</li>
     *   <li>按策略分组（SQL 策略 / Method 策略）</li>
     *   <li>串行遍历策略，每个策略内部并行执行查询</li>
     *   <li>聚合所有策略的查询结果</li>
     *   <li>按原始请求顺序组装结果，处理未定义/失败情况</li>
     * </ol>
     *
     * @param codes    指标编码列表，多个用分号分隔
     * @param date1    开始时间
     * @param date2    结束时间
     * @param useCache 是否使用缓存（由 Strategy 内部处理）
     * @return 指标查询结果映射（Key: 指标编码, Value: 查询结果，按请求顺序排列）
     */
    public Map<String, ReportMetricResult> getValueByTimeInterval(String codes, Date date1, Date date2, Boolean useCache) {
        /* 解析并去重编码列表 */
        String[] splitCodes = codes.split(";");
        List<String> codeLists = Arrays.stream(splitCodes).distinct().collect(Collectors.toList());

        /* 使用 LinkedHashMap 保持插入顺序 */
        LinkedHashMap<String, ReportMetricResult> result = new LinkedHashMap<>();

        /* 加载指标配置，过滤无效编码 */
        List<ReportMetricConfig> configs = codeLists.stream()
                .map(metricDataManager::get)
                .filter(ObjectUtil::isNotNull)
                .collect(Collectors.toList());

        /* 按策略分组：每个配置只匹配一个策略 */
        Map<QueryStrategy, List<ReportMetricConfig>> grouped = configs.stream()
                .collect(Collectors.groupingBy(config ->
                        queryStrategies.stream()
                                .filter(strategy -> strategy.supports(config))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("No strategy supports config: " + config.getCode()))
                ));

        /*
         * 串行遍历策略，每个策略内部并行执行查询：
         * - SQL 策略：按表名分组后并行查询不同表
         * - Method 策略：并行查询不同指标
         */
        Map<String, ReportMetricResult> allResults = grouped.entrySet().stream()
                .flatMap(entry ->
                        entry
                                .getKey()
                                .query(entry.getValue(), date1, date2 == null ? date1 : date2, useCache)
                                .entrySet()
                                .stream()
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        /* 按原始顺序组装结果 */
        for (String code : codeLists) {
            /* 配置不存在：返回未定义结构 */
            ReportMetricConfig config = metricDataManager.get(code);
            if (config == null) {
                result.put(code, metricDataManager.buildUndefinedStruct(code));
                continue;
            }

            /* 使用查询结果 */
            ReportMetricResult queryResult = allResults.get(code);
            if (queryResult != null) {
                result.put(code, queryResult);
                continue;
            }

            /* 查询未返回结果：返回错误信息 */
            String message = StrUtil.format("指标 {} 未从任何SQL和METHOD来源找到数据", code);
            log.warn(message);
            result.put(code, metricDataManager.buildErrorResult(config, message));
        }

        return result;
    }


    public Map<String, Object> previewReportMetricQuery(String codes, Date date1, Date date2) {
        /* 解析并去重编码列表 */
        String[] splitCodes = codes.split(";");
        List<String> codeLists = Arrays.stream(splitCodes).distinct().collect(Collectors.toList());
        /* 使用 LinkedHashMap 保持插入顺序 */
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        /* 加载指标配置，过滤无效编码 */
        List<ReportMetricConfig> configs = codeLists.stream()
                .map(metricDataManager::get)
                .filter(ObjectUtil::isNotNull)
                .collect(Collectors.toList());

        /* 按策略分组：每个配置只匹配一个策略 */
        Map<QueryStrategy, List<ReportMetricConfig>> grouped = configs.stream()
                .collect(Collectors.groupingBy(config ->
                        queryStrategies.stream()
                                .filter(strategy -> strategy.supports(config))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("No strategy supports config: " + config.getCode()))
                ));

        /*
         * 串行遍历策略，每个策略内部并行执行查询：
         * - SQL 策略：按表名分组后并行查询不同表
         * - Method 策略：并行查询不同指标
         */
        Map<String, Object> allResults = grouped.entrySet().stream()
                .flatMap(entry ->
                        entry
                                .getKey()
                                .previewProcessQuery(entry.getValue(), date1, date2 == null ? date1 : date2)
                                .entrySet()
                                .stream()
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));



        /* 按原始顺序组装结果 */
        for (String code : codeLists) {
            /* 配置不存在：返回未定义结构 */
            ReportMetricConfig config = metricDataManager.get(code);
            if (config == null) {
                result.put(code, "指标不存在");
                continue;
            }

            /* 使用查询结果 */
            Object processResult = allResults.get(code);
            if (ObjectUtil.isNotEmpty(processResult)) {
                result.put(code, processResult);
                allResults.remove(code);
            }
        }
        for (Map.Entry<String, Object> entry : allResults.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 根据指标编码查询同表的其他指标（分类归总）
     *
     * <p>用于发现与指定指标在同一张表的其他指标，支持批量查询并自动去重。</p>
     *
     * @param codes 指标编码列表，多个编码用分号分隔（如 "LS-0020;LS-0021"）
     * @return 按输入编码分组的同表指标映射（Key: 输入的指标编码, Value: 该指标所在表的所有指标列表）
     */
    public Map<String, Object> querySameTableMetricsByName(String codes) {
        /* 解析并去重编码列表 */
        String[] splitCodes = codes.split(";");
        List<String> codeLists = Arrays.stream(splitCodes)
                .distinct()
                .filter(StrUtil::isNotEmpty)
                .collect(Collectors.toList());

        if (codeLists.isEmpty()) {
            return new LinkedHashMap<>();
        }

        /* 使用 LinkedHashMap 保持插入顺序 */
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();

        /* 缓存已查询过的表，避免重复扫描 */
        Map<String, List<ReportMetricConfig>> tableCache = new HashMap<>();
        // 缓存已查询过的表对应的源编码
        Map<String, String> tableCacheSourceCode = new HashMap<>();

        for (String code : codeLists) {
            /* 配置不存在：返回提示信息 */
            ReportMetricConfig config = metricDataManager.get(code);
            if (config == null) {
                result.put(code, "指标不存在");
                continue;
            }

            if (StrUtil.isEmpty(config.getDbTableName())) {
                result.put(code, "指标不是数据库指标");
                continue;
            }

            String tableName = config.getDbTableName();

            /* 检查缓存，避免重复扫描全量数据 */
            List<ReportMetricConfig> sameTableMetrics = tableCache.get(tableName);
            if (sameTableMetrics == null) {
                /* 首次查询该表：扫描所有指标并过滤出同表指标 */
                sameTableMetrics = metricDataManager.getAll().stream()
                        .filter(rmConfig -> tableName.equals(rmConfig.getDbTableName()))
                        .collect(Collectors.toList());

                /* 存入缓存 */
                tableCache.put(tableName, sameTableMetrics);
                tableCacheSourceCode.put(tableName, code);
                /* 返回该表的所有指标信息（包含编码、名称等） */
                result.put(code, Maps.asMap(
                                LinkedHashMap.class,
                                Maps.put("sameTableMetricsCodes", sameTableMetrics.stream().map(ReportMetricConfig::getCode).collect(Collectors.joining(";"))),
                                Maps.put("sameTableMetrics", sameTableMetrics)
                        )
                );
            } else {
                String sourceCode = tableCacheSourceCode.get(tableName);
                result.put(code, StrUtil.format("请看[{}]下内容,当前code表名称和其是一致的", sourceCode));
            }
        }

        return result;
    }

    public List<Map<String, Object>> fuzzyQuerySameTableMetricsByTableName(String tableName) {
        List<Map<String, Object>> collect = metricDataManager.getAll().stream()
                .filter(config -> StrUtil.isNotEmpty(config.getDbTableName()))
                .filter(config -> config.getDbTableName().contains(tableName))
                .collect(Collectors.groupingBy(ReportMetricConfig::getDbTableName))
                .entrySet()
                .stream()
                .map(entry -> {
                    String searchTableName = entry.getKey();
                    List<ReportMetricConfig> sameTableMetrics = entry.getValue();
                    return Maps.asMap(Maps.put("tableName", searchTableName),
                            Maps.put("sameTableMetricsCodes", sameTableMetrics.stream().map(ReportMetricConfig::getCode).collect(Collectors.joining(";"))),
                            Maps.put("sameTableMetrics", sameTableMetrics)
                    );
                }).collect(Collectors.toList());
        return collect ;
    }
}
