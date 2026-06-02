
package io.github.forget_the_bright.rmetric.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 报表指标提供者注解
 *
 * <p>标注在类上,声明该类为报表指标数据提供者,自动注册为Spring Bean</p>
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/4/24 09:52
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ReportMetric {
    /**
     * 报表名称
     * @return 报表名称
     */
    String reportName();

    /**
     * 车间名称
     * @return 车间名称
     */
    String workShop();

    /**
     * 指标数据库的数据源,ReportMetricOperation 中的数据源填写后此处填写的就不生效了
     *
     * @return 数据源，默认为空字符串,使用默认数据源
     */
    String dataSource() default "";
}