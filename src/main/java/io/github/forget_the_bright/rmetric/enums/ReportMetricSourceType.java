
package io.github.forget_the_bright.rmetric.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 报表指标值数据来源
 *
 * <p>定义报表指标值数据来源类型</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/24 12:46
 */
public enum ReportMetricSourceType {
    /**
     * 报表填报
     */
    REPORT("报表填报"),

    /**
     * 数采
     */
    SCADE("数采");

    private final String value;

    ReportMetricSourceType(String value) {
        this.value = value;
    }

    /**
     * 获取报表指标值数据来源类型
     *
     * @return 中文描述
     */
    public String getValue() {
        return value;
    }

    /**
     * 返回报表指标值数据来源类型
     *
     * @return 中文描述
     */
    @Override
    @JsonValue
    public String toString() {
        return value;
    }
}