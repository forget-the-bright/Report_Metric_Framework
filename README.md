# Report Metric Framework - 通用多数据源报表指标框架

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.13-brightgreen.svg)](https://spring.io/projects/spring-boot)

## 📖 项目简介

**Report Metric Framework** 是一个基于 Spring Boot 的通用多数据源报表指标查询框架。采用**注解驱动**模式，通过自动扫描注册机制统一管理分散的指标查询逻辑。框架内置**高性能并发查询**、**SQL智能合并**、**多级缓存**及**细粒度锁控制**，适用于工业时序数据、业务报表等场景。

### ✨ 核心特性

- 🚀 **注解驱动**：零配置，仅需 `@ReportMetric` 和 `@ReportMetricOperation` 即可定义指标
- 🔌 **多数据源支持**：基于 Dynamic-Datasource，支持动态切换数据源
- ⚡ **SQL智能合并**：同表查询自动合并为一条 SQL，减少数据库 IO
- 💾 **多级缓存**：支持 Redis 和本地缓存，可自定义过期时间
- 🔒 **并发安全**：内置细粒度锁控制，线程安全
- 📊 **图表支持**：内置散点图、柱状图等图表类型标识
- 🔄 **热刷新**：支持运行时重新扫描指标配置
- 🎯 **启动校验**：编码唯一性、参数类型等在启动期强校验

---

## 🛠️ 技术栈

- **基础框架**: Spring Boot 2.7.13+
- **多数据源**: dynamic-datasource-spring-boot-starter 4.1.3+
- **缓存**: Spring Data Redis + Caffeine
- **工具库**: Hutool 5.8.25+, Lombok 1.18.30+
- **JSON处理**: Fastjson 1.2.59+
- **数据库**: MySQL 8.0+（支持其他关系型数据库）
- **监控**: Micrometer + Actuator

---

## 📦 快速集成

### 1. 添加 Maven 依赖

在你的 Spring Boot 项目中添加以下依赖：
```xml
<dependency> 
    <groupId>io.github.forget-the-bright</groupId> 
    <artifactId>Report_Metric_Framework</artifactId> 
    <version>0.0.1</version> 
</dependency>
```
### 2. 配置数据源

在 `application.yml` 中配置多数据源：

```
yaml
spring:
  datasource:
    dynamic:
      # 默认数据源
      primary: master
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/db_master?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
          username: root
          password: your_password
          driver-class-name: com.mysql.cj.jdbc.Driver
        slave:
          url: jdbc:mysql://localhost:3306/db_slave?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
          username: root
          password: your_password
          driver-class-name: com.mysql.cj.jdbc.Driver
  
  # Redis 配置（可选，用于分布式缓存）
  redis:
    host: localhost
    port: 6379
    database: 0
    password: ''

# 日志配置（开发环境建议开启 DEBUG）
logging:
  level:
    io.github.forget_the_bright.rmetric: DEBUG
    com.baomidou.dynamic.datasource: DEBUG
```
### 3. 启用自动配置

框架通过 `spring.factories` 自动装配，无需额外配置。确保你的启动类能扫描到指标提供者类所在的包。

---
## 🚀 快速开始

### 第一步：创建指标提供者类

使用 `@ReportMetric` 标注类，声明为指标提供者：

```
java
package com.example.report.metric;

import io.github.forget_the_bright.rmetric.annotation.ReportMetric;
import io.github.forget_the_bright.rmetric.annotation.ReportMetricOperation;
import io.github.forget_the_bright.rmetric.enums.ReportMetricGranularity;
import io.github.forget_the_bright.rmetric.enums.ReportMetricValueType;
import io.github.forget_the_bright.rmetric.model.ReportMetricData;

import java.util.List;

/**
 * 示例：残极机组作业指标
 */
@ReportMetric(
    reportName = "残极洗涤破碎机组作业记录表",
    workShop = "电解车间"
)
public class ResidualPoleOpMetric {

    // 在此处定义指标方法...
}
```
### 第二步：定义指标方法

#### 方式 A：SQL 驱动模式（推荐 ✅）

框架自动生成并执行 SQL，性能最优：

```
java
@ReportMetricOperation(
    code = "D001",
    name = "1#残极洗涤破碎机组总加工数",
    valueType = ReportMetricValueType.SUM,
    unit = "块",
    granularity = ReportMetricGranularity.DAY,
    timeFormat = "yyyy-MM-dd",
    dbTableName = "mes_residual_pole_op_record",
    dbTableTimeColumn = "op_date",
    dbTableValueColumn = "total_processing_number",
    dbFilterColumn = "machine_number",
    dbFilterColumnValue = "1#",
    sort = 1,
    cacheTime = 120
)
public List<ReportMetricData> getOneTotalProcessingNumber(String startTime, String endTime) {
    // ⚠️ 此方法不会被执行！框架会根据注解自动生成 SQL
    throw new UnsupportedOperationException("该指标由框架自动构建SQL查询");
}
```
#### 方式 B：自定义方法模式

适用于复杂业务逻辑：

```
java
@ReportMetricOperation(
    code = "D002",
    name = "1#残极机组合格率",
    valueType = ReportMetricValueType.PV,
    unit = "%",
    granularity = ReportMetricGranularity.DAY,
    timeFormat = "yyyy-MM-dd",
    sort = 2
)
public List<ReportMetricData> getQualificationRate(String startTime, String endTime) {
    // 你的业务查询逻辑
    List<Map<String, Object>> dataList = yourService.queryQualificationRate(startTime, endTime);
    
    // 转换为标准格式
    return ReportMetricData.convertMaps(dataList, "date", "value");
}
```
### 第三步：调用接口查询

#### REST API 调用

```
bash
# 查询单个指标
GET http://localhost:8080/queryReportMetricData?codes=D001&date1=2026-04-01&date2=2026-04-30

# 批量查询多个指标（用分号分隔）
GET http://localhost:8080/queryReportMetricData?codes=D001;D002;D003&date1=2026-04-01
```
#### Java 代码调用

```
java
@Autowired
private ReportMetricExecutor reportMetricExecutor;

// 查询单个指标
Map<String, ReportMetricResult> result = reportMetricExecutor.getValueByTimeIntervalCache(
    "D001", startDate, endDate
);

// 批量查询
Map<String, ReportMetricResult> results = reportMetricExecutor.getValueByTimeIntervalCache(
    "D001;D002;D003", startDate, endDate
);
```
---

## 📋 详细配置说明

### @ReportMetric（类级别）

| 属性 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `reportName` | 是 | - | 报表业务名称，用于前端分组展示 |
| `workShop` | 是 | - | 车间名称，用于多维度筛选 |
| `dataSource` | 否 | "" | 指定数据源名称，为空则使用默认数据源 |

**示例：**
```
java
@ReportMetric(
    reportName = "电解槽温度记录表",
    workShop = "电解一车间",
    dataSource = "slave"  // 使用从库数据源
)
```
### @ReportMetricOperation（方法级别）

#### 基础属性

| 属性 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `code` | 是 | - | **全局唯一标识**，前端查询时的 Key |
| `name` | 是 | - | 指标显示名称 |
| `valueType` | 是 | - | 值类型：`PV`(瞬时值)、`SUM`(累计值) |
| `valueSource` | 否 | `REPORT` | 数据来源：`REPORT`(报表填报)、`SCADE`(数采) |
| `unit` | 否 | "" | 单位（如：吨、kWh、%） |
| `granularity` | 是 | - | 时间粒度：`DAY`、`HOUR`、`MINUTE`、`SECOND` |
| `timeFormat` | 否 | "yyyy-MM-dd" | 时间格式化模板 |
| `chartType` | 否 | `SCATTER` | 图表类型：`SCATTER`(散点图)、`BAR`(柱状图) |
| `sort` | 否 | 0 | 排序权重，从小到大排序 |
| `cacheTime` | 否 | 60 | 缓存时间（秒），0 表示使用默认值 |

#### SQL 驱动属性（填写任意一个即开启 SQL 模式）

| 属性 | 必填条件 | 说明 |
| :--- | :--- | :--- |
| `dbTableName` | 开启 SQL 模式时必填 | 数据库表名 |
| `dbTableValueColumn` | 开启 SQL 模式时必填 | 存储指标值的列名 |
| `dbTableTimeColumn` | 开启 SQL 模式时必填 | 存储时间的列名 |
| `dbTableTimeAsColumn` | 否 | 时间展示列名（不参与过滤） |
| `dbFilterColumn` | 与 `dbFilterColumnValue` 成对出现 | 过滤字段名（如：`machine_number`） |
| `dbFilterColumnValue` | 与 `dbFilterColumn` 成对出现 | 过滤字段值（如：`1#`） |

#### 多数据源属性

| 属性 | 优先级 | 说明 |
| :--- | :--- | :--- |
| `dataSource`（方法级） | **高** | 方法级别的数据源配置优先 |
| `dataSource`（类级别） | **低** | 类级别的作为默认值 |

**示例：**
```
java
@ReportMetric(
    reportName = "跨库查询示例",
    workShop = "测试车间",
    dataSource = "master"  // 类级别默认使用 master
)
public class CrossDataSourceMetric {
    
    @ReportMetricOperation(
        code = "M001",
        name = "使用默认数据源",
        // ... 其他属性
        dataSource = ""  // 空字符串，继承类级别的 master
    )
    public List<ReportMetricData> method1(String startTime, String endTime) {
        // ...
    }
    
    @ReportMetricOperation(
        code = "M002",
        name = "使用从库数据源",
        // ... 其他属性
        dataSource = "slave"  // 覆盖类级别配置，使用 slave
    )
    public List<ReportMetricData> method2(String startTime, String endTime) {
        // ...
    }
}
```
---
## 🔍 核心功能详解

### 1. SQL 智能合并

当多个指标使用**同一张表**且**相同过滤条件**时，框架会自动将它们合并为一条 `SELECT` 语句执行：

```
java
// 指标 D001
@ReportMetricOperation(
    code = "D001",
    dbTableName = "production_record",
    dbTableValueColumn = "output_value",
    dbFilterColumn = "line",
    dbFilterColumnValue = "A"
)

// 指标 D002
@ReportMetricOperation(
    code = "D002",
    dbTableName = "production_record",  // 同一张表
    dbTableValueColumn = "quality_value",
    dbFilterColumn = "line",
    dbFilterColumnValue = "A"  // 相同过滤条件
)
```
**生成的 SQL：**
```
sql
SELECT 
    op_date,
    output_value AS D001,
    quality_value AS D002
FROM production_record
WHERE line = 'A' 
  AND op_date BETWEEN ? AND ?
```
**优势：**
- ✅ 减少数据库 IO 次数
- ✅ 降低网络往返开销
- ✅ 提升报表加载速度 50%+

### 2. 多级缓存策略

框架提供三层缓存机制：

```

请求 → L1: 本地锁控制 → L2: Redis 缓存 → L3: 数据库查询
```
**缓存配置：**
```
java
@ReportMetricOperation(
    code = "D001",
    cacheTime = 300  // 自定义缓存 5 分钟
)
```
**缓存键格式：**
```

Base:ReportMetric:{code}_{startTimeMillis}_{endTimeMillis}
```
**缓存优先级：**
```

传入 expireSeconds > 注解 cacheTime > 默认值 60s
```
### 3. 并发锁控制

框架内部已处理并发锁，开发者无需额外加锁：

- **细粒度锁**：每个指标编码独立加锁
- **超时保护**：防止死锁
- **双重检查**：避免重复查询

### 4. 运行时刷新

支持在不重启应用的情况下重新加载指标配置：

```
bash
GET http://localhost:8080/refreshReportMetric
```
**适用场景：**
- 新增指标后动态生效
- 修改指标配置后即时更新

---

## 🌐 REST API 接口

框架提供完整的 RESTful API，所有接口返回统一格式：

```
json
{
  "success": true,
  "message": "",
  "code": 200,
  "result": { ... },
  "timestamp": 1717200000000
}
```
### 1. 查询指标数据

**接口：** `GET /queryReportMetricData`

**参数：**
- `codes`: 指标编码，多个用 `;` 分隔（必填）
- `date1`: 开始时间，格式 `yyyy-MM-dd HH:mm:ss`（必填）
- `date2`: 结束时间，格式 `yyyy-MM-dd HH:mm:ss`（选填）

**示例：**
```
bash
GET /queryReportMetricData?codes=D001;D002&date1=2026-04-01 00:00:00&date2=2026-04-30 23:59:59
```
**返回：**
```
json
{
  "success": true,
  "code": 200,
  "result": {
    "D001": {
      "code": "D001",
      "name": "1#残极洗涤破碎机组总加工数",
      "sourceReport": "残极洗涤破碎机组作业记录表",
      "workShop": "电解车间",
      "unit": "块",
      "granularity": "天",
      "chartType": "散点图",
      "status": "Success",
      "message": "查询成功",
      "data": [
        {"date": "2026-04-01", "value": "734"},
        {"date": "2026-04-02", "value": "456"}
      ]
    },
    "D002": { ... }
  }
}
```
### 2. 获取所有指标配置

**接口：** `GET /getAllReportMetricConfig`

**用途：** 获取所有注册的指标元数据

### 3. 分页查询指标

**接口：** `GET /queryReportMetricPage`

**参数：**
- `code`: 指标编码（模糊匹配，选填）
- `name`: 指标名称（模糊匹配，选填）
- `reportName`: 报表名称（模糊匹配，选填）
- `workShop`: 车间名称（模糊匹配，选填）
- `pageNo`: 页码，默认 1
- `pageSize`: 每页条数，默认 10

### 4. 获取报表名称列表

**接口：** `GET /getAllReportName`

**返回：** 所有不重复的报表名称

### 5. 获取车间名称列表

**接口：** `GET /getAllWorkShopName`

**返回：** 所有不重复的车间名称

### 6. 预览 SQL 查询

**接口：** `GET /previewReportMetricQuery`

**参数：** 同 `/queryReportMetricData`

**用途：** 查看框架生成的 SQL 语句，便于调试

**返回：**
```
json
{
  "D001": {
    "sql": "SELECT op_date, total_processing_number FROM mes_residual_pole_op_record WHERE machine_number = '1#' AND op_date BETWEEN ? AND ?",
    "dataSource": "master",
    "params": ["2026-04-01", "2026-04-30"]
  }
}
```
### 7. 查询同表指标

**接口：** `GET /querySameTableMetricsByName`

**参数：** `code` 指标编码

**用途：** 根据某个指标查询与其同表的所有指标

### 8. 模糊查询同表指标

**接口：** `GET /fuzzyQuerySameTableMetricsByTableName`

**参数：** `tableName` 表名（支持模糊匹配）

**用途：** 根据表名查找所有相关指标

---

## ⚠️ 关键约束与注意事项

### ✅ 1. 返回值与参数约束（启动期强校验）

- **返回类型**：必须是 `List<ReportMetricData>`
    - ❌ 错误：`List<Map<String, Object>>`
    - ✅ 正确：`List<ReportMetricData>`

- **参数个数**：必须为 **2个** `String` 类型参数
```java
public List<ReportMetricData> queryMethod(String startTime, String endTime)
 ```
- **启动校验**：如果不符合规范，**项目启动时会直接报错**并指出具体问题

### ✅ 2. 编码唯一性

- `code` 属性在全局必须唯一
- 如果重复，**启动时会抛出异常**并指出冲突的类名和方法名
- 建议命名规范：`模块前缀_序号`，如 `D001`、`M_ELEC_001`

### ✅ 3. SQL 驱动模式的联动校验

以下属性必须**成对出现**或**都不填**：

| 属性对 | 规则 |
| :--- | :--- |
| `dbTableName` + `dbTableTimeColumn` + `dbTableValueColumn` | 开启 SQL 模式时三者缺一不可 |
| `dbFilterColumn` + `dbFilterColumnValue` | 要么都填，要么都不填 |

**错误示例：**
```
java
@ReportMetricOperation(
code = "D001",
dbTableName = "test_table",
dbTableTimeColumn = "op_date"
// ❌ 缺少 dbTableValueColumn，启动报错
)
```
### ✅ 4. 数据源配置

- 如果同时配置了类级别和方法级别的 `dataSource`，**方法级别优先**
- 数据源名称必须与 `application.yml` 中配置的 `spring.datasource.dynamic.datasource` 下的 key 一致
- 如果指定的数据源不存在，运行时会抛出异常

### ✅ 5. 时间格式

- 框架会根据注解中的 `timeFormat` 自动将 `Date` 转换为字符串传入方法
- 默认格式：`yyyy-MM-dd`
- 如果需要时分秒，请显式指定：`timeFormat = "yyyy-MM-dd HH:mm:ss"`

---

## 🐛 常见错误与排查指南

### 1. 启动报错：`返回参数类型不正确`

**原因：** 方法返回值不是 `List<ReportMetricData>`

**解决：**
```
java
// ❌ 错误
public List<Map<String, Object>> queryMethod(...)

// ✅ 正确
public List<ReportMetricData> queryMethod(...) {
List<Map<String, Object>> dataList = ...;
return ReportMetricData.convertMaps(dataList, "date", "value");
}
```
### 2. 启动报错：`指标编码 xxx 出现重复`

**原因：** 不同的类或方法使用了相同的 `code`

**解决：** 查看日志中给出的冲突类名，修改 `code` 确保全局唯一

### 3. 启动报错：`填写dbTableName属性后,dbTableTimeColumn属性不可为空`

**原因：** 开启了 SQL 驱动模式，但未提供完整的时间/值列信息

**解决：** 补全 `dbTableTimeColumn` 和 `dbTableValueColumn`

### 4. 启动报错：`填写dbFilterColumn属性后,dbFilterColumnValue属性不可为空`

**原因：** 指定了过滤字段名，但未提供对应的过滤值

**解决：** 检查注解，确保 `dbFilterColumn` 和 `dbFilterColumnValue` 要么都不填，要么都填

### 5. 运行时结果：`Status: Error`

**原因：** 查询过程中发生了异常（如 SQL 语法错误、数据库连接超时）

**排查：** 查看后端日志，搜索 `指标 xxx 查询异常`，日志中会包含具体的错误堆栈

### 6. 运行时结果：`Status: Undefined`

**原因：** 前端请求的 `code` 在后端找不到对应的配置

**排查：** 
- 检查拼写是否正确
- 确认该类是否被 Spring 扫描到（是否加了 `@ReportMetric`）
- 确认包路径是否在组件扫描范围内

### 7. 数据源找不到

**错误信息：** `dynamic-datasource can not find primary datasource`

**解决：**
```
yaml
spring:
datasource:
dynamic:
primary: master  # 明确指定主数据源
datasource:
master:
# ... 配置
```
### 8. Redis 连接失败

**现象：** 缓存失效，但查询仍能正常进行

**解决：** 检查 Redis 配置是否正确，Redis 服务是否启动

---

## 💡 最佳实践

### 1. 优先使用 SQL 驱动模式

对于简单的单表统计，尽量使用 `dbTableName` 等属性：

```
java
// ✅ 推荐：让框架处理 SQL 合并，性能更优
@ReportMetricOperation(
code = "D001",
dbTableName = "production_record",
dbTableValueColumn = "output_value",
dbTableTimeColumn = "op_date"
)

// ❌ 不推荐：手动编写查询，无法享受 SQL 合并优化
@ReportMetricOperation(code = "D001")
public List<ReportMetricData> queryMethod(...) {
return mapper.selectList(...);
}
```
### 2. 复杂逻辑走 Method 模式

如果需要跨表联查或复杂的 Java 计算，请保持 `dbTableName` 为空：

```
java
@ReportMetricOperation(
code = "D099",
name = "综合效率指标"
// 不填写 dbTableName，走自定义方法
)
public List<ReportMetricData> calculateEfficiency(String startTime, String endTime) {
// 跨表查询 + 复杂计算
List<Data1> data1 = mapper1.query(startTime, endTime);
List<Data2> data2 = mapper2.query(startTime, endTime);

    // 业务逻辑处理
    return computeResult(data1, data2);
}
```
### 3. 合理设置缓存时间

根据数据更新频率设置缓存：

```
java
// 实时数据：短缓存或不缓存
@ReportMetricOperation(code = "D001", cacheTime = 30)  // 30秒

// 日报数据：长缓存
@ReportMetricOperation(code = "D002", cacheTime = 3600)  // 1小时

// 历史数据：超长缓存
@ReportMetricOperation(code = "D003", cacheTime = 86400)  // 24小时
```
### 4. 统一的编码规范

建议采用以下命名规范：

```

{模块前缀}_{业务类型}_{序号}

示例：
- D_ELEC_001: 电解模块-电量指标-001
- M_TEMP_001: 测量模块-温度指标-001
- R_QUAL_001: 报表模块-质量指标-001
```
### 5. 空值处理

数据库返回的 `null` 会被框架转为字符串 `"null"`，前端展示时需做判断：

```
javascript
// 前端处理
if (item.value === 'null' || item.value === null) {
return '-';
}
return item.value;
```
### 6. 利用预览接口调试

开发阶段多使用 `/previewReportMetricQuery` 接口查看生成的 SQL：

```
bash
GET /previewReportMetricQuery?codes=D001&date1=2026-04-01&date2=2026-04-30
```
### 7. 线程安全

框架内部已处理并发锁，**开发者无需在指标方法内额外加锁**：

```
java
// ❌ 不需要
@ReportMetricOperation(code = "D001")
public synchronized List<ReportMetricData> queryMethod(...) {
// ...
}

// ✅ 正确
@ReportMetricOperation(code = "D001")
public List<ReportMetricData> queryMethod(...) {
// ...
}
```
---
## 📊 返回结果结构

### 成功响应

```
json
{
"success": true,
"message": "",
"code": 200,
"result": {
"D001": {
"code": "D001",
"name": "1#残极洗涤破碎机组总加工数",
"sourceReport": "残极洗涤破碎机组作业记录表",
"workShop": "电解车间",
"valueType": "累计值",
"valueSource": "报表填报",
"unit": "块",
"granularity": "天",
"chartType": "散点图",
"status": "Success",
"message": "查询成功",
"data": [
{
"date": "2026-04-23",
"value": "734"
},
{
"date": "2026-04-24",
"value": "456"
}
]
}
},
"timestamp": 1717200000000
}
```
### 失败响应

```
json
{
"success": false,
"message": "指标 D999 未找到配置",
"code": 500,
"result": {
"D999": {
"code": "D999",
"status": "Undefined",
"message": "指标编码未注册",
"data": []
}
},
"timestamp": 1717200000000
}
```
---

## 🔧 高级配置

### 自定义缓存管理器

如果需要自定义缓存策略，可以注入 `MetricCacheManager`：

```
java
@Autowired
private MetricCacheManager cacheManager;

// 手动设置缓存
cacheManager.set(key, value, 300);  // 5分钟

// 批量设置
cacheManager.batchSet(resultMap, date1, date2, 600);  // 10分钟
```
### 监控指标

框架集成了 Micrometer，提供以下监控指标：

- `metric.cache.hit`: 缓存命中次数
- `metric.cache.miss`: 缓存未命中次数
- `metric.query.duration`: 查询耗时

可通过 Actuator 端点查看：
```
bash
GET /actuator/metrics/metric.cache.hit
```
### 日志配置

开发环境建议开启 DEBUG 日志：

```
yaml
logging:
level:
io.github.forget_the_bright.rmetric: DEBUG
com.baomidou.dynamic.datasource: DEBUG
```
生产环境建议调整为 INFO 或 WARN。

---
## 🤝 技术支持

- **作者**: wanghao
- **邮箱**: helloworlwh@163.com
- **GitHub**: [https://github.com/forget-the-bright/Report_Metric_Framework](https://github.com/forget-the-bright/Report_Metric_Framework)

---

## 📄 License

本项目采用 Apache License 2.0 开源协议。详见 [LICENSE](LICENSE) 文件。

---

## 📝 更新日志

### v2.0 (2026-06-02)
- ✨ 新增多数据源支持
- ✨ 新增 `workShop` 车间维度
- ✨ 新增 `valueType` 值类型标识
- ✨ 新增 `valueSource` 数据来源标识
- ✨ 新增 `dbTableTimeAsColumn` 时间展示列
- 🚀 优化 SQL 合并算法
- 🐛 修复并发查询问题
- 📝 完善 Javadoc 文档

### v1.0 (2026-04-24)
- 🎉 初始版本发布
- 基础注解驱动功能
- SQL 自动合并
- Redis 缓存支持
