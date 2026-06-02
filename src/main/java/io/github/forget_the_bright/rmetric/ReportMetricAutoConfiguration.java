package io.github.forget_the_bright.rmetric;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import io.github.forget_the_bright.rmetric.ReportMetricExecutor;
import io.github.forget_the_bright.rmetric.ReportMetricMonitor;
import io.github.forget_the_bright.rmetric.ReportMetricRegistry;
import io.github.forget_the_bright.rmetric.cache.MetricCacheManager;
import io.github.forget_the_bright.rmetric.cache.MetricLockManager;
import io.github.forget_the_bright.rmetric.common.MetricDataManager;
import io.github.forget_the_bright.rmetric.common.MetricThreadManager;
import io.github.forget_the_bright.rmetric.controller.ReportMetricController;
import io.github.forget_the_bright.rmetric.strategy.MethodQueryStrategy;
import io.github.forget_the_bright.rmetric.strategy.QueryStrategy;
import io.github.forget_the_bright.rmetric.strategy.SqlQueryStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.sql.DataSource;

/**
 * 报表指标框架自动配置类
 *
 * <p>提供以下核心 Bean 的自动装配：</p>
 * <ul>
 *   <li>MetricDataManager - 指标数据管理器</li>
 *   <li>MetricCacheManager - 指标缓存管理器</li>
 *   <li>MetricLockManager - 指标锁管理器</li>
 *   <li>MetricThreadManager - 指标线程管理器</li>
 *   <li>ReportMetricExecutor - 指标查询执行器</li>
 *   <li>ReportMetricMonitor - 指标监控器</li>
 *   <li>ReportMetricRegistry - 指标注册器</li>
 *   <li>QueryStrategy - 查询策略实现（SQL + Method）</li>
 *   <li>ReportMetricController - REST API 控制器</li>
 * </ul>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/6/1
 */
@Configuration
@ConditionalOnClass({DataSource.class, DynamicRoutingDataSource.class})
public class ReportMetricAutoConfiguration {

    /**
     * 指标数据管理器
     *
     * @param dynamicRoutingDataSource 动态数据源（必需依赖）
     * @return MetricDataManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricDataManager metricDataManager(DynamicRoutingDataSource dynamicRoutingDataSource) {
        return new MetricDataManager(dynamicRoutingDataSource);
    }

    /**
     * 指标缓存管理器
     *
     * @return MetricCacheManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricCacheManager metricCacheManager() {
        return new MetricCacheManager();
    }

    /**
     * 指标锁管理器
     *
     * @return MetricLockManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricLockManager metricLockManager() {
        return new MetricLockManager();
    }

    /**
     * 指标线程管理器
     *
     * @return MetricThreadManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricThreadManager metricThreadManager() {
        return new MetricThreadManager();
    }

    /**
     * SQL 查询策略
     *
     * @return QueryStrategy 实现
     */
    @Bean
    @ConditionalOnMissingBean(name = "sqlQueryStrategy")
    public QueryStrategy sqlQueryStrategy() {
        return new SqlQueryStrategy();
    }

    /**
     * 方法查询策略
     *
     * @return QueryStrategy 实现
     */
    @Bean
    @ConditionalOnMissingBean(name = "methodQueryStrategy")
    public QueryStrategy methodQueryStrategy() {
        return new MethodQueryStrategy();
    }

    /**
     * 指标监控器
     *
     * @return ReportMetricMonitor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ReportMetricMonitor reportMetricMonitor(MeterRegistry meterRegistry) {
        return new ReportMetricMonitor(meterRegistry);
    }

    /**
     * 指标查询执行器
     *
     * @param metricDataManager 指标数据管理器
     * @param queryStrategies   查询策略列表（自动注入所有 QueryStrategy Bean）
     * @param cacheManager      缓存管理器
     * @param metricMonitor     监控器
     * @return ReportMetricExecutor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ReportMetricExecutor reportMetricExecutor(
            MetricDataManager metricDataManager,
            java.util.List<QueryStrategy> queryStrategies,
            MetricCacheManager cacheManager,
            ReportMetricMonitor metricMonitor
    ) {
        return new ReportMetricExecutor();
    }

    /**
     * 指标注册器
     *
     * <p>在应用启动时自动扫描所有标注了 @ReportMetric 的 Bean，
     * 并注册指标配置到 MetricDataManager 中。</p>
     *
     * @param applicationContext  Spring 应用上下文
     * @param metricDataManager   指标数据管理器
     * @return ReportMetricRegistry 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ReportMetricRegistry reportMetricRegistry(
            ApplicationContext applicationContext,
            MetricDataManager metricDataManager
    ) {
        return new ReportMetricRegistry(applicationContext, metricDataManager);
    }

    /**
     * RedisTemplate<String, Object> 配置
     *
     * <p>当容器中不存在 RedisTemplate<String, Object> 类型的 Bean 时，自动创建。</p>
     * <p>Key 使用 String 序列化，Value 使用 JSON 序列化，支持存储复杂对象（如 ReportMetricResult）。</p>
     *
     * @param connectionFactory Redis 连接工厂（由 Spring Boot 自动配置提供）
     * @return 配置好的 RedisTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 使用 String 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 使用 JSON 序列化（支持 ReportMetricResult 等复杂对象）
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
