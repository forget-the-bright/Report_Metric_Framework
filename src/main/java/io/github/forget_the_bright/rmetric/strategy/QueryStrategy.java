package io.github.forget_the_bright.rmetric.strategy;

import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricResult;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 【接口】查询策略
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/28 10:23
 */
public interface QueryStrategy {

    /**
     * 判断是否支持该配置
     * @param config 指标配置
     * @return true=支持，false=不支持
     */
    boolean supports(ReportMetricConfig config);

    /**
     * 执行查询
     *
     * @param configs  指标配置列表
     * @param start    开始时间
     * @param end      结束时间
     * @param useCache 是否使用缓存
     * @return 查询结果
     */
    Map<String, ReportMetricResult> query(List<ReportMetricConfig> configs, Date start, Date end, Boolean useCache);

    /**
     * 执行预览查询
     *
     * <p>用于在执行正式查询前，预览指标配置对应的 SQL 查询语句或数据源信息。</p>
     *
     * @param value 指标配置列表
     * @param start 开始时间
     * @param end  结束时间
     * @return 预览结果映射（Key: 指标编码, Value: 预览信息如 SQL 语句）
     */
    Map<String, Object> previewProcessQuery(List<ReportMetricConfig> value, Date start, Date end);
}
