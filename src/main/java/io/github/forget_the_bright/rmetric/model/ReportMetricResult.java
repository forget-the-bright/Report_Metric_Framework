package io.github.forget_the_bright.rmetric.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import io.github.forget_the_bright.rmetric.enums.ReportMetricChartType;
import io.github.forget_the_bright.rmetric.enums.ReportMetricGranularity;

import java.util.List;

/**
 * 报表指标查询结果对象
 *
 * <p>封装单个指标查询后的完整返回信息，包含元数据、状态及具体的时序数据列表</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/24 13:52
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "ReportMetricResult对象", description = "基础模块_报表指标结果对象")
public class ReportMetricResult {

    /**
     * 指标编码(唯一标识)
     */
    @ApiModelProperty(value = "指标编码")
    private String code;

    /**
     * 指标名称
     */
    @ApiModelProperty(value = "指标名称")
    private String name;

    /**
     * 来源报表名称
     */
    @ApiModelProperty(value = "来源报表")
    private String sourceReport;

    /**
     * 指标值单位
     */
    @ApiModelProperty(value = "指标值单位")
    private String unit;
    /**
     * 时间粒度(天、小时等)
     */
    @ApiModelProperty(value = "时间粒度（天、小时等）")
    private ReportMetricGranularity granularity;

    /**
     * 指标图表类型
     */
    @ApiModelProperty(value = "指标图表类型")
    private ReportMetricChartType chartType;

    /**
     * 指标缓存时间
     */
    @ApiModelProperty(value = "指标缓存时间")
    @JsonIgnore
    private long cacheTime;

    /**
     * 状态编码:Undefined,Success
     */
    @ApiModelProperty(value = "状态编码:Undefined,Success,Error")
    private String status;

    /**
     * 状态消息
     */
    @ApiModelProperty(value = "状态消息")
    private String message;

    /**
     * 指标值
     */
    @ApiModelProperty(value = "指标值")
    private List<ReportMetricData> data;
}
