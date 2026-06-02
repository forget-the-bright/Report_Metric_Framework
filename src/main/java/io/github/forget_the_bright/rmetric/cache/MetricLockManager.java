package io.github.forget_the_bright.rmetric.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 指标查询锁管理器
 *
 * <p>基于 Caffeine 本地缓存实现细粒度分布式锁，用于防止并发场景下的缓存击穿问题。</p>
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li><b>表级锁</b>：针对 SQL 查询策略，按表名加锁，避免同表并发重复查询数据库</li>
 *   <li><b>方法级锁</b>：针对 Method 查询策略，按指标编码加锁，避免同一指标并发重复执行</li>
 *   <li><b>自动过期</b>：基于最后访问时间自动清理锁对象，避免内存泄漏</li>
 *   <li><b>线程安全</b>：Caffeine 的 get() 方法保证原子性，确保同一 Key 只创建一个锁对象</li>
 * </ul>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/28 10:24
 */
@Component
public class MetricLockManager {

    /**
     * 表级锁缓存（用于 SQL 查询策略）
     *
     * <p>配置说明：</p>
     * <ul>
     *   <li>过期策略：访问后 10 分钟过期（适合低频访问的表）</li>
     *   <li>初始容量：100（预估系统涉及的表数量）</li>
     *   <li>最大容量：1000（超出后按 LRU 淘汰）</li>
     * </ul>
     */
    private final Cache<String, Object> tableLockCache = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .initialCapacity(100)
            .maximumSize(1000)
            .build();

    /**
     * 方法级锁缓存（用于 Method 查询策略）
     *
     * <p>配置说明：</p>
     * <ul>
     *   <li>过期策略：访问后 5 分钟过期（适合高频访问的指标）</li>
     *   <li>初始容量：100（预估常用指标数量）</li>
     *   <li>最大容量：5000（超出后按 LRU 淘汰）</li>
     * </ul>
     */
    private final Cache<String, Object> methodLockCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .initialCapacity(100)
            .maximumSize(5000)
            .build();

    /**
     * 获取表级锁对象
     *
     * <p>利用 Caffeine 的原子性 get() 方法，确保同一表名只创建一个锁对象。
     * 如果锁对象已存在则直接返回，不存在则创建新对象并缓存。</p>
     *
     * @param tableName 数据库表名
     * @return 该表对应的锁对象（用于 synchronized 同步块）
     */
    public Object getTableLock(String tableName) {
        /* Caffeine.get() 保证原子性：Key 不存在时调用 mappingFunction 创建新对象 */
        return tableLockCache.get(tableName, key -> new Object());
    }

    /**
     * 获取方法级锁对象
     *
     * <p>利用 Caffeine 的原子性 get() 方法，确保同一指标编码只创建一个锁对象。
     * 如果锁对象已存在则直接返回，不存在则创建新对象并缓存。</p>
     *
     * @param code 指标编码
     * @return 该指标对应的锁对象（用于 synchronized 同步块）
     */
    public Object getMethodLock(String code) {
        /* Caffeine.get() 保证原子性：Key 不存在时调用 mappingFunction 创建新对象 */
        return methodLockCache.get(code, key -> new Object());
    }
}
