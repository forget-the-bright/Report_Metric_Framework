
package io.github.forget_the_bright.rmetric;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.forget_the_bright.rmetric.cache.MetricCacheManager;
import io.github.forget_the_bright.rmetric.model.Tuple;
import lombok.extern.slf4j.Slf4j;
import io.github.forget_the_bright.rmetric.exception.ReportMetricException;
import io.github.forget_the_bright.rmetric.common.MetricDataManager;
import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricData;
import io.github.forget_the_bright.rmetric.model.ReportMetricResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 报表指标调用处理器
 * 已弃用
 *
 * <p>提供统一的指标查询入口,封装反射调用逻辑</p>
 *
 * @author wanghao(helloworlwh @ 163.com)
 * @since 2026/4/24 13:32
 */
@Slf4j
@Component
@Deprecated
public class ReportMetricInvokeHandler {

    @Autowired
    private MetricDataManager metricDataManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MetricCacheManager redisUtil;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String redisKeyPrefix = "Base:MesReportMetricController:queryReportMetricList:";
    private static final Long redisCacheTimeSeconds = 60L;

    //region 异步线程池
    // IO 密集型（推荐）： 如果你的指标查询主要是在查数据库（SQL）或查 Redis。因为线程在等待数据库返回时是空闲的，所以需要更多的线程来充分利用 CPU。
    private static final int poolSize = Runtime.getRuntime().availableProcessors() * 2;
    /**
     * 自定义线程工厂，用于设置 ForkJoinPool 工作线程的名称
     */
    private static final ForkJoinPool.ForkJoinWorkerThreadFactory metricQueryThreadFactory = pool -> {
        // 创建一个默认的 worker 线程
        ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        t.setName("MetricQuery-Worker-" + t.getId());
        return t;
    };

    private static final ForkJoinPool metricQueryPool = new ForkJoinPool(poolSize, metricQueryThreadFactory, null, false);
    //endregion

    //region 报表指标查询

    /**
     * 获取所有指标配置
     *
     * @return 指标配置集合
     */
    public Collection<ReportMetricConfig> getAll() {
        return metricDataManager.getSearchMap().values();
    }

    /**
     * 根据编码获取指标配置
     *
     * @param code 指标编码
     * @return 指标配置, 不存在返回null
     */
    public ReportMetricConfig get(String code) {
        return metricDataManager.getSearchMap().get(code);
    }


    public List<LinkedHashMap<String, Object>> buildAllDataToMap() {
        List<LinkedHashMap<String, Object>> allData = getAll().stream().map(reportMetricConfig -> {
            LinkedHashMap<String, Object> configMap = new LinkedHashMap<>();
            configMap.put("编码", reportMetricConfig.getCode());
            configMap.put("名称", reportMetricConfig.getName());
            configMap.put("单位", reportMetricConfig.getUnit());
            configMap.put("时间粒度", reportMetricConfig.getGranularity() != null ? reportMetricConfig.getGranularity().getValue() : "-");
            configMap.put("时间格式", reportMetricConfig.getTimeFormat());
            configMap.put("来源报表", reportMetricConfig.getSourceReport());
            configMap.put("来源报表类", reportMetricConfig.getSourceClass() != null ? reportMetricConfig.getSourceClass().getName() : "-");
            // SQL 驱动模式相关配置
            boolean dirverMode = StrUtil.isNotEmpty(reportMetricConfig.getDbTableName());
            configMap.put("查询方式", dirverMode ? "SQL自动构建" : "自定义方法");
            configMap.put("处理方法", dirverMode ? "-" : (reportMetricConfig.getQueryMethod() != null ? reportMetricConfig.getQueryMethod().getName() : "-"));
            configMap.put("数据库表名", dirverMode ? reportMetricConfig.getDbTableName() : "-");
            configMap.put("值列名", dirverMode ? reportMetricConfig.getDbTableValueColumn() : "-");
            configMap.put("时间列名", dirverMode ? reportMetricConfig.getDbTableTimeColumn() : "-");
            configMap.put("过滤字段", dirverMode ? (StrUtil.isNotEmpty(reportMetricConfig.getDbFilterColumn()) ? reportMetricConfig.getDbFilterColumn() : "无") : "-");
            configMap.put("过滤值", dirverMode ? (StrUtil.isNotEmpty(reportMetricConfig.getDbFilterColumnValue()) ? reportMetricConfig.getDbFilterColumnValue() : "无") : "-");
            return configMap;
        }).collect(Collectors.toList());
        return allData;
    }

    public Map<String, List<Map<String, Object>>> buildMutilReportDataToMap() {
        Map<String, List<Map<String, Object>>> groupBySourceReport = buildAllDataToMap()
                .stream()
                .collect(Collectors.groupingBy(map -> map.get("来源报表").toString()));
        return groupBySourceReport;
    }
    //endregion

    // region 缓存锁
    /**
     * 表级别锁缓存：访问后 10 分钟未使用则自动清除
     */
    private final Cache<String, Object> tableLockCache = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .initialCapacity(100)
            .maximumSize(1000)
            .build();

    /**
     * 方法/指标级别锁缓存：访问后 5 分钟未使用则自动清除
     */
    private final Cache<String, Object> methodLockCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .initialCapacity(100)
            .maximumSize(5000)
            .build();


    /**
     * 获取表级别锁
     */
    private Object getTableLock(String tableName) {
        return tableLockCache.get(tableName, key -> new Object());
    }

    /**
     * 获取方法级别锁
     */
    private Object getMethodLock(String code) {
        return methodLockCache.get(code, key -> new Object());
    }

    // endregion

    //region 查询入口方法

    /**
     * 按时间查询多个指标值（带缓存优先策略）
     * <p>
     * 执行流程：
     * 1. 解析并去重指标编码
     * 2. 第一轮遍历：尝试从 Redis 获取缓存。
     * - 命中缓存：存入 cacheResult
     * - 未命中/配置无效：记录到 needSearchCodes 或 invalidCodes
     * 3. 批量查询：对未命中的有效指标，调用底层查询逻辑（内部含双检锁）
     * 4. 第二轮遍历：合并结果。
     * - 优先取缓存
     * - 其次取查询结果
     * - 若配置不存在，返回默认“未定义”结构
     *
     * @param codes 报表指标编码，多个用分号分隔
     * @param date1 开始时间
     * @param date2 结束时间
     * @return 指标结果映射表（保持输入顺序）
     */
    public Map<String, ReportMetricResult> getValueByTimeIntervalCache(String codes, Date date1, Date date2) {
        // 1. 解析编码并去重，保持插入顺序
        String[] splitCodes = codes.split(";");
        List<String> codeLists = Arrays.stream(splitCodes).distinct().collect(Collectors.toList());

        // 最终结果集
        LinkedHashMap<String, ReportMetricResult> result = new LinkedHashMap<>();

        // 临时存储：缓存命中的结果
        Map<String, ReportMetricResult> cacheHitMap = new HashMap<>();
        // 2.临时存储：需要进一步查询数据库/方法的指标编码
        List<String> configs = codeLists.stream()
                .map(this::get)
                .filter(ObjectUtil::isNotNull)
                .map(ReportMetricConfig::getCode)
                .collect(Collectors.toList());

        // 情况 B: 尝试获取缓存
        Tuple<Map<String, ReportMetricResult>, List<String>> mapListTuple = batchGetCacheResultsByCodes(configs, date1, date2);
        Map<String, ReportMetricResult> cacheResults = mapListTuple.getFirst();
        cacheHitMap.putAll(cacheResults);
        List<String> needSearchCodes = mapListTuple.getSecond();

        // 3. 批量查询未命中的指标
        Map<String, ReportMetricResult> searchResultMap = new HashMap<>();
        if (!needSearchCodes.isEmpty()) {
            // 调用底层查询方法，该方法内部会处理 SQL 聚合和方法调用，并带有双检锁
            String joinedCodes = StrUtil.join(";", needSearchCodes);
            searchResultMap = getValueByTimeInterval(joinedCodes, date1, date2, true);
        }

        // 4. 第二遍扫描：按原始顺序组装最终结果
        for (String code : codeLists) {
            // 优先级 1: 缓存命中
            if (cacheHitMap.containsKey(code)) {
                result.put(code, cacheHitMap.get(code));
                continue;
            }

            // 优先级 2: 查询结果命中
            if (searchResultMap.containsKey(code)) {
                result.put(code, searchResultMap.get(code));
                continue;
            }

            // 优先级 3: 配置不存在或查询失败兜底
            // 再次检查配置是否存在，以区分“配置缺失”和“查询为空”
            ReportMetricConfig config = get(code);
            if (config == null) {
                result.put(code, buildUndefinedStruct(code));
            } else {
                // 如果配置存在但没查到数据（理论上 searchResultMap 应该包含它，即使是空列表）
                // 这里做一个防御性编程，如果真的缺失，返回空结构
                String message = StrUtil.format("指标 {} 配置存在但未返回任何结果", code);
                log.warn(message);
                ReportMetricResult reportMetricResult = buildValueStruct(config, new ArrayList<>());
                reportMetricResult.setStatus("Error");
                reportMetricResult.setMessage(message);
                result.put(code, reportMetricResult);
            }
        }

        return result;
    }

    /**
     * 按时间查询多个指标值（核心入口）
     * <p>
     * 逻辑流程：
     * 1. 解析并去重指标编码
     * 2. 获取指标配置，过滤掉无效配置
     * 3. 【分流】将指标分为两类：
     * - DB型：有表名配置，通过 SQL 批量查询
     * - Method型：无表名配置，通过反射/MethodHandle 单独调用
     * 4. 【执行】分别执行查询逻辑（内部包含双检锁和缓存处理）
     * 5. 【合并】将两部分结果合并，并补充未定义指标的默认值
     *
     * @param codes    报表指标编码，多个用分号分隔
     * @param date1    开始时间
     * @param date2    结束时间
     * @param useCache 是否启用缓存
     * @return 指标结果映射表
     */
    public Map<String, ReportMetricResult> getValueByTimeInterval(String codes, Date date1, Date date2, Boolean useCache) {
        // 1. 解析编码并去重
        String[] splitCodes = codes.split(";");
        List<String> codeLists = Arrays.stream(splitCodes).distinct().collect(Collectors.toList());

        // 最终结果集，使用 LinkedHashMap 保持请求顺序
        LinkedHashMap<String, ReportMetricResult> result = new LinkedHashMap<>();

        // 2. 获取有效的指标配置列表
        List<ReportMetricConfig> configs = codeLists.stream()
                .map(this::get)
                .filter(ObjectUtil::isNotNull)
                .collect(Collectors.toList());

        // 3. 处理 DB 型指标（SQL 查询）
        // 按表名分组，同一张表的指标合并为一条 SQL 查询，提高性能
        Map<String, ReportMetricResult> sqlResults = metricQueryPool.submit(() -> configs.stream()
                .filter(config -> StrUtil.isNotEmpty(config.getDbTableName()))
                .collect(Collectors.groupingBy(ReportMetricConfig::getDbTableName))
                .entrySet()
                .parallelStream()
                .flatMap(entry -> {
                    String tableName = entry.getKey();
                    List<ReportMetricConfig> dbConfigs = entry.getValue();
                    try {
                        // 调用带双检锁的 SQL 查询方法
                        Map<String, ReportMetricResult> values = getValueTimeIntervalByBuildSql(dbConfigs, date1, date2, useCache);
                        return values.entrySet().stream();
                    } catch (Exception e) {
                        String errorCodes = dbConfigs.stream().map(ReportMetricConfig::getCode).collect(Collectors.joining(";"));
                        log.error("表 {} 对应的指标 {} 查询异常: {}", tableName, errorCodes, e.getMessage());
                        // 异常时为组内所有指标返回错误状态
                        return dbConfigs.stream().map(config ->
                                new AbstractMap.SimpleEntry<>(config.getCode(), buildErrorResult(config, e))
                        );
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).join();

        // 4. 处理 Method 型指标（反射调用）
        // 注意：此处为串行执行。如果方法调用耗时较长且互不依赖，可考虑 .parallel() 并行流
        // 必须把逻辑包裹在 metricQueryPool.submit() 中，利用 Fork/Join 的线程继承特性。
        Map<String, ReportMetricResult> methodResults = metricQueryPool.submit(() -> configs.parallelStream()
                .filter(config -> StrUtil.isEmpty(config.getDbTableName())) // 这里的并行流会“继承”外层 submit 的线程池
                .collect(Collectors.toMap(
                        ReportMetricConfig::getCode,
                        config -> {
                            try {
                                return getValueTimeIntervalByInvokeMethod(config, date1, date2, useCache);
                            } catch (Exception e) {
                                log.error("指标 {} 查询异常: {}", config.getCode(), e.getMessage());
                                ReportMetricResult errorResult = buildErrorResult(config, e);
                                return errorResult;
                            }
                        }
                ))).join(); // 等待任务完成并返回结果

        // 5. 合并结果并处理异常情况
        for (String code : codeLists) {
            // 情况 A: 指标配置不存在
            ReportMetricConfig reportMetricConfig = get(code);
            if (reportMetricConfig == null) {
                result.put(code, buildUndefinedStruct(code));
                continue;
            }

            // 情况 B: 优先取 SQL 查询结果
            ReportMetricResult sqlReportMetricResult = sqlResults.get(code);
            if (sqlReportMetricResult != null) {
                result.put(code, sqlReportMetricResult);
                continue;
            }

            // 情况 C: 其次取方法调用结果
            ReportMetricResult methodReportMetricResult = methodResults.get(code);
            if (methodReportMetricResult != null) {
                result.put(code, methodReportMetricResult);
                continue;
            }
            // 情况 D: 理论上不应发生，除非中间过程抛出异常被吞掉或逻辑遗漏
            // 这里可以做一个兜底，返回空结构，防止前端报错
            String message = StrUtil.format("指标 {} 未找到任何SQL和METHOD来源找到数据", code);
            log.warn(message);
            ReportMetricResult reportMetricResult = buildValueStruct(reportMetricConfig, new ArrayList<>());
            reportMetricResult.setStatus("Error");
            reportMetricResult.setMessage(message);
            result.put(code, reportMetricResult);
        }
        // 6. 批量设置缓存结果
        // （不用入口方法统一写入缓存，并发情况下，双检索因为等待主方法慢会导致失效）
/*        Map<String, ReportMetricResult> batchSetCacheResults = result.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().getName().equals("未定义"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        batchSetCacheResults(batchSetCacheResults, date1, date2, redisCacheTimeSeconds);*/
        return result;
    }

    /*
     * 按时间查询多个指标值是否使用缓存默认为false（核心入口）
     */
    public Map<String, ReportMetricResult> getValueByTimeInterval(String codes, Date date1, Date date2) {
        return getValueByTimeInterval(codes, date1, date2, false);
    }
    //endregion

    //region 扩展方法

    /**
     * 按时间查询单个指标值(无缓存/带双检锁)
     *
     * <p>使用MethodHandle或反射执行指标查询方法,优先使用MethodHandle提升性能</p>
     * <p>集成双重检查锁定(DCL)以防止高并发下的重复计算和缓存击穿</p>
     *
     * @param reportMetricConfig 报表指标编码
     * @param date1              必填,单独传入为精确查询,与date2配合为区间开始时间
     * @param date2              区间结束时间,null则使用date1作为精确时间
     * @param useCache           是否使用缓存逻辑
     * @return 指标结果对象, 包含code/name/unit/status/data
     */
    private ReportMetricResult getValueTimeIntervalByInvokeMethod(ReportMetricConfig reportMetricConfig, Date date1, Date date2, Boolean useCache) {


        // 单参数精确查询时,date2使用date1的值
        if (date2 == null) {
            date2 = date1;
        }
        String code = reportMetricConfig.getCode();
        // 生成唯一的缓存Key
        String redisKey = StrUtil.format("{}{}_{}_{}", redisKeyPrefix, code, date1.getTime(), date2.getTime());

        // --- 第一重检查：如果不使用缓存，直接跳过检查进入锁逻辑；如果使用缓存，先查一次 ---
        if (useCache != null && useCache) {
            ReportMetricResult cacheResult = (ReportMetricResult) redisUtil.get(redisKey);
            if (cacheResult != null) {
                return cacheResult;
            }
        }

        // 获取基于 Code 的细粒度锁对象
        // 注意：这里复用 methodLockMap，但 Key 是 code。
        Object lock = getMethodLock(code);

        // --- 同步块开始 ---
        synchronized (lock) {
            // --- 第二重检查：拿到锁后，再次检查缓存 ---
            if (useCache != null && useCache) {
                ReportMetricResult cacheResult = (ReportMetricResult) redisUtil.get(redisKey);
                if (cacheResult != null) {
                    return cacheResult;
                }
            }

            // --- 执行真正的业务逻辑：反射或 MethodHandle 调用 ---
            Object sourceInstance = reportMetricConfig.getSourceInstance();
            Method queryMethod = reportMetricConfig.getQueryMethod();
            MethodHandle queryMethodHandle = reportMetricConfig.getQueryMethodHandle();
            List<ReportMetricData> invoke = null;

            try {
                log.info("{}执行指标方法,当前查询指标编码：({})", (useCache != null && useCache) ? "缓存未查询到数据," : "", code);
                if (queryMethodHandle != null) {
                    invoke = (List<ReportMetricData>) queryMethodHandle.invoke(sourceInstance,
                            DateUtil.format(date1, reportMetricConfig.getTimeFormat()),
                            DateUtil.format(date2, reportMetricConfig.getTimeFormat()));
                } else {
                    invoke = ReflectUtil.invoke(sourceInstance, queryMethod,
                            DateUtil.format(date1, reportMetricConfig.getTimeFormat()),
                            DateUtil.format(date2, reportMetricConfig.getTimeFormat()));
                }
            } catch (Throwable e) {
                throw new ReportMetricException(StrUtil.format("执行查询指标方法出现错误,当前错误CODE值: {} ,错误信息: {} ", reportMetricConfig.getCode(), e.getMessage()), e);
            }

            // 构建结果对象
            ReportMetricResult reportMetricResult = buildValueStruct(reportMetricConfig, invoke);

            // --- 写入缓存 无论是否开始使用缓存 ---
            // （不用入口方法统一写入缓存，并发情况下，双检索因为等待主方法慢会导致失效）
            redisUtil.set(redisKey, reportMetricResult, redisCacheTimeSeconds);

            return reportMetricResult;
        }
        // --- 同步块结束 ---
    }

    /**
     * 按时间查询指标值(通过构建SQL)，支持双重检查锁定以防止缓存击穿
     * <p>
     * 核心逻辑：
     * 1. 批量预检缓存 (MGET) -> 分离命中/未命中
     * 2. 对未命中部分，基于【表名】加锁 (细粒度锁)
     * 3. 锁内二次预检缓存 (防止等待锁期间其他线程已更新)
     * 4. 执行 SQL 查询 (同一张表的多个指标合并为一条 SQL)
     * 5. 解析结果并批量回写缓存
     *
     * @param dbConfigs 数据库配置列表（通常属于同一张表）
     * @param date1     开始时间
     * @param date2     结束时间
     * @param useCache  是否使用缓存
     * @return 指标结果映射
     */
    private Map<String, ReportMetricResult> getValueTimeIntervalByBuildSql(List<ReportMetricConfig> dbConfigs, Date date1, Date date2, Boolean useCache) {
        if (dbConfigs == null || dbConfigs.isEmpty()) {
            return new HashMap<>();
        }

        // 统一时间格式处理：如果结束时间为空，视为精确时间点查询
        if (date2 == null) {
            date2 = date1;
        }

        // 最终结果集：包含缓存命中 + 新查询的结果
        Map<String, ReportMetricResult> finalResult = new HashMap<>();
        // 待查询数据库的配置列表
        List<ReportMetricConfig> needDbQueryConfigs = new ArrayList<>();

        // --- 【第一重检查】批量筛选出缓存未命中的配置 (使用 MGET 优化) ---
        if (useCache != null && useCache) {
            Tuple<Map<String, ReportMetricResult>, List<ReportMetricConfig>> cacheCheckResult = batchGetCacheResultsByConfigs(dbConfigs, date1, date2);
            // 将命中的缓存结果直接放入最终结果集
            finalResult.putAll(cacheCheckResult.getFirst());
            // 未命中的配置进入待查询列表
            needDbQueryConfigs = cacheCheckResult.getSecond();
        } else {
            // 不使用缓存时，所有配置都需要查库
            needDbQueryConfigs.addAll(dbConfigs);
        }

        // 如果全部命中缓存，直接返回，无需查库
        if (needDbQueryConfigs.isEmpty()) {
            return finalResult;
        }

        // 获取锁对象：基于【表名】进行加锁
        // 保证同一张表的并发查询串行化，不同表之间互不阻塞，最大化并发性能
        String tableName = needDbQueryConfigs.get(0).getDbTableName();
        Object lock = getTableLock(tableName);

        // --- 【同步块开始】 ---
        synchronized (lock) {
            // --- 【第二重检查】拿到锁后，再次批量检查缓存 ---
            // 目的：防止在等待锁的过程中，其他持有锁的线程已经完成了查询并写入了缓存
            List<ReportMetricConfig> stillNeedQueryConfigs = new ArrayList<>();

            if (useCache != null && useCache) {
                Tuple<Map<String, ReportMetricResult>, List<ReportMetricConfig>> secondCheckResult = batchGetCacheResultsByConfigs(needDbQueryConfigs, date1, date2);

                // 将第二次检查中命中的缓存也加入最终结果
                finalResult.putAll(secondCheckResult.getFirst());

                // 依然未命中的，才真正需要执行 SQL
                stillNeedQueryConfigs = secondCheckResult.getSecond();
            } else {
                stillNeedQueryConfigs.addAll(needDbQueryConfigs);
            }

            // 如果第二重检查后发现都不需要查库了，直接返回
            if (stillNeedQueryConfigs.isEmpty()) {
                return finalResult;
            }

            // --- 【执行数据库查询】 ---
            List<Map<String, Object>> results = null;
            try {
                // 构建合并 SQL：将同一张表的多个指标字段合并到一条 SELECT 语句中
                Tuple<String, String> tSqlAndFormat = buildSql(stillNeedQueryConfigs);
                String sql = tSqlAndFormat.getFirst();
                String timeFormat = tSqlAndFormat.getSecond();
                String startTime = DateUtil.format(date1, timeFormat);
                String endTime = DateUtil.format(date2, timeFormat);
                log.debug("执行合并SQL查询, 表: {}, 指标数: {}", tableName, stillNeedQueryConfigs.size());
                results = jdbcTemplate.queryForList(sql, startTime, endTime);
            } catch (DataAccessException e) {
                String codes = stillNeedQueryConfigs.stream().map(ReportMetricConfig::getCode).collect(Collectors.joining(","));
                throw new ReportMetricException(StrUtil.format("执行报表指标编码查询sql失败,报表指标为: {}, 错误信息为: {}", codes, e.getMessage()), e);
            }

            // --- 【处理结果并回填缓存】 ---
            if (results != null && !results.isEmpty()) {
                for (ReportMetricConfig dbConfig : stillNeedQueryConfigs) {
                    // 从合并的结果集中提取当前指标的特定数据
                    ReportMetricResult reportMetricResult = processSingleConfigResult(dbConfig, results);

                    // 1. 存入内存结果集
                    finalResult.put(dbConfig.getCode(), reportMetricResult);
                }
            } else {
                // 如果 SQL 执行成功但返回空结果集，也需要为每个指标生成空结构，避免前端缺失 Key
                for (ReportMetricConfig dbConfig : stillNeedQueryConfigs) {
                    ReportMetricResult emptyResult = buildValueStruct(dbConfig, new ArrayList<>());
                    finalResult.put(dbConfig.getCode(), emptyResult);
                }
            }
            // --- 批量写入缓存无论是否开始使用缓存,保证双检索有效 ---
            // （不用入口方法统一写入缓存，并发情况下，双检索因为等待主方法慢会导致失效）
            batchSetCacheResults(finalResult, date1, date2, redisCacheTimeSeconds);
        }
        // --- 【同步块结束】 ---

        return finalResult;
    }


    /**
     * 辅助方法：从SQL查询结果集中提取单个指标的数据
     */
    private ReportMetricResult processSingleConfigResult(ReportMetricConfig dbConfig, List<Map<String, Object>> results) {
        String dbTableValueColumn = dbConfig.getDbTableValueColumn();
        String dbTableTimeColumn = dbConfig.getDbTableTimeColumn();
        String dbFilterColumn = dbConfig.getDbFilterColumn();
        String dbFilterColumnValue = dbConfig.getDbFilterColumnValue();

        Stream<Map<String, Object>> stream = results.stream();

        // 应用过滤条件
        if (StrUtil.isNotEmpty(dbFilterColumn)) {
            stream = stream.filter(map -> dbFilterColumnValue.equals(map.get(dbFilterColumn)));
        }

        List<ReportMetricData> collect = stream.map(map -> {
            Object dbTableTimeColumnValue = map.get(dbTableTimeColumn);
            String dateStr = "null";
            if (dbTableTimeColumnValue != null) {
                // 注意：这里假设数据库返回的是 LocalDateTime，如果是 Date 或其他类型需调整
                if (dbTableTimeColumnValue instanceof LocalDateTime) {
                    dateStr = DateUtil.format((LocalDateTime) dbTableTimeColumnValue, dbConfig.getTimeFormat());
                } else if (dbTableTimeColumnValue instanceof Date) {
                    dateStr = DateUtil.format((Date) dbTableTimeColumnValue, dbConfig.getTimeFormat());
                } else {
                    dateStr = dbTableTimeColumnValue.toString();
                }
            }

            Object valueObj = map.get(dbTableValueColumn);
            String valueStr = valueObj == null ? "null" : valueObj.toString();

            return new ReportMetricData()
                    .setValue(valueStr)
                    .setDate(dateStr);
        }).collect(Collectors.toList());

        return buildValueStruct(dbConfig, collect);
    }

    private Tuple<String, String> buildSql(List<ReportMetricConfig> dbConfigs) {
        ReportMetricException.throwByFlag(dbConfigs.size() == 0, "dbConfigs个数不能为空");

        ReportMetricConfig defaultReportMetricConfig = dbConfigs.get(0);
        String dbTableTimeColumn = defaultReportMetricConfig.getDbTableTimeColumn();
        String dbTableName = defaultReportMetricConfig.getDbTableName();
        String timeFormat = defaultReportMetricConfig.getTimeFormat();

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
        //存在没有过滤条件的选项
        boolean containsNotHaveDbFilterColumn = dbConfigs.stream()
                .map(ReportMetricConfig::getDbFilterColumn)
                .filter(StrUtil::isEmpty)
                .count() > 0;
        List<String> tableFilter = dbConfigs.stream()
                .filter(config -> StrUtil.isNotEmpty(config.getDbFilterColumn()))
                .map(config ->
                        StrUtil.format("( {} = '{}' )", config.getDbFilterColumn(), config.getDbFilterColumnValue()))
                .distinct()
                .collect(Collectors.toList());
        //如果存在没有过滤条件的选项 添加 1=1 会让所有过滤条件失效，查询全部，后续进行数据过滤。
        if (tableFilter.size() == 0 || containsNotHaveDbFilterColumn) {
            tableFilter.add("(1=1)");
        }
        List<String> selectCloumns = new ArrayList<>();
        selectCloumns.addAll(tableValueColumns);
        selectCloumns.addAll(dbFilterColumns);
        selectCloumns.add(dbTableTimeColumn);

        String format = StrUtil.format("SELECT {} FROM {} WHERE ( {} ) AND {} BETWEEN ? AND ?",
                StrUtil.join(",", selectCloumns),
                dbTableName,
                StrUtil.join(" OR ", tableFilter),
                dbTableTimeColumn
        );

        String codes = dbConfigs.stream().map(ReportMetricConfig::getCode).collect(Collectors.joining(","));
        log.info("构建查询SQL完成,当前指标编码：({}),查询SQL: \n{} ", codes, format);
        //PrintUtil.RED.Println("构建查询SQL完成,当前指标编码：({}),查询SQL: {} ", codes, format);
        return Tuple.newTuple(format, timeFormat);
    }


    /**
     * 构建指标值结构化数据
     *
     * @param reportMetricConfig 指标配置
     * @param invoke             查询结果值
     * @return 包含code/name/unit/status/data的映射表
     */
    private ReportMetricResult buildValueStruct(ReportMetricConfig reportMetricConfig, List<ReportMetricData> invoke) {
        ReportMetricResult reportMetricData = new ReportMetricResult();
        reportMetricData
                .setCode(reportMetricConfig.getCode())
                .setName(reportMetricConfig.getName())
                .setSourceReport(reportMetricConfig.getSourceReport())
                .setUnit(reportMetricConfig.getUnit())
                .setData(invoke)
                .setStatus("Success")
                .setMessage("查询成功");
        return reportMetricData;
    }

    /**
     * 构建未定义结果结构
     */
    private ReportMetricResult buildUndefinedStruct(String code) {
        ReportMetricConfig reportMetricConfig = new ReportMetricConfig()
                .setCode(code)
                .setName("未定义")
                .setUnit("-");
        ReportMetricResult reportMetricResult = buildValueStruct(reportMetricConfig, new ArrayList<>());
        reportMetricResult
                .setStatus("Undefined")
                .setMessage("查询成功");
        return reportMetricResult;
    }

    /**
     * 构建错误状态的结果对象
     */
    private ReportMetricResult buildErrorResult(ReportMetricConfig config, Exception e) {
        ReportMetricResult result = buildValueStruct(config, new ArrayList<>());
        result.setStatus("Error");
        result.setMessage(e.getMessage());
        return result;
    }
    //endregion

    //region redis操作方法

    /**
     * 批量从 Redis 获取指标缓存结果 (使用 MGET 优化)
     *
     * @param configs 指标配置列表
     * @param date1   开始时间
     * @param date2   结束时间
     * @return Tuple<命中缓存的结果Map, 未命中缓存的配置List>
     */
    private Tuple<Map<String, ReportMetricResult>, List<ReportMetricConfig>> batchGetCacheResultsByConfigs(List<ReportMetricConfig> configs, Date date1, Date date2) {
        if (configs == null || configs.isEmpty()) {
            return Tuple.newTuple(new HashMap<>(), new ArrayList<>());
        }
        List<String> codes = configs.stream().map(ReportMetricConfig::getCode).collect(Collectors.toList());
        Tuple<Map<String, ReportMetricResult>, List<String>> mapListTuple = batchGetCacheResultsByCodes(codes, date1, date2);
        Map<String, ReportMetricResult> hitMap = mapListTuple.getFirst();
        List<ReportMetricConfig> missList = mapListTuple.getSecond().stream().map(this::get).collect(Collectors.toList());
        return Tuple.newTuple(hitMap, missList);
    }

    /**
     * 重载方法：通过 Code 列表批量获取
     */
    private Tuple<Map<String, ReportMetricResult>, List<String>> batchGetCacheResultsByCodes(List<String> codes, Date date1, Date date2) {
        if (codes == null || codes.isEmpty()) {
            return Tuple.newTuple(new HashMap<>(), new ArrayList<>());
        }
        long time1 = date1.getTime();
        long time2 = (date2 == null) ? time1 : date2.getTime();
        List<String> keys = codes.stream()
                .map(code -> StrUtil.format("{}{}_{}_{}", redisKeyPrefix, code, time1, time2))
                .collect(Collectors.toList());

        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        Map<String, ReportMetricResult> hitMap = new HashMap<>();
        List<String> missList = new ArrayList<>();

        if (values != null) {
            for (int i = 0; i < codes.size(); i++) {
                String code = codes.get(i);
                Object val = values.get(i);
                if (val instanceof ReportMetricResult) {
                    hitMap.put(code, (ReportMetricResult) val);
                } else {
                    missList.add(code);
                }
            }
        } else {
            missList.addAll(codes);
        }

        return Tuple.newTuple(hitMap, missList);
    }

    /**
     * 批量将指标结果写入 Redis 缓存 (推荐方式：利用 RedisTemplate 自身序列化能力)
     * <p>
     * 步骤：
     * 1. 构建 Map<String, ReportMetricResult>
     * 2. 使用 opsForValue().multiSet 批量写入 (自动处理序列化)
     * 3. 使用 expire 批量设置过期时间
     *
     * @param resultMap     指标结果映射 (Key: Code, Value: ReportMetricResult)
     * @param date1         开始时间
     * @param date2         结束时间
     * @param expireSeconds 过期时间 (单位: 秒)
     */
    private void batchSetCacheResults(Map<String, ReportMetricResult> resultMap, Date date1, Date date2, long expireSeconds) {
        if (resultMap == null || resultMap.isEmpty()) {
            return;
        }

        long time1 = date1.getTime();
        long time2 = (date2 == null) ? time1 : date2.getTime();


        // 使用 executePipelined 执行批量操作
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, ReportMetricResult> entry : resultMap.entrySet()) {
                String code = entry.getKey();
                ReportMetricResult value = entry.getValue();

                if (value != null) {
                    // 1. 生成 Key
                    String redisKey = StrUtil.format("{}{}_{}_{}", redisKeyPrefix, code, time1, time2);

                    // 2. 序列化 Key 和 Value
                    byte[] keyBytes = redisTemplate.getStringSerializer().serialize(redisKey);
                    RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
                    byte[] valueBytes = valueSerializer.serialize(value);

                    if (keyBytes != null && valueBytes != null) {
                        // 3. 执行 SETEX 命令 (Set 值并设置过期时间)
                        // 注意：SETEX 是原子操作，比先 SET 后 EXPIRE 更安全且高效
                        connection.setEx(keyBytes, expireSeconds, valueBytes);
                    }
                }
            }
            // 返回 null 是 executePipelined 的要求
            return null;
        });
/* 这里不使用 mset 方式，是为了防止可能出现的网络问题导致没有设置过期时间使key 变成永久的问题。
        // 1. 构建 Redis Key-Value 映射 (Key: String, Value: ReportMetricResult)
        Map<String, ReportMetricResult> redisData = new HashMap<>(resultMap.size());
        for (Map.Entry<String, ReportMetricResult> entry : resultMap.entrySet()) {
            if (entry.getValue() != null) {
                String redisKey = StrUtil.format("{}{}_{}_{}", redisKeyPrefix, entry.getKey(), time1, time2);
                redisData.put(redisKey, entry.getValue());
            }
        }

        if (redisData.isEmpty()) {
            return;
        }

        try {
            // 2. 批量写入数据 (自动使用 RedisTemplate 配置的 ValueSerializer 进行序列化)
            redisTemplate.opsForValue().multiSet(redisData);

            // 3. 批量设置过期时间
            // 注意：multiSet 不支持带过期时间，所以需要单独设置
            // 为了性能，可以使用 Pipeline 设置过期时间，或者如果数据量不大(<1000)，直接循环设置也可接受
            List<String> keys = new ArrayList<>(redisData.keySet());

            // 使用 Pipeline 批量设置过期时间，减少网络 IO
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (String key : keys) {
                    byte[] rawKey = redisTemplate.getStringSerializer().serialize(key);
                    if (rawKey != null) {
                        connection.expire(rawKey, expireSeconds);
                    }
                }
                return null;
            });

            log.debug("批量写入缓存成功, 数量: {}, 过期时间: {}s", redisData.size(), expireSeconds);

        } catch (Exception e) {
            log.error("批量写入缓存失败", e);
        }*/
    }
    //endregion

}