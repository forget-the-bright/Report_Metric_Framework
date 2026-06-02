package io.github.forget_the_bright.controller;

import io.github.forget_the_bright.rmetric.ReportMetricExecutor;
import io.github.forget_the_bright.rmetric.common.MetricDataManager;
import io.github.forget_the_bright.rmetric.controller.ReportMetricController;
import io.github.forget_the_bright.rmetric.model.ReportMetricConfig;
import io.github.forget_the_bright.rmetric.model.ReportMetricResult;
import io.github.forget_the_bright.rmetric.model.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * 测试控制器
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/6/1
 */
@Slf4j
@RestController
@RequestMapping("/test")
@Api(tags = "测试接口")
public class TestController extends ReportMetricController {

}
