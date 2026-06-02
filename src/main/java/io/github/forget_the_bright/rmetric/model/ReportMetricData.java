
package io.github.forget_the_bright.rmetric.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表指标数据VO
 *
 * <p>用于封装报表指标的时序数据</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/24 14:06
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "ReportMetricData对象", description = "基础模块_报表指标数据对象")
public class ReportMetricData {
    /**
     * 日期时间
     */
    @ApiModelProperty(value = "日期时间")
    private String date;

    /**
     * 指标值
     */
    @ApiModelProperty(value = "指标值")
    private String value;

    public static List<ReportMetricData> convertMaps(List<Map<String, Object>> maps) {
        return maps.stream().map(map ->{
            Object date = map.get("date");
            Object value = map.get("value");
            ReportMetricData reportMetricData = new ReportMetricData()
                    .setDate(date == null ? null : date.toString())
                    .setValue(value == null ? null : value.toString());
            return reportMetricData;
        }).collect(Collectors.toList());
    }
}