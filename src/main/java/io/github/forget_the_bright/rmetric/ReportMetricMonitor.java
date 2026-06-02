package io.github.forget_the_bright.rmetric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 指标查询监控管理器
 *
 * <p>基于 Micrometer 采集关键性能指标，支持 Prometheus/Grafana 可视化。</p>
 */
@Slf4j
@Component
public class ReportMetricMonitor {

    private final MeterRegistry meterRegistry;

    /* 查询耗时计时器（秒） */
    private final Timer queryTimer;

    /* 缓存命中计数器 */
    private final Counter cacheHitCounter;

    /* 缓存未命中计数器 */
    private final Counter cacheMissCounter;

    /* 查询失败计数器 */
    private final Counter queryErrorCounter;

    /* SQL 查询计数器 */
    private final Counter sqlQueryCounter;

    /* Method 查询计数器 */
    private final Counter methodQueryCounter;

    @Autowired
    public ReportMetricMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        /* 初始化监控指标 */
        this.queryTimer = Timer.builder("report.metric.query.duration")
                .description("报表指标查询耗时")
                .tag("unit", "seconds")
                .register(meterRegistry);

        this.cacheHitCounter = Counter.builder("report.metric.cache.hit")
                .description("缓存命中次数")
                .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("report.metric.cache.miss")
                .description("缓存未命中次数")
                .register(meterRegistry);

        this.queryErrorCounter = Counter.builder("report.metric.query.error")
                .description("查询失败次数")
                .register(meterRegistry);

        this.sqlQueryCounter = Counter.builder("report.metric.query.sql")
                .description("SQL 查询次数")
                .register(meterRegistry);

        this.methodQueryCounter = Counter.builder("report.metric.query.method")
                .description("Method 查询次数")
                .register(meterRegistry);
    }

    /**
     * 记录查询耗时
     *
     * @param durationMs 耗时（毫秒）
     */
    public void recordQueryDuration(long durationMs) {
        queryTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    /**
     * 记录查询失败
     */
    public void recordQueryError() {
        queryErrorCounter.increment();
    }

    /**
     * 记录 SQL 查询
     */
    public void recordSqlQuery() {
        sqlQueryCounter.increment();
    }

    /**
     * 记录 Method 查询
     */
    public void recordMethodQuery() {
        methodQueryCounter.increment();
    }

    /**
     * 计算缓存命中率
     *
     * @return 命中率（0.0 ~ 1.0）
     */
    public double getCacheHitRate() {
        long hits = (long) cacheHitCounter.count();
        long misses = (long) cacheMissCounter.count();
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
