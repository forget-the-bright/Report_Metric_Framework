package io.github.forget_the_bright.rmetric.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * 指标查询线程管理器
 *
 * <p>提供基于 ForkJoinPool 的隔离线程池，用于执行报表指标查询任务。</p>
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li><b>线程隔离</b>：避免并行流污染 Tomcat 公共线程池，保障 Web 服务稳定性</li>
 *   <li><b>IO 密集型优化</b>：线程数 = CPU 核数 * 2，适配数据库/Redis 等待场景</li>
 *   <li><b>线程命名</b>：自定义线程名称（MetricQuery-Worker-X），便于日志追踪和问题排查</li>
 *   <li><b>线程继承</b>：利用 ForkJoinPool 特性，使内部 parallelStream() 自动继承该线程池</li>
 * </ul>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/28 11:07
 */
@Slf4j
@Component
public class MetricThreadManager {

    /* IO 密集型线程池配置：CPU 核数 * 2，适配数据库/Redis 等待场景 */
    private final int poolSize = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * 自定义线程工厂，设置工作线程名称为 "MetricQuery-Worker-{ID}"
     *
     * <p>便于在日志中识别指标查询线程，快速定位问题。</p>
     */
    private final ForkJoinPool.ForkJoinWorkerThreadFactory metricQueryThreadFactory = pool -> {
        /* 创建默认的 worker 线程 */
        ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        t.setName("MetricQuery-Worker-" + t.getId());
        return t;
    };

    /**
     * 指标查询专用 ForkJoinPool
     *
     * <p>参数说明：</p>
     * <ul>
     *   <li>parallelism: 并行度（线程数）</li>
     *   <li>threadFactory: 自定义线程工厂（设置线程名）</li>
     *   <li>handler: 异常处理器（null 表示使用默认策略）</li>
     *   <li>asyncMode: false 表示使用 LIFO 模式（适合递归任务）</li>
     * </ul>
     */
    private final ForkJoinPool metricQueryPool = new ForkJoinPool(poolSize, metricQueryThreadFactory, null, false);

    /**
     * 在隔离线程池中执行任务
     *
     * <p>利用 ForkJoinPool 的线程继承特性，使任务内部的 parallelStream() 自动在该线程池中执行。</p>
     *
     * @param task 待执行的任务（Callable）
     * @param <T>  返回值类型
     * @return 任务执行结果
     */
    public <T> T execute(Callable<T> task) {
        /* 提交任务到 metricQueryPool，并阻塞等待结果 */
        return metricQueryPool.submit(task).join();
    }
}
