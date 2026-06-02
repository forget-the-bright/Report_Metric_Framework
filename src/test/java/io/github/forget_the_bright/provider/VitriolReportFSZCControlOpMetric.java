package io.github.forget_the_bright.provider;

import io.github.forget_the_bright.rmetric.annotation.ReportMetric;
import io.github.forget_the_bright.rmetric.annotation.ReportMetricOperation;
import io.github.forget_the_bright.rmetric.enums.ReportMetricChartType;
import io.github.forget_the_bright.rmetric.enums.ReportMetricGranularity;
import io.github.forget_the_bright.rmetric.enums.ReportMetricValueType;
import io.github.forget_the_bright.rmetric.model.ReportMetricData;

import java.util.List;

/**
 * 示例SQL类型指标提供者
 * 
 * <p>演示如何通过SQL查询获取报表指标数据</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/6/1
 */
@ReportMetric(
        reportName = "废酸主操工作台_业务填报",
        workShop = "硫酸车间"
        //  dataSource = "prod" // 测试多数据源
)
public class VitriolReportFSZCControlOpMetric {
    private final String DbFilterColumn = "pressfilter_id";
    private final String DbTableName = "mes_vitriol_wastewater_pressfilter";
    private final String DbTableTimeColumn = "DATE_FORMAT(feeding_time, '%Y-%m-%d %H')";
    private final String TimeFormat = "yyyy-MM-dd HH";

    @ReportMetricOperation(
            code = "LS-0020",
            name = "硫化1#压滤机下料记录 - 进料压力",
            valueType = ReportMetricValueType.PV,
            unit = "Mpa",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.SCATTER,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "inlet_pressure",//取值属性
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "硫化1#",
            sort = 1
    )
    public List<ReportMetricData> getLS0020(String startTime, String endTime) {
        // 建议：明确抛出异常或返回空，避免歧义
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0021",
            name = "硫化1#压滤机下料记录 - 滤饼厚度",
            valueType = ReportMetricValueType.SUM,
            unit = "cm",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.BAR,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "filter_cake_thickness",//取值属性
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "硫化1#",
            sort = 2
    )
    public List<ReportMetricData> getLS0021(String startTime, String endTime) {
        // 建议：明确抛出异常或返回空，避免歧义
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }


    @ReportMetricOperation(
            code = "LS-0022",
            name = "硫化1#压滤机下料记录 - 下料量",
            valueType = ReportMetricValueType.SUM,
            unit = "t",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.BAR,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "feeding_amount", //取值属性
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "硫化1#",
            sort = 3
    )
    public List<ReportMetricData> getLS0022(String startTime, String endTime) {
        // 建议：明确抛出异常或返回空，避免歧义
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0023",
            name = "硫化2#压滤机下料记录 - 进料压力",
            valueType = ReportMetricValueType.PV,
            unit = "Mpa",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.SCATTER,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "inlet_pressure",
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "硫化2#",
            sort = 4
    )
    public List<ReportMetricData> getLS0023(String startTime, String endTime) {
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0024",
            name = "硫化2#压滤机下料记录 - 滤饼厚度",
            valueType = ReportMetricValueType.SUM,
            unit = "cm",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.BAR,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "filter_cake_thickness",
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "硫化2#",
            sort = 5
    )
    public List<ReportMetricData> getLS0024(String startTime, String endTime) {
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0025",
            name = "硫化2#压滤机下料记录 - 下料量",
            valueType = ReportMetricValueType.SUM,
            unit = "t",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.BAR,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "feeding_amount",
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "硫化2#",
            sort = 6
    )
    public List<ReportMetricData> getLS0025(String startTime, String endTime) {
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0026",
            name = "中和1#压滤机下料记录 - 进料压力",
            valueType = ReportMetricValueType.PV,
            unit = "Mpa",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.SCATTER,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "inlet_pressure",
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "中和1#",
            sort = 7
    )
    public List<ReportMetricData> getLS0026(String startTime, String endTime) {
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0027",
            name = "中和1#压滤机下料记录 - 滤饼厚度",
            valueType = ReportMetricValueType.SUM,
            unit = "cm",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.BAR,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "filter_cake_thickness",
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "中和1#",
            sort = 8
    )
    public List<ReportMetricData> getLS0027(String startTime, String endTime) {
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0028",
            name = "中和1#压滤机下料记录 - 下料量",
            valueType = ReportMetricValueType.SUM,
            unit = "t",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.BAR,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "feeding_amount",
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "中和1#",
            sort = 9
    )
    public List<ReportMetricData> getLS0028(String startTime, String endTime) {
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0029",
            name = "中和2#压滤机下料记录 - 进料压力",
            valueType = ReportMetricValueType.PV,
            unit = "Mpa",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.SCATTER,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "inlet_pressure",
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "中和2#",
            sort = 10
    )
    public List<ReportMetricData> getLS0029(String startTime, String endTime) {
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0030",
            name = "中和2#压滤机下料记录 - 滤饼厚度",
            valueType = ReportMetricValueType.SUM,
            unit = "cm",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.BAR,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "filter_cake_thickness",
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "中和2#",
            sort = 11
    )
    public List<ReportMetricData> getLS0030(String startTime, String endTime) {
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }

    @ReportMetricOperation(
            code = "LS-0031",
            name = "中和2#压滤机下料记录 - 下料量",
            valueType = ReportMetricValueType.SUM,
            unit = "t",
            granularity = ReportMetricGranularity.HOUR,
            chartType = ReportMetricChartType.BAR,
            timeFormat = TimeFormat,
            dbTableName = DbTableName,
            dbTableTimeColumn = DbTableTimeColumn,
            dbTableValueColumn = "feeding_amount",
            dbFilterColumn = DbFilterColumn,
            dbFilterColumnValue = "中和2#",
            sort = 12
    )
    public List<ReportMetricData> getLS0031(String startTime, String endTime) {
        throw new UnsupportedOperationException("该指标由框架自动构建SQL查询，无需执行此方法");
    }
}
