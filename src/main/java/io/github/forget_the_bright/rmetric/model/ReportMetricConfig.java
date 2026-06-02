
package io.github.forget_the_bright.rmetric.model;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import io.github.forget_the_bright.rmetric.exception.ReportMetricException;
import io.github.forget_the_bright.rmetric.enums.ReportMetricChartType;
import io.github.forget_the_bright.rmetric.enums.ReportMetricGranularity;
import io.github.forget_the_bright.rmetric.enums.ReportMetricSourceType;
import io.github.forget_the_bright.rmetric.enums.ReportMetricValueType;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 报表指标配置实体
 *
 * <p>用于存储报表指标的元数据和查询方法信息</p>
 *
 * @author wanghao(helloworlwh @ 163.com)
 * @since 2026/4/24 09:15
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "ReportMetricConfig对象", description = "基础模块_报表指标配置")
public class ReportMetricConfig {
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
     * 车间名称
     */
    @ApiModelProperty(value = "车间名称")
    private String workShop;

    /**
     * 值类型
     */
    @ApiModelProperty(value = "值类型")
    private ReportMetricValueType valueType;

    /**
     * 值来源类型
     */
    @ApiModelProperty(value = "值来源类型")
    private ReportMetricSourceType valueSource;

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
     * 时间格式化模板(yyyy-MM-dd HH:mm:ss)
     */
    @ApiModelProperty(value = "时间格式化 yyyy-MM-dd HH:mm:ss")
    private String timeFormat;

    /**
     * 指标缓存时间
     */
    @ApiModelProperty(value = "指标缓存时间")
    @JsonIgnore
    private long cacheTime;

    /**
     * 查询类型(时间区间-双参数,具体时间-单参数)
     */
    //@ApiModelProperty(value = "查询类型 (时间区间(双参数),具体时间(单参数))")
    //private ReportMetricQueryType queryType;

    /**
     * 查询方法引用(不序列化到前端)
     */
    @ApiModelProperty(value = "查询方法引用")
    @JsonIgnore
    private Method queryMethod;

    @ApiModelProperty(value = "查询方法引用执行器")
    @JsonIgnore
    private MethodHandle queryMethodHandle;


    /**
     * 来源报表名称
     */
    @ApiModelProperty(value = "来源报表")
    private String sourceReport;

    /**
     * 来源报表类(不序列化到前端)
     */
    @ApiModelProperty(value = "来源报表类")
    @JsonIgnore
    private Class<?> sourceClass;

    /**
     * 来源报表实例(不序列化到前端)
     */
    @ApiModelProperty(value = "来源报表实例")
    @JsonIgnore
    private Object sourceInstance;

    /**
     * 指标所用的数据源名称,可空,默认使用默认数据源
     */
    @ApiModelProperty(value = "数据源名称")
    private String dataSource;

    /**
     * 指定数据库表名,填写后不走方法逻辑,由框架根据配置自动生成查询语句。
     *
     * @return 数据库表名，默认为空字符串
     */
    @ApiModelProperty(value = "指定数据库表名")
    private String dbTableName;

    /**
     * 指定数据库表中存储值的列名，dbTableName填写后这里校验不可为空。
     *
     * @return 值列名，默认为空字符串
     */
    @ApiModelProperty(value = "指定数据库表中存储值的列名")
    private String dbTableValueColumn;

    /**
     * 指定数据库表中存储时间的列名。
     *
     * @return 时间列名，默认为空字符串，dbTableName填写后这里校验不可为空
     */
    @ApiModelProperty(value = "指定数据库表中存储时间的列名")
    private String dbTableTimeColumn;

    /**
     * 指定数据库表中最后用来展示时间的列名,可为空，为空还是默认使用dbTableTimeColumn。
     *
     * @return 展示时间的列名，默认为空字符串，为空还是默认使用dbTableTimeColumn。
     */
    @ApiModelProperty(value = "指定数据库表中存储时间的列名")
    private String dbTableTimeAsColumn;

    /**
     * 指定默认的数据库过滤条件，dbTableName填写后这里校验不可为空。
     *
     * @return 默认过滤条件，默认为空字符串
     */
    @ApiModelProperty(value = "指定默认的数据库过滤条件字段")
    private String dbFilterColumn;

    @ApiModelProperty(value = "指定默认的数据库过滤条件值")
    private String dbFilterColumnValue;

    /**
     * 获取预览查询 SQL
     *
     * <p>根据配置的表名、列名和过滤条件构建用于预览的 SQL 查询语句。</p>
     *
     * @return 预览用的 SQL 字符串
     */
    @JsonProperty("privewQuerySql")
    private String getPrivewQuerySql() {
        if (StrUtil.isEmpty(dbTableName)) return "";
        List<String> selectColumns = new ArrayList<>();
        selectColumns.add(dbTableValueColumn);
        selectColumns.add(dbTableTimeColumn);
        selectColumns.add(dbTableTimeAsColumn);
        selectColumns.add(extractColumnExpression(dbFilterColumn));
        selectColumns = selectColumns.stream().distinct().filter(StrUtil::isNotEmpty).collect(Collectors.toList());

        List<String> tableFilter = new ArrayList<>();

        /* 若无过滤条件或部分指标无过滤条件，添加 (1=1) 占位 */
        if (dbFilterColumn.isEmpty()) {
            tableFilter.add("(1=1)");
        } else {
            tableFilter.add(StrUtil.format("( {} = '{}' )", dbFilterColumn, dbFilterColumnValue));
        }
        String sql = StrUtil.format("SELECT {} FROM {} WHERE ( {} ) AND {} BETWEEN ? AND ? ORDER BY {} ASC;",
                StrUtil.join(",", selectColumns),
                dbTableName,
                StrUtil.join(" OR ", tableFilter),
                dbTableTimeColumn,
                StrUtil.isEmpty(dbTableTimeAsColumn) ? dbTableTimeColumn : dbTableTimeAsColumn
        );

        sql = sql.replaceAll("--.*?\\n", " ")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
        return sql;
    }


    //region 数据库字段处理

    public ReportMetricConfig trimDbStr() {
        this.dataSource = trimStr(this.dataSource);
        this.dbTableName = trimStr(this.dbTableName);
        this.dbTableTimeColumn = trimStr(this.dbTableTimeColumn);
        this.dbTableTimeAsColumn = trimStr(this.dbTableTimeAsColumn);
        this.dbTableValueColumn = trimStr(this.dbTableValueColumn);
        this.dbFilterColumn = trimStr(this.dbFilterColumn);
        this.dbFilterColumnValue = trimStr(this.dbFilterColumnValue);
        return this;
    }

    private String trimStr(String str) {
        if (StrUtil.isEmpty(str)) return str;
        return str.trim();
    }

    public void checkColumnLength() {
        List<SFunction<ReportMetricConfig, String>> checkColumnMethods = Arrays.asList(
                ReportMetricConfig::getDbTableTimeColumn,
                ReportMetricConfig::getDbTableTimeAsColumn,
                ReportMetricConfig::getDbTableValueColumn
        );

        for (SFunction<ReportMetricConfig, String> method : checkColumnMethods) {
            String columnName = method.apply(this);
            String parseColumnName = parseColumnName(columnName);
            if (StrUtil.isEmpty(parseColumnName)) continue;
            String fieldName = getFieldName(method);
            ReportMetricException.throwByFlag(parseColumnName.length() >= 256,
                    "报表指标 CODE [{}] 中的 {} 属性 长度不得超过256个字符,超过请使用 AS 字段别名",
                    this.code, fieldName);
        }
    }

    public static <T> String getSqlFieldName(SFunction<T, ?> fieldFunction) {
        String methodName = LambdaUtils.extract(fieldFunction).getImplMethodName();
        String fieldName = PropertyNamer.methodToProperty(methodName);
        String sqlFieldName = StringUtils.camelToUnderline(fieldName);
        return sqlFieldName;
    }
    public static <T> String getFieldName(SFunction<T, ?> fieldFunction) {
        String methodName = LambdaUtils.extract(fieldFunction).getImplMethodName();
        String fieldName = PropertyNamer.methodToProperty(methodName);
        return fieldName;
    }
    //endregion

    //region 列名解析

    /**
     * SQL 列名解析正则表达式模式
     *
     * <p>AS_PATTERN: 匹配 SQL 列的别名定义，支持两种语法：</p>
     * <ul>
     *   <li>显式别名：column_name as alias （使用 AS 关键字）</li>
     *   <li>隐式别名：column_name alias （省略 AS 关键字）</li>
     * </ul>
     *
     * <p>FUNCTION_PATTERN: 匹配 SQL 函数表达式（包含括号的复杂表达式）</p>
     *
     * <p>这些模式用于 {@link #parseColumnName(String)} 方法中解析 SQL 列表达式。</p>
     */
    private static final Pattern AS_PATTERN =
            Pattern.compile("(?i)\\s+as\\s+([a-zA-Z_]\\w*)|\\s+([a-zA-Z_]\\w*)\\s*$");

    /**
     * 匹配 SQL 函数表达式（带括号的复杂表达式）
     * <p>FUNCTION_PATTERN: 匹配 SQL 函数表达式（包含括号的复杂表达式）</p>
     *
     * <p>用于识别 SQL 中的函数调用或复杂表达式，如 DATE_FORMAT()、CONCAT() 等。</p>
     * <p>当列表达式是函数时，在解析列名时应返回原表达式而非提取别名。</p>
     * <p>这些模式用于 {@link #parseColumnName(String)} 方法中解析 SQL 列表达式。</p>
     */
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\(.*\\)");


    /**
     * 解析 SQL 列名或别名
     *
     * <p>从复杂的 SQL 表达式中提取最终的列标识符，支持以下场景：</p>
     * <ul>
     *   <li><b>带别名的列</b>：如 "column_name as alias" 或 "column_name alias" → 返回 "alias"</li>
     *   <li><b>函数表达式</b>：如 "DATE_FORMAT(create_time, '%Y-%m-%d')" → 返回原表达式</li>
     *   <li><b>带表前缀的列</b>：如 "main.create_time" → 返回 "create_time"</li>
     *   <li><b>普通列名</b>：如 "create_time" → 返回 "create_time"</li>
     * </ul>
     *
     * <p>该方法主要用于构建查询结果映射时确定 Map 的键名。</p>
     *
     * @param columnSql SQL 列表达式，可以是普通列名、带表前缀的列名、函数表达式或带别名的列
     * @return 解析后的列名或别名；如果输入为空则返回 null
     */
    public String parseColumnName(String columnSql) {
        // 空值判断
        if (columnSql == null || columnSql.trim().isEmpty()) {
            return null;
        }
        String sql = columnSql.trim();

        // 1. 优先匹配别名，有别名直接返回别名
        Matcher asMatcher = AS_PATTERN.matcher(sql);
        if (asMatcher.find()) {
            return asMatcher.group(1) != null
                    ? asMatcher.group(1).trim()
                    : asMatcher.group(2).trim();
        }

        // 2. 没有别名，判断是否是函数（带括号）→ 直接返回原内容
        if (FUNCTION_PATTERN.matcher(sql).find()) {
            return sql;
        }

        // 3. 普通列名（如 main.date）→ 只取最后一段
        int lastDotIndex = sql.lastIndexOf('.');
        if (lastDotIndex != -1) {
            return sql.substring(lastDotIndex + 1);
        }

        // 4. 没有点的普通列名，直接返回
        return sql;
    }

    public String parseColumnName(Function<ReportMetricConfig, String> columnSqlMethod) {
        return parseColumnName(columnSqlMethod.apply(this));
    }

    /**
     * 提取 SQL 列表达式中的真实取值部分（去除别名）
     *
     * <p>从带别名的 SQL 表达式中提取 AS 前面的真实取值内容，支持以下场景：</p>
     * <ul>
     *   <li><b>带显式别名</b>：如 "column_name as alias" → 返回 "column_name"</li>
     *   <li><b>带隐式别名</b>：如 "column_name alias" → 返回 "column_name"</li>
     *   <li><b>函数带别名</b>：如 "DATE_FORMAT(create_time, '%Y-%m-%d') as date_str" → 返回 "DATE_FORMAT(create_time, '%Y-%m-%d')"</li>
     *   <li><b>无别名</b>：如 "create_time" → 返回 "create_time"</li>
     *   <li><b>复杂表达式</b>：如 "(a + b) / 2 as result" → 返回 "(a + b) / 2"</li>
     * </ul>
     *
     * <p>该方法用于获取 SQL 查询中实际取值的字段、函数或表达式，不包括别名部分。</p>
     *
     * @param columnSql SQL 列表达式，可以是普通列名、函数表达式或带别名的列
     * @return 真实的取值内容（字段名、函数表达式等）；如果输入为空则返回 null
     */
    public String extractColumnExpression(String columnSql) {
        if (columnSql == null || columnSql.trim().isEmpty()) {
            return null;
        }
        String sql = columnSql.trim();

        Matcher asMatcher = AS_PATTERN.matcher(sql);
        if (asMatcher.find()) {
            int aliasStartPos = asMatcher.start();
            return sql.substring(0, aliasStartPos).trim();
        }

        return sql;
    }

    /**
     * 通过方法引用提取 SQL 列表达式的真实取值部分
     *
     * @param columnSqlMethod 获取 SQL 列表达式的方法引用
     * @return 真实的取值内容
     */
    public String extractColumnExpression(Function<ReportMetricConfig, String> columnSqlMethod) {
        return extractColumnExpression(columnSqlMethod.apply(this));
    }
    //endregion
}