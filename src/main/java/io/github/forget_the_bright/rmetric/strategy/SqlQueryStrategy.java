package io.github.forget_the_bright.rmetric.strategy;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import io.github.forget_the_bright.rmetric.common.Maps;
import io.github.forget_the_bright.rmetric.model.Tuple;
import io.github.forget_the_bright.rmetric.exception.ReportMetricException;
import io.github.forget_the_bright.rmetric.ReportMetricMonitor;
import io.github.forget_the_bright.rmetric.cache.MetricCacheManager;
import io.github.forget_the_bright.rmetric.cache.MetricLockManager;
import io.github.forget_the_bright.rmetric.common.MetricDataManager;
import io.github.forget_the_bright.rmetric.common.MetricThreadManager;
import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricData;
import io.github.forget_the_bright.rmetric.model.ReportMetricResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SQL 查询策略实现
 *
 * <p>根据注解配置自动构建 SQL 查询语句，支持以下特性：</p>
 * <ul>
 *   <li><b>同表指标合并</b>：来自同一张表的多个指标合并为一条 SQL，减少数据库往返</li>
 *   <li><b>并行查询</b>：不同表的查询在自定义 ForkJoinPool 中并行执行</li>
 *   <li><b>表级锁控制</b>：基于表名的细粒度锁，避免同表并发重复查询</li>
 *   <li><b>双重缓存检查</b>：批量缓存预检查 + 锁内二次检查，有效防止缓存击穿</li>
 * </ul>
 *
 * <p>适用场景：简单查询场景，通过注解配置表名、列名、过滤条件即可，无需编写 Java 方法。</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/28 10:23
 */
@Slf4j
@Component
public class SqlQueryStrategy implements QueryStrategy {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
     * @return true=支持（配置了 dbTableName 时走 SQL 查询）
     */
    @Override
    public boolean supports(ReportMetricConfig config) {
        return StrUtil.isNotEmpty(config.getDbTableName());
    }

    /**
     * 执行 SQL 查询
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>按表名分组指标配置</li>
     *   <li>在自定义 ForkJoinPool 中并行处理不同表</li>
     *   <li>每张表执行一次合并 SQL 查询</li>
     *   <li>为每个指标提取对应数据并写入缓存</li>
     * </ol>
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
         * 1. 按表名分组（configs.stream().collect(groupingBy(...))）
         * 2. 并行处理不同表（.entrySet().parallelStream()）
         * 3. 每个表执行一次 SQL 查询（getValueTimeIntervalByBuildSql）
         */
        Map<String, ReportMetricResult> resultMap = metricThreadManager.execute(
                () -> configs.stream()
                        .collect(Collectors.groupingBy(ReportMetricConfig::getDbTableName))
                        .entrySet()
                        .parallelStream()
                        .flatMap(entry -> {
                            String tableName = entry.getKey();
                            List<ReportMetricConfig> dbConfigs = entry.getValue();
                            try {
                                /* 调用带双检锁的 SQL 查询方法 */
                                Map<String, ReportMetricResult> values = getValueTimeIntervalByBuildSql(dbConfigs, start, end == null ? start : end, useCache);
                                return values.entrySet().stream();
                            } catch (Exception e) {
                                /* 记录查询失败 */
                                metricMonitor.recordQueryError();
                                /* 表级查询异常：为该表所有指标返回错误状态 */
                                String errorCodes = dbConfigs.stream().map(ReportMetricConfig::getCode).collect(Collectors.joining(";"));
                                String message = StrUtil.format("表 {} 对应的指标 {} 缓存查询异常: {}", tableName, errorCodes, e.getMessage());
                                log.error(message, e);
                                return dbConfigs.stream().map(config ->
                                        new AbstractMap.SimpleEntry<>(config.getCode(), metricDataManager.buildErrorResult(config, e))
                                );
                            }
                        })
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
        return resultMap;
    }

    /**
     * 按表名执行 SQL 查询（带双检锁）
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li><b>第一次缓存检查（无锁）</b>：批量查询缓存，分离命中和未命中的指标</li>
     *   <li><b>获取表级锁</b>：基于表名的细粒度锁，避免同表并发重复查询</li>
     *   <li><b>第二次缓存检查（有锁）</b>：双重检查，防止等待锁期间其他线程已查询</li>
     *   <li><b>构建并执行 SQL</b>：合并多个指标为一条 SQL，参数化查询防注入</li>
     *   <li><b>处理结果</b>：按指标配置过滤和提取数据</li>
     *   <li><b>批量写入缓存</b>：使用 Pipeline 提升写入性能</li>
     * </ol>
     *
     * @param configs  同一张表的指标配置列表
     * @param start    开始时间
     * @param end      结束时间
     * @param useCache 是否使用缓存
     * @return 该表所有指标的查询结果映射
     */
    private Map<String, ReportMetricResult> getValueTimeIntervalByBuildSql(List<ReportMetricConfig> configs, Date start, Date end, Boolean useCache) {
        Map<String, ReportMetricResult> finalResult = new HashMap<>();
        List<ReportMetricConfig> needDbQueryConfigs = new ArrayList<>();

        /* 第一次缓存检查（无锁，快速路径） */
        if (useCache != null && useCache) {
            Tuple<Map<String, ReportMetricResult>, List<ReportMetricConfig>> cacheCheckResult = cacheManager.batchGetByConfigs(configs, start, end);
            finalResult.putAll(cacheCheckResult.getFirst());
            needDbQueryConfigs = cacheCheckResult.getSecond();
        } else {
            needDbQueryConfigs.addAll(configs);
        }

        /* 全部命中缓存，直接返回 */
        if (needDbQueryConfigs.isEmpty()) {
            return finalResult;
        }

        /* 获取表级细粒度锁（基于 Caffeine 缓存的锁对象） */
        String tableName = needDbQueryConfigs.get(0).getDbTableName();
        Object lock = lockManager.getTableLock(tableName);

        synchronized (lock) {
            List<ReportMetricConfig> stillNeedQueryConfigs = new ArrayList<>();

            /* 第二次缓存检查（有锁，双重检查防击穿） */
            if (useCache != null && useCache) {
                Tuple<Map<String, ReportMetricResult>, List<ReportMetricConfig>> secondCheckResult = cacheManager.batchGetByConfigs(needDbQueryConfigs, start, end);
                finalResult.putAll(secondCheckResult.getFirst());
                stillNeedQueryConfigs = secondCheckResult.getSecond();
            } else {
                stillNeedQueryConfigs.addAll(needDbQueryConfigs);
            }

            /* 锁内检查发现已全部命中缓存 */
            if (stillNeedQueryConfigs.isEmpty()) {
                return finalResult;
            }

            /* 构建并执行合并 SQL */
            List<Map<String, Object>> results = null;
            try {
                ReportMetricConfig defaultConfig = stillNeedQueryConfigs.get(0);
                String dataSource = defaultConfig.getDataSource();
                Tuple<String, String> sqlAndFormat = buildSql(stillNeedQueryConfigs);
                String sql = sqlAndFormat.getFirst();
                String timeFormat = sqlAndFormat.getSecond();
                String startTime = DateUtil.format(start, timeFormat);
                String endTime = DateUtil.format(end, timeFormat);
                //log.debug("执行合并SQL查询, 表: {}, 指标数: {}", tableName, stillNeedQueryConfigs.size());
                /* 记录 SQL 查询次数 */
                metricMonitor.recordSqlQuery();
                // 切换数据源
                DynamicDataSourceContextHolder.push(dataSource);
                results = jdbcTemplate.queryForList(sql, startTime, endTime);
            } catch (Exception e) {
                /* 记录查询失败 */
                metricMonitor.recordQueryError();
                /* SQL 执行失败：为所有受影响指标返回错误结果 */
                String codes = stillNeedQueryConfigs.stream().map(ReportMetricConfig::getCode).collect(Collectors.joining(","));
                String message = StrUtil.format("执行报表指标编码查询sql失败,报表指标为: {}, 错误信息为: {}", codes, e.getMessage());
                log.error(message, e);
                stillNeedQueryConfigs.forEach(config -> {
                    finalResult.put(config.getCode(), metricDataManager.buildErrorResult(config, e));
                });
                return finalResult;
            } finally {
                DynamicDataSourceContextHolder.poll();
            }

            /* 处理查询结果：按指标配置提取数据 */
            if (results != null && !results.isEmpty()) {
                for (ReportMetricConfig dbConfig : stillNeedQueryConfigs) {
                    ReportMetricResult reportMetricResult = processSingleConfigResult(dbConfig, results);
                    finalResult.put(dbConfig.getCode(), reportMetricResult);
                }
            } else {
                /* 查询无数据：返回空结果集 */
                for (ReportMetricConfig dbConfig : stillNeedQueryConfigs) {
                    ReportMetricResult emptyResult = metricDataManager.buildValueStruct(dbConfig, new ArrayList<>());
                    finalResult.put(dbConfig.getCode(), emptyResult);
                }
            }

            /* 批量写入缓存（使用 Pipeline 提升性能） */
            cacheManager.batchSet(finalResult, start, end);
        }

        return finalResult;
    }

    /**
     * 处理单个指标的查询结果
     *
     * <p>从合并查询结果中提取该指标的数据：</p>
     * <ul>
     *   <li>根据 dbFilterColumn 和 dbFilterColumnValue 过滤数据行</li>
     *   <li>提取时间列和值列，格式化为 ReportMetricData</li>
     *   <li>支持 LocalDateTime、Date、String 多种时间类型</li>
     * </ul>
     *
     * @param dbConfig 指标配置
     * @param results  合并查询的结果集
     * @return 该指标的查询结果
     */
    private ReportMetricResult processSingleConfigResult(ReportMetricConfig dbConfig, List<Map<String, Object>> results) {
        String dbTableValueColumn = dbConfig.parseColumnName(ReportMetricConfig::getDbTableValueColumn);
        String dbTableTimeColumn = dbConfig.parseColumnName(ReportMetricConfig::getDbTableTimeColumn);
        String dbTableTimeAsColumn = dbConfig.parseColumnName(ReportMetricConfig::getDbTableTimeAsColumn);
        String dbFilterColumn = dbConfig.parseColumnName(ReportMetricConfig::getDbFilterColumn);
        String dbFilterColumnValue = dbConfig.getDbFilterColumnValue();

        Stream<Map<String, Object>> stream = results.stream();

        /* 按过滤条件筛选数据行 */
        if (StrUtil.isNotEmpty(dbFilterColumn)) {
            stream = stream.filter(map -> dbFilterColumnValue.equals(map.get(dbFilterColumn)));
        }

        /* 转换为 ReportMetricData 列表 */
        List<ReportMetricData> collect = stream.map(map -> {
            Object dbTableTimeColumnValue = map.get(StrUtil.isNotEmpty(dbTableTimeAsColumn) ? dbTableTimeAsColumn : dbTableTimeColumn);
            String dateStr = "null";
            if (dbTableTimeColumnValue != null) {
                /* 兼容多种时间类型 */
                if (dbTableTimeColumnValue instanceof LocalDateTime) {
                    dateStr = DateUtil.format((LocalDateTime) dbTableTimeColumnValue, dbConfig.getTimeFormat());
                } else if (dbTableTimeColumnValue instanceof Date) {
                    dateStr = DateUtil.format((Date) dbTableTimeColumnValue, dbConfig.getTimeFormat());
                } else {
                    dateStr = dbTableTimeColumnValue.toString();
                }
            }

            /* 提取值列 */
            Object valueObj = map.get(dbTableValueColumn);
            String valueStr = valueObj == null ? "null" : valueObj.toString();

            return new ReportMetricData()
                    .setValue(valueStr)
                    .setDate(dateStr);
        }).collect(Collectors.toList());

        return metricDataManager.buildValueStruct(dbConfig, collect);
    }

    /**
     * 构建合并 SQL 语句
     *
     * <p>SQL 构建规则：</p>
     * <ul>
     *   <li><b>SELECT 列</b>：所有指标的值列 + 过滤列 + 时间列（去重）</li>
     *   <li><b>WHERE 条件</b>：各指标的过滤条件用 OR 连接，时间范围用 BETWEEN 参数化</li>
     *   <li><b>时间参数</b>：使用 ? 占位符，防止 SQL 注入</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>
     * SELECT value_col_1, value_col_2, filter_col, time_col
     * FROM table_name
     * WHERE ( (filter_col = 'A') OR (filter_col = 'B') OR (1=1) )
     *   AND time_col BETWEEN ? AND ?
     * </pre>
     *
     * @param dbConfigs 同一张表的指标配置列表
     * @return Tuple(SQL 语句, 时间格式)
     */
    private Tuple<String, String> buildSql(List<ReportMetricConfig> dbConfigs) {
        /* 防御性校验 */
        ReportMetricException.throwByFlag(dbConfigs.isEmpty(), "dbConfigs个数不能为空");

        /* 获取公共配置（同表指标共享） */
        ReportMetricConfig defaultConfig = dbConfigs.get(0);
        String dbTableTimeColumn = defaultConfig.getDbTableTimeColumn();
        String dbTableName = defaultConfig.getDbTableName();
        String timeFormat = defaultConfig.getTimeFormat();

        /* 收集所有需要查询的列（去重） */
        List<String> tableValueColumns = dbConfigs.stream()
                .map(ReportMetricConfig::getDbTableValueColumn)
                .filter(StrUtil::isNotEmpty)
                .distinct()
                .collect(Collectors.toList());

        List<String> dbFilterColumns = dbConfigs.stream()
                .map(ReportMetricConfig::getDbFilterColumn)
                .filter(StrUtil::isNotEmpty)
                .distinct()
                .collect(Collectors.toList());

        List<String> dbTableTimeAsColumns = dbConfigs.stream()
                .map(ReportMetricConfig::getDbTableTimeAsColumn)
                .filter(StrUtil::isNotEmpty)
                .distinct()
                .collect(Collectors.toList());

        /* 判断是否存在未配置过滤条件的指标 */
        boolean containsNotHaveDbFilterColumn = dbConfigs.stream()
                .map(ReportMetricConfig::getDbFilterColumn)
                .filter(StrUtil::isEmpty)
                .count() > 0;

        /* 构建 WHERE 过滤条件 */
        List<String> tableFilter = dbConfigs.stream()
                .filter(config -> StrUtil.isNotEmpty(config.getDbFilterColumn()))
                .map(config ->
                        StrUtil.format(
                                "( {} = '{}' )",
                                config.extractColumnExpression(ReportMetricConfig::getDbFilterColumn),
                                config.getDbFilterColumnValue()
                        )
                )
                .distinct()
                .collect(Collectors.toList());

        /* 若无过滤条件或部分指标无过滤条件，添加 (1=1) 占位 */
        if (tableFilter.isEmpty() || containsNotHaveDbFilterColumn) {
            tableFilter.add("(1=1)");
        }

        /* 构建 SELECT 列 */
        List<String> selectColumns = new ArrayList<>();
        selectColumns.addAll(tableValueColumns);
        selectColumns.addAll(dbFilterColumns);
        selectColumns.add(dbTableTimeColumn);
        selectColumns.addAll(dbTableTimeAsColumns);
        selectColumns = selectColumns.stream().distinct().collect(Collectors.toList());

        /* 拼接完整 SQL（时间范围使用参数化查询） */
        String sql = StrUtil.format("SELECT {} FROM {} WHERE ( {} ) AND {} BETWEEN ? AND ? ORDER BY {} ASC;",
                StrUtil.join(",", selectColumns),
                dbTableName,
                StrUtil.join(" OR ", tableFilter),
                dbTableTimeColumn,
                dbTableTimeAsColumns.isEmpty() ? dbTableTimeColumn : dbTableTimeAsColumns.get(0)
        );

        String codes = dbConfigs.stream().map(ReportMetricConfig::getCode).collect(Collectors.joining(","));
        log.debug("构建查询SQL完成,当前指标编码[{}]：({}),查询SQL: \n{} ", dbConfigs.size(), codes, sql);

        return Tuple.newTuple(sql, timeFormat);
    }


    /**
     * 预览指标查询 SQL 及元数据信息
     *
     * <p>根据指标配置生成可用于预览的 SQL 查询语句及相关元数据信息，将参数占位符替换为实际的时间值。</p>
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>按表名分组指标配置</li>
     *   <li>为每组配置构建合并的 SQL 查询语句</li>
     *   <li>将参数占位符（?）替换为格式化后的时间值</li>
     *   <li>清理 SQL 格式（移除注释、换行符等）</li>
     *   <li>生成包含 SQL、表名、时间格式等信息的预览结果</li>
     * </ol>
     *
     * @param value 指标配置列表
     * @param start 开始时间
     * @param end   结束时间
     * @return SQL 预览映射（Key: [指标编码](数量), Value: 包含 sql、tableName、timeFormat、dbTableTimeColumn、codes 的 Map）
     */
    @Override
    public Map<String, Object> previewProcessQuery(List<ReportMetricConfig> value, Date start, Date end) {
        Map<String, Object> collect = value.stream()
                .collect(Collectors.groupingBy(ReportMetricConfig::getDbTableName))
                .entrySet()
                .stream()
                .map(entry -> {
                    String tableName = entry.getKey();
                    List<ReportMetricConfig> dbConfigs = entry.getValue();
                    ReportMetricConfig defaultConfig = dbConfigs.get(0);
                    Tuple<String, String> sqlAndFormat = buildSql(dbConfigs);
                    String sql = sqlAndFormat.getFirst()
                            .replace("?", "'{}'")
                            .replaceAll("--.*?\\n", " ")
                            .replace("\n", " ")
                            .replace("\r", " ")
                            .replace("\t", " ");
                    String timeFormat = sqlAndFormat.getSecond();
                    String startTime = DateUtil.format(start, timeFormat);
                    String endTime = DateUtil.format(end, timeFormat);
                    String sqlWithParams = StrUtil.format(sql, startTime, endTime);
                    List<String> codeList = dbConfigs.stream().map(ReportMetricConfig::getCode).collect(Collectors.toList());
                    String codes = String.join(",", codeList);
                    String codesFormat = StrUtil.format("[{}]({})", codes, dbConfigs.size());
                    return new AbstractMap.SimpleEntry<>(codesFormat, Maps.asMap(
                            Maps.put("sql", sqlWithParams),
                            Maps.put("tableName", tableName),
                            Maps.put("timeFormat", timeFormat),
                            Maps.put("dbTableTimeColumn", defaultConfig.getDbTableTimeColumn()),
                            Maps.put("codes", codeList)
                    ));
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return collect;
    }
}
