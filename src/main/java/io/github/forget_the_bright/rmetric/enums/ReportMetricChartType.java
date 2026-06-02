
package io.github.forget_the_bright.rmetric.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 报表指标图表类型枚举
 *
 * <p>定义指标数据在报表中展示的图表形式</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/24 12:46
 */
public enum ReportMetricChartType {
    /**
     * 柱状图 (Bar Chart)
     */
    BAR("柱状图"),

    /**
     * 折线图 (Scatter Plot)
     */
    SCATTER("折线图");

    private final String value;

    ReportMetricChartType(String value) {
        this.value = value;
    }

    /**
     * 获取图表类型的中文描述
     *
     * @return 中文描述
     */
    public String getValue() {
        return value;
    }

    /**
     * 返回图表类型的中文描述
     *
     * @return 中文描述
     */
    @Override
    @JsonValue
    public String toString() {
        return value;
    }
}