package io.github.forget_the_bright.rmetric.controller;

import cn.hutool.core.util.StrUtil;
import io.github.forget_the_bright.rmetric.ReportMetricExecutor;
import io.github.forget_the_bright.rmetric.ReportMetricInvokeHandler;
import io.github.forget_the_bright.rmetric.ReportMetricRegistry;
import io.github.forget_the_bright.rmetric.common.Maps;
import io.github.forget_the_bright.rmetric.common.MetricDataManager;
import io.github.forget_the_bright.rmetric.model.Page;
import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricResult;
import io.github.forget_the_bright.rmetric.model.Result;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 报表指标管理控制器
 *
 * <p>提供报表指标的元数据查询、配置导出以及指标数据获取接口</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/24 10:53
 */
@Slf4j
public class ReportMetricController {

    @Autowired
    @Deprecated
    private ReportMetricInvokeHandler reportMetricInvokeHandler;

    @Autowired
    private ReportMetricExecutor reportMetricExecutor;

    @Autowired
    private MetricDataManager metricDataManager;

    @Autowired
    private ReportMetricRegistry reportMetricRegistry;


    @ApiOperation(value = "报表指标查询", notes = "报表指标查询")
    @GetMapping(value = "/queryReportMetricData")
    public Result<Map<String, ReportMetricResult>> queryReportMetricList(@ApiParam(value = "报表指标编码,多个用;分割") @RequestParam String codes,
                                                                         @ApiParam(value = "时间参数1(必填,只传入当前参数就是精确查询,区间查询为开始时间) 传入时间格式为yyyy-MM-dd HH:mm:ss")
                                                                         @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") @RequestParam Date date1,
                                                                         @ApiParam(value = "时间参数2(选填,区间查询为结束时间) 传入时间格式为yyyy-MM-dd HH:mm:ss")
                                                                         @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) Date date2
    ) {
        Map<String, ReportMetricResult> valueByTimeInterval = reportMetricExecutor.getValueByTimeIntervalCache(codes, date1, date2);
        return Result.OK(valueByTimeInterval);
    }


    @ApiOperation(value = "报表指标All列表", notes = "报表指标All列表")
    @GetMapping(value = "/getAllReportMetricConfig")
    public Result<Collection<ReportMetricConfig>> getAllReportMetricConfig() {
        return Result.OK(metricDataManager.getAll());
    }

    @ApiOperation(value = "报表指标分页", notes = "报表指标分页")
    @GetMapping(value = "/queryReportMetricPage")
    public Result<Page<ReportMetricConfig>> queryReportMetricPage(@ApiParam(value = "报表指标编码") @RequestParam(required = false) String code,
                                                                  @ApiParam(value = "报表指标名称") @RequestParam(required = false) String name,
                                                                  @ApiParam(value = "报表名称") @RequestParam(required = false) String reportName,
                                                                  @ApiParam(value = "车间名称") @RequestParam(required = false) String workShop,
                                                                  @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                                  @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
        Collection<ReportMetricConfig> all = metricDataManager.getAll();
        Stream<ReportMetricConfig> stream = all.stream();
        if (StrUtil.isNotEmpty(code) && code.trim().length() > 0) {
            stream = stream.filter(reportMetricConfig -> reportMetricConfig.getCode().contains(code.trim()));
        }
        if (StrUtil.isNotEmpty(name) && name.trim().length() > 0) {
            stream = stream.filter(reportMetricConfig -> reportMetricConfig.getName().contains(name.trim()));
        }
        if (StrUtil.isNotEmpty(reportName) && reportName.trim().length() > 0) {
            stream = stream.filter(reportMetricConfig -> reportMetricConfig.getSourceReport().contains(reportName.trim()));
        }
        if (StrUtil.isNotEmpty(workShop) && workShop.trim().length() > 0) {
            stream = stream.filter(reportMetricConfig -> reportMetricConfig.getWorkShop().contains(workShop.trim()));
        }
        List<ReportMetricConfig> allData = stream.collect(Collectors.toList());
        List<ReportMetricConfig> collect = allData.stream().skip((pageNo - 1) * pageSize).limit(pageSize).collect(Collectors.toList());
        Page<ReportMetricConfig> reportMetricConfigPage = new Page<>();
        reportMetricConfigPage.setRecords(collect);
        reportMetricConfigPage.setTotal(allData.size());
        reportMetricConfigPage.setCurrent(pageNo);
        reportMetricConfigPage.setSize(pageSize);
        return Result.OK(reportMetricConfigPage);
    }


    @ApiOperation(value = "获取所有报表名称", notes = "获取所有报表名称")
    @GetMapping(value = "/getAllReportName")
    public Result<Collection<Map<String, Object>>> getAllReportName() {
        List<Map<String, Object>> reportNames = metricDataManager
                .getCheckSameNameProvider()
                .keySet()
                .stream()
                .distinct()
                .filter(StrUtil::isNotEmpty)
                .map(name -> Maps.asMap(Maps.put("name", name)))
                .collect(Collectors.toList());
        ;
        return Result.OK(reportNames);
    }

    @ApiOperation(value = "获取所有车间名称", notes = "获取所有车间名称")
    @GetMapping(value = "/getAllWorkShopName")
    public Result<Collection<Map<String, Object>>> getAllWorkShopName() {
        List<Map<String, Object>> workShopNames = metricDataManager
                .getAll()
                .stream()
                .map(ReportMetricConfig::getWorkShop)
                .distinct()
                .filter(StrUtil::isNotEmpty)
                .map(name -> Maps.asMap(Maps.put("name", name)))
                .collect(Collectors.toList());
        return Result.OK(workShopNames);
    }


    @ApiOperation(value = "重新加载刷新报表指标配置数据", notes = "重新加载刷新报表指标配置数据")
    @GetMapping(value = "/refreshReportMetric")
    public Result<Collection<ReportMetricConfig>> refreshReportMetric() {
        reportMetricRegistry.reScan();
        return Result.OK(metricDataManager.getAll());
    }

    @ApiOperation(value = "报表指标查询过程解析", notes = "报表指标查询过程解析")
    @GetMapping(value = "/previewReportMetricQuery")
    public Result<Map<String, Object>> previewReportMetricQuery(@ApiParam(value = "报表指标编码,多个用;分割") @RequestParam String codes,
                                                                @ApiParam(value = "时间参数1(必填,只传入当前参数就是精确查询,区间查询为开始时间) 传入时间格式为yyyy-MM-dd HH:mm:ss")
                                                                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") @RequestParam Date date1,
                                                                @ApiParam(value = "时间参数2(选填,区间查询为结束时间) 传入时间格式为yyyy-MM-dd HH:mm:ss")
                                                                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) Date date2
    ) {
        Map<String, Object> previewReportMetricQuery = reportMetricExecutor.previewReportMetricQuery(codes, date1, date2);
        return Result.OK(previewReportMetricQuery);
    }


    @ApiOperation(value = "Sql类型报表指标根据指标名称查询同表名称的指标", notes = "Sql类型报表指标根据指标名称查询同表名称的指标")
    @GetMapping(value = "/querySameTableMetricsByName")
    public Result<Map<String, Object>> querySameTableMetricsByName(@ApiParam(value = "报表指标编码,多个用;分割") @RequestParam String code) {
        Map<String, Object> previewReportMetricQuery = reportMetricExecutor.querySameTableMetricsByName(code);
        return Result.OK(previewReportMetricQuery);
    }

    @ApiOperation(value = "Sql类型报表指标根据表命模糊查询同类型指标", notes = "Sql类型报表指标根据表命模糊查询同类型指标")
    @GetMapping(value = "/fuzzyQuerySameTableMetricsByTableName")
    public Result<List<Map<String, Object>>> fuzzyQuerySameTableMetricsByTableName(@ApiParam(value = "表名称") @RequestParam String tableName) {
        List<Map<String, Object>> fuzzyQuerySameTableMetricsByTableName = reportMetricExecutor.fuzzyQuerySameTableMetricsByTableName(tableName);
        return Result.OK(fuzzyQuerySameTableMetricsByTableName);
    }
}
