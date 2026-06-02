
package io.github.forget_the_bright.rmetric.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 报表指标值类型枚举
 *
 * <p>定义指标数据值类型</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/24 12:46
 */
public enum ReportMetricValueType {
    /**
     * 瞬时值
     */
    PV("瞬时值"),

    /**
     * 累计值
     */
    SUM("累计值");

    private final String value;

    ReportMetricValueType(String value) {
        this.value = value;
    }

    /**
     * 获取报表指标值类型
     *
     * @return 中文描述
     */
    public String getValue() {
        return value;
    }

    /**
     * 返回报表指标值类型
     *
     * @return 中文描述
     */
    @Override
    @JsonValue
    public String toString() {
        return value;
    }
}