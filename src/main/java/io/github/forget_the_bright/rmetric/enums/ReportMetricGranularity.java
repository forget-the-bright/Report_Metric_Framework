
package io.github.forget_the_bright.rmetric.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 报表指标时间粒度枚举
 *
 * <p>定义指标数据的时间统计维度</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/24 09:52
 */
public enum ReportMetricGranularity {
    /**
     * 天
     */
    DAY("天"),

    /**
     * 小时
     */
    HOUR("小时"),

    /**
     * 分钟
     */
    MINUTE("分钟"),

    /**
     * 秒
     */
    SECOND("秒");

    private String value;

    ReportMetricGranularity(String value) {
        this.value = value;
    }

    /**
     * 获取粒度的中文描述
     *
     * @return 中文描述
     */
    public String getValue() {
        return value;
    }

    /**
     * 返回粒度的中文描述
     *
     * @return 中文描述
     */
    @Override
    @JsonValue
    public String toString() {
        return value;
    }
}