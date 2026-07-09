---
description: "数据库表设计、命名、索引与SQL文件管理规范"
alwaysApply: false
globs: ["**/*.sql", "**/sql/**"]
version: "1.0.0"
---

# 数据库设计规范

## 背景

数据库设计是系统的基础设施，直接影响系统的性能、可维护性和数据一致性。本规范定义表设计标准、命名规范、索引与唯一约束、SQL
文件管理及表结构示例。MyBatis 与 DAO
层规范见 [DAO 层设计](db_dao.md)、[MyBatis Mapper 规范](db_mybatis_mapper.md)、[分页与测试配置](db_pagination_and_test.md)。

## 表设计规范

### 命名规范

1. **表名规范**：
    - 表名必须使用小写字母，使用下划线分隔单词
    - 草稿相关表统一包含`draft`关键字
    - 审核相关表统一包含`audit`关键字
    - 示例：`course_info`、`course_draft`、`audit_info`

2. **字段名规范**：
    - 字段名必须使用小写字母，使用下划线分隔单词
    - 状态字段统一使用`status`
    - 类型字段统一使用`type`
    - 排序字段统一使用`sort_order`
    - 层级字段统一使用`level`
    - 百分比字段统一使用`percent`结尾，如`progress_percent`
    - 原因说明字段统一使用`reason`结尾，如`audit_reason`

### 字段设计规范

1. **主键设计**：
    - 所有表必须使用自增长的`bigint`类型主键`id`
    - 主键字段必须设置为`NOT NULL AUTO_INCREMENT`

2. **必要字段**：
    - **id**：主键，`bigint NOT NULL AUTO_INCREMENT`
    - **region**：地区标识，`varchar(32) NOT NULL`，用于数据隔离
    - **create_by**：创建人，`varchar(64) NOT NULL`
    - **update_by**：更新人，`varchar(64) NOT NULL`
    - **ctime**：创建时间，`bigint(20) NOT NULL`，存储时间戳
    - **utime**：更新时间，`bigint(20) NOT NULL`，存储时间戳
    - **is_valid**：数据有效性，`tinyint(4) NOT NULL DEFAULT '1'`，0-失效，1-有效

3. **时间字段规范**：
    - 涉及到时间相关的字段，例如`ctime`和`utime`，使用`bigint(20)`类型存储时间戳
    - 便于跨时区处理和高性能查询

4. **通用字段规范**：
    - 每个表都必须包含以下通用字段：
   ```sql
   `create_by` varchar(64) NOT NULL COMMENT '创建人',
   `update_by` varchar(64) NOT NULL COMMENT '更新人',
   `ctime` bigint(20) NOT NULL COMMENT '创建时间',
   `utime` bigint(20) NOT NULL COMMENT '更新时间',
   `is_valid` tinyint(4) NOT NULL DEFAULT '1' COMMENT '数据有效性：0-失效，1-有效'
   ```

5. **业务字段规范**：
    - 如果数据维度是用户，则使用`acct_id`进行存储
    - 如果数据维度是门店，则使用`shop_id`进行存储
    - 字段长度：`varchar`字段必须指定合理的长度，避免浪费存储空间

### 索引设计规范

1. **基础索引**：
    - 主键自动创建聚簇索引
    - 必须为`region`字段建立索引，通常与其他查询字段组成复合索引

2. **复合索引设计**：
    - 根据查询场景合理设计索引，避免全表扫描
    - 高频查询字段组合建立复合索引
    - 索引字段顺序按照查询频率和选择性排列

3. **Region索引规范**：
    - 所有包含`region`字段的查询都必须使用包含`region`的索引
    - 复合索引中`region`字段通常放在第一位或根据查询模式优化位置

### 唯一约束规范

1. **Region唯一约束**：
    - 原有唯一约束需要加入`region`字段，确保同一region内的唯一性
    - 示例：`uk_code`改为`uk_code_region(code, region)`

2. **业务唯一约束**：
    - 根据业务需求设计合理的唯一约束
    - 考虑region数据隔离的影响

### SQL文件管理规范

1. **正式SQL文件**：
    - 所有正式的SQL语句生成在根目录的`sql.sql`文件中
    - 包含建表语句、索引创建、数据初始化等

2. **测试SQL文件**：
    - 测试用的SQL语句生成在`infrastructure`模块的`src/test/resources/h2/V1_init.sql`文件中
    - 与正式环境保持结构一致

### 表结构示例

```sql
-- 课程信息表
CREATE TABLE `course_info`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_code` varchar(64)  NOT NULL COMMENT '课程编码',
    `course_name` varchar(255) NOT NULL COMMENT '课程名称',
    `status`      int          NOT NULL DEFAULT '1' COMMENT '课程状态：1-草稿，2-待审核，3-已上线',
    `region`      varchar(32)  NOT NULL COMMENT '地区',
    `ctime`       bigint       NOT NULL COMMENT '创建时间',
    `utime`       bigint       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_region` (`course_code`, `region`),
    KEY           `idx_region_status` (`region`, `status`),
    KEY           `idx_ctime` (`ctime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程信息表';

-- 审核信息表
CREATE TABLE `audit_info`
(
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id`    bigint      NOT NULL COMMENT '课程ID',
    `audit_status` int         NOT NULL DEFAULT '1' COMMENT '审核状态：1-待审核，2-审核通过，3-审核拒绝',
    `audit_reason` varchar(500)         DEFAULT NULL COMMENT '审核原因',
    `region`       varchar(32) NOT NULL COMMENT '地区',
    `ctime`        bigint      NOT NULL COMMENT '创建时间',
    `utime`        bigint      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_region` (`course_id`, `region`),
    KEY            `idx_region_status` (`region`, `audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核信息表';
```

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
