
package io.github.forget_the_bright.rmetric.annotation;

import io.github.forget_the_bright.rmetric.enums.ReportMetricChartType;
import io.github.forget_the_bright.rmetric.enums.ReportMetricGranularity;
import io.github.forget_the_bright.rmetric.enums.ReportMetricSourceType;
import io.github.forget_the_bright.rmetric.enums.ReportMetricValueType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 报表指标操作注解
 *
 * <p>用于标记报表指标查询方法,自动注册指标配置</p>
 *
 * @author wanghao(helloworlwh @ 163.com)
 * @since 2026/4/24 09:15
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReportMetricOperation {
    /**
     * 指标编码(唯一标识)
     * @return 指标编码
     */
    String code();

    /**
     * 指标名称
     * @return 指标名称
     */
    String name();

    /**
     * 车间名称
     * @return 车间名称
     */
    String workShop() default "";

    /**
     * 值类型
     * @return 值类型
     */
    ReportMetricValueType valueType();

    /**
     * 值来源类型
     * @return 值来源类型
     */
    ReportMetricSourceType valueSource() default ReportMetricSourceType.REPORT;

    /**
     * 指标单位(可选)
     * @return 指标单位
     */
    String unit() default "";

    /**
     * 时间粒度(天、小时等)
     * @return 时间粒度
     */
    ReportMetricGranularity granularity();

    /**
     * 图表类型
     * @return 图表类型
     */
    ReportMetricChartType chartType() default ReportMetricChartType.SCATTER;

    /**
     * 时间格式化模板
     *
     * @return 默认yyyy-MM-dd
     */
    String timeFormat() default "yyyy-MM-dd";

    /**
     * 指标数据库的数据源
     *
     * @return 数据源，默认为空字符串,使用默认数据源
     */
    String dataSource() default "";

    /**
     * 指定数据库表名,填写后不走方法逻辑,由框架根据配置自动生成查询语句。
     *
     * @return 数据库表名，默认为空字符串
     */
    String dbTableName() default "";

    /**
     * 指定数据库表中存储值的列名，dbTableName填写后这里校验不可为空。
     *
     * @return 值列名，默认为空字符串
     */
    String dbTableValueColumn() default "";

    /**
     * 指定数据库表中存储时间的列名。
     *
     * @return 时间列名，默认为空字符串，dbTableName填写后这里校验不可为空
     */
    String dbTableTimeColumn() default "";

    /**
     * 查询时间展示列名称，如果填写这里的时间属性则取这个，此属性不参与时间过滤
     *
     * @return 查询时间展示列名称，默认为空字符串
     */
    String dbTableTimeAsColumn() default "";

    /**
     * 指定默认的数据库过滤条件字段，
     *
     * @return 默认过滤条件，默认为空字符串
     */
    String dbFilterColumn() default "";

    /**
     * 指定默认的数据库过滤条件字段，dbDbFilterColumn填写后这里校验不可为空。
     *
     * @return 默认过滤条件值，默认为空字符串
     */
    String dbFilterColumnValue() default "";

    /**
     * 指标的排序属性,查询指标列表时候会按照此属性排序 从小到大
     *
     * @return 默认的排序属性，默认为0
     */
    long sort() default 0;

    /**
     * 缓存时间，单位秒
     *
     * @return 默认的指标缓存时间，默认为60秒
     */
    long cacheTime() default 60;
}