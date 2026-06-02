package io.github.forget_the_bright.rmetric.cache;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import io.github.forget_the_bright.rmetric.model.Tuple;
import io.github.forget_the_bright.rmetric.ReportMetricMonitor;
import io.github.forget_the_bright.rmetric.common.MetricDataManager;
import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 指标缓存管理器
 *
 * <p>负责报表指标查询结果的 Redis 缓存管理，提供以下核心功能：</p>
 * <ul>
 *   <li><b>缓存键构建</b>：统一生成包含指标编码和时间戳的 Redis Key</li>
 *   <li><b>批量缓存读取</b>：使用 MGET 优化批量查询性能，减少网络往返</li>
 *   <li><b>批量缓存写入</b>：使用 Pipeline + SETEX 原子操作，确保过期时间设置安全</li>
 *   <li><b>多级过期策略</b>：支持传入值 > 配置值 > 默认值（60秒）的优先级机制</li>
 * </ul>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/28 10:24
 */
@Slf4j
@Component
public class MetricCacheManager {

    /**
     * Redis Key 前缀，用于隔离不同模块的缓存数据
     */
    public static final String REDIS_KEY_PREFIX = "Base:ReportMetric:";

    /**
     * 默认缓存过期时间（秒）
     */
    public static final Long redisCacheTimeSeconds = 60L;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MetricDataManager metricDataManager;

    @Autowired
    private ReportMetricMonitor metricMonitor;

    /**
     * 构建 Redis 缓存键（Date 版本）
     *
     * @param code  指标编码
     * @param time1 开始时间
     * @param time2 结束时间（为空时视为精确查询，使用 time1）
     * @return Redis Key，格式：{prefix}{code}_{time1Millis}_{time2Millis}
     */
    public String buildRedisKey(String code, Date time1, Date time2) {
        if (time2 == null) {
            time2 = time1;
        }
        return buildRedisKey(code, time1.getTime(), time2.getTime());
    }

    /**
     * 构建 Redis 缓存键（时间戳版本）
     *
     * @param code  指标编码
     * @param time1 开始时间戳（毫秒）
     * @param time2 结束时间戳（毫秒）
     * @return Redis Key，格式：{prefix}{code}_{time1Millis}_{time2Millis}
     */
    public String buildRedisKey(String code, long time1, long time2) {
        return StrUtil.format("{}{}_{}_{}", REDIS_KEY_PREFIX, code, time1, time2);
    }

    /**
     * 从 Redis 获取单个缓存值
     *
     * @param key Redis Key
     * @param <T> 返回值类型
     * @return 缓存的对象，不存在则返回 null
     */
    public <T> T get(String key) {
        T value = (T) redisTemplate.opsForValue().get(key);
        if (value == null) {
            metricMonitor.recordCacheMiss();
        } else {
            metricMonitor.recordCacheHit();
        }
        return value;
    }

    /**
     * 设置单个缓存值（使用默认过期时间）
     *
     * @param key   Redis Key
     * @param value 缓存值
     */
    public void set(String key, Object value) {
        set(key, value, redisCacheTimeSeconds);
    }

    /**
     * 设置单个缓存值（自定义过期时间）
     *
     * @param key           Redis Key
     * @param value         缓存值
     * @param expireSeconds 过期时间（秒）
     */
    public void set(String key, Object value, long expireSeconds) {
        redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 批量从 Redis 获取指标缓存结果（基于配置列表）
     *
     * <p>内部调用 {@link #batchGetByCodes(List, Date, Date)} 实现 MGET 优化。</p>
     *
     * @param configs 指标配置列表
     * @param date1   开始时间
     * @param date2   结束时间
     * @return Tuple<命中缓存的结果Map, 未命中缓存的配置List>
     */
    public Tuple<Map<String, ReportMetricResult>, List<ReportMetricConfig>> batchGetByConfigs(List<ReportMetricConfig> configs, Date date1, Date date2) {
        /* 空列表快速返回 */
        if (configs == null || configs.isEmpty()) {
            return Tuple.newTuple(new HashMap<>(), new ArrayList<>());
        }

        /* 提取编码列表并调用批量查询 */
        List<String> codes = configs.stream().map(ReportMetricConfig::getCode).collect(Collectors.toList());
        Tuple<Map<String, ReportMetricResult>, List<String>> mapListTuple = batchGetByCodes(codes, date1, date2);

        /* 将未命中的编码转换回配置对象 */
        Map<String, ReportMetricResult> hitMap = mapListTuple.getFirst();
        List<ReportMetricConfig> missList = mapListTuple.getSecond().stream()
                .map(code -> metricDataManager.get(code))
                .collect(Collectors.toList());

        return Tuple.newTuple(hitMap, missList);
    }

    /**
     * 批量从 Redis 获取指标缓存结果（基于编码列表）
     *
     * <p>使用 Redis MGET 命令一次性获取多个 Key 的值，减少网络往返次数。</p>
     *
     * @param codes 指标编码列表
     * @param date1 开始时间
     * @param date2 结束时间
     * @return Tuple<命中缓存的结果Map, 未命中缓存的编码List>
     */
    public Tuple<Map<String, ReportMetricResult>, List<String>> batchGetByCodes(List<String> codes, Date date1, Date date2) {
        /* 空列表快速返回 */
        if (codes == null || codes.isEmpty()) {
            return Tuple.newTuple(new HashMap<>(), new ArrayList<>());
        }

        /* 计算时间戳并构建所有 Redis Keys */
        long time1 = date1.getTime();
        long time2 = (date2 == null) ? time1 : date2.getTime();

        List<String> keys = codes.stream()
                .map(code -> buildRedisKey(code, time1, time2))
                .collect(Collectors.toList());

        /* 执行 MGET 批量查询 */
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        /* 分离命中和未命中的结果 */
        Map<String, ReportMetricResult> hitMap = new HashMap<>();
        List<String> missList = new ArrayList<>();

        if (values != null) {
            for (int i = 0; i < codes.size(); i++) {
                String code = codes.get(i);
                Object val = values.get(i);
                if (val instanceof ReportMetricResult) {
                    /* 缓存命中 */
                    metricMonitor.recordCacheHit();
                    hitMap.put(code, (ReportMetricResult) val);
                } else {
                    /* 缓存未命中 */
                    metricMonitor.recordCacheMiss();
                    missList.add(code);
                }
            }
        } else {
            /* MGET 返回 null 表示所有 Key 都不存在 */
            missList.addAll(codes);
            codes.forEach(code -> metricMonitor.recordCacheMiss());
        }

        return Tuple.newTuple(hitMap, missList);
    }

    /**
     * 批量将指标结果写入 Redis 缓存（使用默认过期时间）
     *
     * @param resultMap 指标结果映射（Key: Code, Value: ReportMetricResult）
     * @param date1     开始时间
     * @param date2     结束时间
     */
    public void batchSet(Map<String, ReportMetricResult> resultMap, Date date1, Date date2) {
        batchSet(resultMap, date1, date2, 0);
    }

    /**
     * 批量将指标结果写入 Redis 缓存（推荐方式：Pipeline + SETEX）
     *
     * <p>核心优势：</p>
     * <ul>
     *   <li><b>Pipeline 批量执行</b>：减少网络往返次数，提升写入性能</li>
     *   <li><b>SETEX 原子操作</b>：避免先 SET 后 EXPIRE 可能导致的永久 Key 问题</li>
     *   <li><b>多级过期策略</b>：expireSeconds > config.cacheTime > 默认值 60s</li>
     * </ul>
     *
     * @param resultMap     指标结果映射（Key: Code, Value: ReportMetricResult）
     * @param date1         开始时间
     * @param date2         结束时间
     * @param expireSeconds 过期时间（秒），0 表示使用配置值或默认值
     */
    public void batchSet(Map<String, ReportMetricResult> resultMap, Date date1, Date date2, long expireSeconds) {
        if (resultMap == null || resultMap.isEmpty()) {
            return;
        }

        long time1 = date1.getTime();
        long time2 = (date2 == null) ? time1 : date2.getTime();

        /* 使用 Pipeline 执行批量 SETEX 操作 */
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, ReportMetricResult> entry : resultMap.entrySet()) {
                String code = entry.getKey();
                ReportMetricResult value = entry.getValue();

                if (value != null) {
                    /* 生成 Redis Key */
                    String redisKey = StrUtil.format("{}{}_{}_{}", REDIS_KEY_PREFIX, code, time1, time2);

                    /* 计算过期时间：传入值 > 配置值 > 默认值 */
                    long configCacheTime = value.getCacheTime();
                    long finalExpireSeconds = expireSeconds > 0 ? expireSeconds :
                            (configCacheTime > 0 ? configCacheTime : redisCacheTimeSeconds);

                    /* 序列化 Key 和 Value */
                    byte[] keyBytes = redisTemplate.getStringSerializer().serialize(redisKey);
                    RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
                    byte[] valueBytes = valueSerializer.serialize(value);

                    if (keyBytes != null && valueBytes != null) {
                        /* 执行 SETEX 命令（原子操作：设置值 + 过期时间） */
                        connection.setEx(keyBytes, finalExpireSeconds, valueBytes);
                    }
                }
            }
            /* executePipelined 要求返回 null */
            return null;
        });
    }

    /**
     * 备用方案：使用 multiSet + Pipeline EXPIRE 批量写入缓存
     *
     * <p>注意：此方法已不再使用，保留作为参考。
     * 原因：multiSet 不支持原子设置过期时间，需额外调用 EXPIRE，存在网络故障导致永久 Key 的风险。</p>
     *
     * @param resultMap     指标结果映射
     * @param date1         开始时间
     * @param date2         结束时间
     * @param expireSeconds 过期时间（秒）
     */
    public void batchMSet(Map<String, ReportMetricResult> resultMap, Date date1, Date date2, long expireSeconds) {
        if (resultMap == null || resultMap.isEmpty()) {
            return;
        }

        long time1 = date1.getTime();
        long time2 = (date2 == null) ? time1 : date2.getTime();

        /* 构建 Redis Key-Value 映射 */
        Map<String, ReportMetricResult> redisData = new HashMap<>(resultMap.size());
        resultMap.forEach((code, value) -> {
            if (value != null) {
                String redisKey = buildRedisKey(code, time1, time2);
                redisData.put(redisKey, value);
            }
        });

        if (redisData.isEmpty()) {
            return;
        }

        long finalExpireSeconds = expireSeconds > 0 ? expireSeconds : redisCacheTimeSeconds;

        try {
            /* 批量写入数据（自动使用 RedisTemplate 配置的 ValueSerializer 进行序列化） */
            redisTemplate.opsForValue().multiSet(redisData);

            /* 使用 Pipeline 批量设置过期时间，减少网络 IO */
            List<String> keys = new ArrayList<>(redisData.keySet());
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (String key : keys) {
                    byte[] rawKey = redisTemplate.getStringSerializer().serialize(key);
                    if (rawKey != null) {
                        connection.expire(rawKey, finalExpireSeconds);
                    }
                }
                return null;
            });

            log.debug("批量写入缓存成功, 数量: {}, 过期时间: {}s", redisData.size(), finalExpireSeconds);

        } catch (Exception e) {
            log.error("批量写入缓存失败", e);
        }
    }
}
