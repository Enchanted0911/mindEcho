---
description: "分页全链路规范：从 API 层到 Infra 层的完整分页实现约束，含 PageHelper 使用、各层分页对象定义、测试数据库配置与常见问题排查"
alwaysApply: false
globs: ["**/h2/**", "**/*Mapper.java", "**/*RepositoryImpl.java", "**/*DAOImpl.java", "**/*AppServiceImpl.java", "**/*GatewayServiceImpl.java", "**/*RpcServiceImpl.java"]
version: "2.0.0"
---

# 分页全链路规范

本规范覆盖分页查询从 API 层到 Infra
层的完整实现约束，与 [数据库设计规范](db_table_design.md)、[DAO 层设计规范](db_dao.md)、[MyBatis Mapper 规范](db_mybatis_mapper.md)
共同组成 DB 技术规范体系。

> **何时加载本规范**：凡涉及列表查询、分页接口的任务，必须加载本规范。

---

## 一、分页链路总览

```
API 层        BasePageResultDTO<XxxResponse>
               ↑ BasePageResultDTO.success(list, pageNum, pageSize, total)
UI 层(starter) PageResultBO<XxxBO>  →  转换为 List<XxxResponse>
               ↑
App 层         PageResultBO<XxxBO>
               ↑ PageResult<XxxDO> → PageResultBO<XxxBO>
Domain 层      PageResult<XxxDO>（Repository 接口返回）
               ↑ PageHelper.startPage() + PageInfo → PageResult（在 RepositoryImpl 中）
Infra 层       List<XxxEntity>（DAO 返回，PageHelper 拦截）
               ↑ QueryCondition（含 pageNum/pageSize，仅分页查询时添加）
```

**核心原则**：

- `PageHelper.startPage()` 只在 **RepositoryImpl**（有 Repository 层时）或 **DAOImpl**（无 Repository 层时）中调用，禁止在
  AppService、DomainService 中调用
- 禁止手动写 `LIMIT` 语句
- 禁止 DAO 层将 `PageInfo` 直接返回给上层

---

## 二、各层分页对象定义

### 2.1 PageResult（分页结果领域对象）

**有 Repository 层时**：定义在 `domain/model/PageResult.java`
**无 Repository 层时**：定义在 `infrastructure/commons/PageResult.java`

```java
public class PageResult<T> {

    private List<T> data;
    private Integer pageNum;
    private Integer pageSize;
    private Long total;
    private Integer totalPages;

    public PageResult() {}

    public PageResult(List<T> data, Integer pageNum, Integer pageSize, Long total) {
        this.data = data;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = pageSize != null && pageSize > 0
                ? (int) Math.ceil((double) total / pageSize) : 0;
    }

    public static <T> PageResult<T> buildEmptyResult(Integer pageNum, Integer pageSize) {
        return new PageResult<>(Collections.emptyList(), pageNum, pageSize, 0L);
    }

    // getter/setter 省略
}
```

### 2.2 PageResultBO（分页结果业务对象）

定义在 `application/model/PageResultBO.java`：

```java
public class PageResultBO<T> {

    private List<T> data;
    private Integer pageNum;
    private Integer pageSize;
    private Long total;
    private Integer totalPages;

    public PageResultBO() {}

    public PageResultBO(List<T> data, Integer pageNum, Integer pageSize, Long total) {
        this.data = data;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = calculateTotalPages(total, pageSize);
    }

    public static <T> PageResultBO<T> empty(Integer pageNum, Integer pageSize) {
        return new PageResultBO<>(Collections.emptyList(), pageNum, pageSize, 0L);
    }

    private Integer calculateTotalPages(Long total, Integer pageSize) {
        if (total == null || total <= 0 || pageSize == null || pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / pageSize);
    }

    // getter/setter 省略
}
```

---

## 三、各层实现规范

### 3.1 API 层

分页接口返回 `BasePageResultDTO<T>`，**禁止**使用 `BaseResultDTO<PageDTO<T>>` 或 `BaseResultDTO<List<T>>`。

```java
// ✅ 正确
BasePageResultDTO<AuditListResponse> getAuditList(AuditListRequest request);

// ❌ 禁止
BaseResultDTO<PageDTO<AuditListResponse>> getAuditList(AuditListRequest request);
BaseResultDTO<List<AuditListResponse>> getAuditList(AuditListRequest request);
```

### 3.2 UI 层（starter）

从 `PageResultBO` 转换为 `BasePageResultDTO`，使用 `BasePageResultDTO.success(list, pageNum, pageSize, total)`：

```java
@Override
public BasePageResultDTO<AuditListResponse> getAuditList(AuditListRequest request) {
    try {
        // 1. Request → QueryBO
        AuditQueryBO queryBO = AuditDTOConverter.request2QueryBO(request);

        // 2. 调用 AppService，返回 PageResultBO
        PageResultBO<AuditBO> pageResultBO = auditAppService.getAuditList(queryBO);

        // 3. BO List → Response List
        List<AuditListResponse> responseList = pageResultBO.getData().stream()
                .map(AuditDTOConverter::bo2Response)
                .collect(Collectors.toList());

        // 4. 构建 BasePageResultDTO 返回
        return BasePageResultDTO.success(
                responseList,
                pageResultBO.getPageNum(),
                pageResultBO.getPageSize(),
                pageResultBO.getTotal()
        );
    } catch (BaseRuntimeException e) {
        return BasePageResultDTO.error(e.getCode(), e.getMessage());
    } catch (Exception e) {
        return BasePageResultDTO.error(CommonErrorCodeEnum.SYSTEM_ERROR.getCode(), "system error,please retry");
    }
}
```

### 3.3 App 层

AppService 分页方法返回 `PageResultBO<XxxBO>`，负责将 `PageResult<XxxDO>` 转换为 `PageResultBO<XxxBO>`：

```java
@Override
public PageResultBO<AuditBO> getAuditList(AuditQueryBO queryBO) {
    log.info("查询审核列表, queryBO: {}", queryBO);

    // 1. 参数校验
    queryBO.doCheck();

    // 2. QueryBO → QueryDO
    AuditQueryDO queryDO = AuditBOConverter.queryBO2DO(queryBO);

    // 3. 调用 DomainService，返回 PageResult<XxxDO>
    PageResult<AuditInfoDO> pageResult = auditDomainService.getAuditList(queryDO);

    // 4. DO List → BO List
    List<AuditBO> auditBOList = pageResult.getData().stream()
            .map(AuditBOConverter::do2BO)
            .collect(Collectors.toList());

    // 5. 构建 PageResultBO 返回
    return new PageResultBO<>(auditBOList, pageResult.getPageNum(),
            pageResult.getPageSize(), pageResult.getTotal());
}
```

### 3.4 Domain 层

#### Repository 接口

分页查询方法名以 `pageBy` 开头，返回 `PageResult<XxxDO>`；非分页查询方法名以 `listBy`/`findBy`/`getBy` 开头，返回
`List<XxxDO>` 或单个对象。**通过方法名即可区分是否分页**：

```java
public interface AuditRepository {

    // 分页查询：方法名 pageByXxx，返回 PageResult
    PageResult<AuditInfoDO> pageByQuery(AuditQueryDO queryDO);

    // 非分页查询：方法名 listBy/findBy/getBy，返回 List 或单个对象
    List<AuditInfoDO> listByCourseId(Long courseId, String region);
    AuditInfoDO findById(Long id, String region);
}
```

#### RepositoryImpl（有 Repository 层时，PageHelper 在此调用）

```java
@Override
public PageResult<AuditInfoDO> getAuditList(AuditQueryDO queryDO) {
    // 1. 构建 DAO 查询条件
    AuditInfoDAO.QueryCondition condition = buildQueryCondition(queryDO);

    // 2. 启动分页拦截（在调用 DAO 之前）
    PageHelper.startPage(condition.getPageNum(), condition.getPageSize());

    // 3. 执行查询（PageHelper 拦截此次查询）
    List<AuditInfo> entityList = auditInfoDAO.selectByCondition(condition);

    // 4. PageInfo 包装（获取 total 等分页信息）
    PageInfo<AuditInfo> pageInfo = new PageInfo<>(entityList);

    // 5. Entity → DO，构建 PageResult 返回
    List<AuditInfoDO> doList = entityList.stream()
            .map(AuditInfoDO::fromEntity)
            .collect(Collectors.toList());

    PageResult<AuditInfoDO> pageResult = new PageResult<>();
    pageResult.setData(doList);
    pageResult.setTotal(pageInfo.getTotal());
    pageResult.setPageNum(pageInfo.getPageNum());
    pageResult.setPageSize(pageInfo.getPageSize());
    return pageResult;
}
```

**提前返回空结果**（避免不必要的分页查询）：

```java
// 前置条件不满足时，直接返回空结果，不调用 PageHelper
if (CollectionUtils.isEmpty(auditIdList)) {
    return PageResult.buildEmptyResult(queryDO.getPageNum(), queryDO.getPageSize());
}
```

### 3.5 Infra 层（无 Repository 层时）

**无 Repository 层时**，`PageHelper.startPage()` 在 DAOImpl 中调用，`PageResult` 定义在 `infrastructure/commons/`：

```java
@Override
public PageResult<AuditInfo> selectPageByCondition(QueryCondition condition) {
    // 1. 启动分页拦截
    PageHelper.startPage(condition.getPageNum(), condition.getPageSize());

    // 2. 执行查询
    List<AuditInfo> list = auditInfoMapper.selectByExample(buildExample(condition));

    // 3. PageInfo → PageResult
    PageInfo<AuditInfo> pageInfo = new PageInfo<>(list);
    PageResult<AuditInfo> pageResult = new PageResult<>();
    pageResult.setData(list);
    pageResult.setTotal(pageInfo.getTotal());
    pageResult.setPageNum(pageInfo.getPageNum());
    pageResult.setPageSize(pageInfo.getPageSize());
    return pageResult;
}
```

### 3.6 DAO 层 QueryCondition（分页查询时）

仅当该 DAO 查询方法需要支持分页时，`QueryCondition` 才添加 `pageNum`、`pageSize` 字段；不分页的查询不需要添加：

```java
@Data
class QueryCondition {
    private String region;
    private Integer status;
    // ... 其他业务查询字段

    // 仅分页查询时添加以下字段
    private Integer pageNum;
    private Integer pageSize;
}
```

---

## 四、禁止事项

| 禁止行为                                                            | 说明                                              |
|-----------------------------------------------------------------|-------------------------------------------------|
| ❌ 手动写 `LIMIT` 语句                                                | 统一使用 PageHelper 拦截                              |
| ❌ 在 AppService / DomainService 中调用 `PageHelper.startPage()`     | 只在 RepositoryImpl 或 DAOImpl 中调用                 |
| ❌ DAO 层返回 `PageInfo<T>` 给上层                                     | DAO 只返回 `List<T>`，PageInfo 在 RepositoryImpl 中处理 |
| ❌ 分页接口返回 `BaseResultDTO<PageDTO<T>>` 或 `BaseResultDTO<List<T>>` | 统一使用 `BasePageResultDTO<T>`                     |
| ❌ 非分页查询的 QueryCondition 添加 pageNum/pageSize                     | 按需添加，不强制                                        |

---

## 五、测试数据库配置规范

### H2数据库表结构同步

测试用的H2数据库表结构必须与正式MySQL表结构保持一致，确保测试的准确性。

#### 测试SQL文件位置

```
infrastructure/
└── src/
    └── test/
        └── resources/
            └── h2/
                └── V1_init.sql
```

#### H2表结构示例

```sql
-- H2数据库建表语句（与MySQL保持一致）
CREATE TABLE course_info
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    course_code VARCHAR(64)  NOT NULL,
    course_name VARCHAR(255) NOT NULL,
    status      INT          NOT NULL DEFAULT 1,
    region      VARCHAR(32)  NOT NULL,
    ctime       BIGINT       NOT NULL,
    utime       BIGINT       NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_code_region ON course_info (course_code, region);
CREATE INDEX idx_region_status ON course_info (region, status);
CREATE INDEX idx_ctime ON course_info (ctime);
```

### 测试配置要求

1. **表结构一致性**：所有字段、索引、约束都必须在测试环境中正确配置；字段类型、长度、默认值必须与生产环境一致
2. **数据类型映射**：MySQL 的 `bigint` → H2 的 `BIGINT`；`varchar` → `VARCHAR`；`int` → `INT`
3. **索引同步**：所有生产环境的索引都必须在测试环境中创建，索引名称和字段顺序保持一致

---

## 六、设计检查清单

### 分页链路检查

- [ ] API 层分页接口是否使用 `BasePageResultDTO<T>`？
- [ ] UI 层是否使用 `BasePageResultDTO.success(list, pageNum, pageSize, total)` 构建返回？
- [ ] App 层分页方法是否返回 `PageResultBO<XxxBO>`？
- [ ] `PageHelper.startPage()` 是否只在 RepositoryImpl（或无 Repository 时的 DAOImpl）中调用？
- [ ] DAO 层是否只返回 `List<T>`，不返回 `PageInfo<T>`？
- [ ] 是否避免了手动 LIMIT 语句？
- [ ] 前置条件不满足时是否提前返回 `PageResult.buildEmptyResult()`？
- [ ] 分页查询的 QueryCondition 是否包含 `pageNum`/`pageSize` 字段？

### 测试配置检查

- [ ] H2表结构是否与MySQL一致？
- [ ] 测试数据是否正确初始化？
- [ ] 索引和约束是否同步创建？

---

## 版本与变更

- 2.0.0 (2026-04-02): 重写为分页全链路规范，覆盖 API → UI → App → Domain → Infra 完整链路；明确 PageResult/PageResultBO
  定义位置；PageHelper 调用位置改为 RepositoryImpl（有 Repository 时）或 DAOImpl（无 Repository 时）；补充禁止事项表
- 1.0.0 (2025-02-06): 初始化版本与变更记录
