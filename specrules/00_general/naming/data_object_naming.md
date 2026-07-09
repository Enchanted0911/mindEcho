---
description: "数据对象命名规范"
alwaysApply: false
globs: ["**/*.java"]
version: "1.1.0"
---

# 数据对象命名规范

## 按层级命名数据对象

数据对象必须按照所在层级使用对应的后缀命名：

| 后缀         | 全称                   | 所属层级    | 用途             | 示例                                                   |
|------------|----------------------|---------|----------------|------------------------------------------------------|
| **DTO**    | Data Transfer Object | API层    | 数据传输对象，用于API接口 | `CreateTaskRequest`, `TaskDetailResponse`, `TaskDTO` |
| **VO**     | View Object          | 用户接口层输出 | 视图对象，用于前端展示    | `TaskVO`, `DashboardVO`                              |
| **BO**     | Business Object      | 应用层     | 业务对象，用于应用层业务处理 | `TaskBO`, `RuleBO`, `MonthlySettleBO`                |
| **DO**     | Domain Object        | 领域层     | 领域对象，包含业务逻辑和行为 | `TaskDO`, `RuleDO`, `MerchantRecordDO`               |
| **Entity** | Database Entity      | 基础设施层   | 数据库实体，与表结构对应   | `TaskEntity`, `RuleEntity`                           |

## 各层级详细说明

### DTO (Data Transfer Object) - API层

**用途**: 在 API 边界进行数据传输。

**位置**: `api/request/`, `api/response/`, `api/dto/`

**命名规范**:

- Request: 以 `Request` 结尾（如: `CreateTaskRequest`, `UpdateRuleRequest`）
- Response: 以 `Response` 结尾（如: `TaskDetailResponse`, `ListTaskResponse`）
- 通用DTO: 以 `DTO` 结尾（如: `TaskDTO`, `RuleDTO`）

**示例**:

```java
// Request DTO
public class CreateTaskRequest {
    private String taskCode;
    private String taskName;
    private String region;
}

// Response DTO
public class TaskDetailResponse {
    private String taskCode;
    private String taskName;
    private Integer status;
    private String statusDesc;
}

// 通用 DTO
public class TaskDTO {
    private Long id;
    private String taskCode;
    private String taskName;
}
```

### VO (View Object) - 用户接口层输出

**用途**: 为前端提供视图数据，可包含格式化、聚合等展示逻辑。

**位置**: `starter/vo/`

**命名规范**: 以 `VO` 结尾（如: `TaskVO`, `DashboardVO`）

**示例**:

```java
public class TaskVO {
    private String taskCode;
    private String taskName;
    private String statusDesc;        // 格式化后的状态描述
    private String createTimeStr;     // 格式化后的创建时间
    private Integer ruleCount;        // 聚合的规则数量
}
```

### BO (Business Object) - 应用层

**用途**: 封装应用层的业务数据，用于应用服务之间的数据传递。

**位置**: `application/bo/`

**命名规范**:

- 基础业务对象: 以 `BO` 结尾（如: `TaskBO`, `RuleBO`）
- 查询参数: 以 `QueryBO` 结尾（如: `TaskQueryBO`, `RuleQueryBO`）
- 操作结果: 以 `ResultBO` 结尾（如: `MonthlySettleResultBO`）

**示例**:

```java
// 基础业务对象
public class TaskBO {
    private String taskCode;
    private String taskName;
    private String region;
    
    /**
     * 参数校验
     */
    public void doCheck() {
        if (!org.apache.commons.lang3.StringUtils.isNotBlank(taskCode)) {
            throw new IllegalArgumentException("任务编码不能为空");
        }
        if (!org.apache.commons.lang3.StringUtils.isNotBlank(taskName)) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (!org.apache.commons.lang3.StringUtils.isNotBlank(region)) {
            throw new IllegalArgumentException("地区不能为空");
        }
    }
}

// 查询参数
public class TaskQueryBO {
    private String taskCode;
    private String region;
    private Integer status;
    private Integer pageNum;
    private Integer pageSize;
}

// 操作结果
public class MonthlySettleResultBO {
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
}
```

### DO (Domain Object) - 领域层

**用途**: 领域对象，包含业务逻辑和行为方法。

**位置**: `domain/model/`

**命名规范**:

- 基础领域对象: 以 `DO` 结尾（如: `TaskDO`, `RuleDO`）
- 查询参数: 以 `QueryDO` 结尾（如: `TaskQueryDO`）
- 聚合对象: 以 `AggDO` 结尾（如: `TaskRuleAggDO`）

**关键要求**:

- 所有核心业务 DO 必须包含 `region` 字段
- 应包含业务行为方法
- 应提供 `toEntity()` 和 `fromEntity()` 转换方法

**示例**:

```java
// 基础领域对象
public class TaskDO {
    private Long id;
    private String taskCode;
    private String taskName;
    private String region;  // 必须包含
    private Integer status;
    
    /**
     * 业务行为：发布任务
     */
    public void publish() {
        if (TaskStatusEnum.PUBLISHED.value().equals(this.status)) {
            throw new RuntimeException("任务已发布");
        }
        this.status = TaskStatusEnum.PUBLISHED.value();
    }
    
    /**
     * 转换为Entity
     */
    public TaskEntity toEntity() {
        TaskEntity entity = new TaskEntity();
        entity.setId(this.id);
        entity.setTaskCode(this.taskCode);
        entity.setTaskName(this.taskName);
        entity.setRegion(this.region);
        entity.setStatus(this.status);
        return entity;
    }
    
    /**
     * 从Entity转换
     */
    public static TaskDO fromEntity(TaskEntity entity) {
        if (entity == null) {
            return null;
        }
        TaskDO taskDO = new TaskDO();
        taskDO.setId(entity.getId());
        taskDO.setTaskCode(entity.getTaskCode());
        taskDO.setTaskName(entity.getTaskName());
        taskDO.setRegion(entity.getRegion());
        taskDO.setStatus(entity.getStatus());
        return taskDO;
    }
}

// 查询参数
public class TaskQueryDO {
    private String taskCode;
    private String region;
    private Integer status;
}

// 聚合对象
public class TaskRuleAggDO {
    private TaskDO taskDO;
    private List<RuleDO> ruleList;
}
```

### Entity (Database Entity) - 基础设施层

**用途**: 数据库实体，与表结构一一对应。

**位置**: `infrastructure/dal/entity/`

**命名规范**: 以 `Entity` 结尾（如: `TaskEntity`, `RuleEntity`）

**关键要求**: 所有 Entity 必须包含通用字段

**示例**:

```java
public class TaskEntity {
    // 通用字段（必须）
    private Long id;
    private String region;
    private String createBy;
    private String updateBy;
    private Long ctime;
    private Long utime;
    private Integer isValid;
    
    // 业务字段
    private String taskCode;
    private String taskName;
    private Integer status;
}
```

## 参数对象命名规范

### 查询参数对象

| 层级    | 后缀               | 示例                       | 说明            |
|-------|------------------|--------------------------|---------------|
| 应用层   | `QueryBO`        | `TaskQueryBO`            | 应用层查询参数       |
| 领域层   | `QueryDO`        | `TaskQueryDO`            | 领域层查询参数       |
| 基础设施层 | `QueryCondition` | `TaskDAO.QueryCondition` | DAO内部类，封装查询条件 |

### 操作参数对象

| 后缀          | 使用场景 | 示例                                       | 说明            |
|-------------|------|------------------------------------------|---------------|
| `Param`     | 操作参数 | `TaskUpdateParam`, `RuleValidateParam`   | 封装业务操作的参数组合   |
| `SyncParam` | 同步参数 | `TaskIndexSyncParam`, `BenefitSyncParam` | 用于MQ消息传递和数据同步 |

### 禁用命名

禁止使用以下模糊的命名：

- ❌ `Identifier`, `Criteria`, `Condition` (作为外部参数对象)
- ❌ `Request`, `Response` (用于内部参数，这些专用于API层)

## 数据转换规则

### 转换方向

```
用户 → API层(DTO) → 用户接口层(DTO→BO) → 应用层(BO→DO) → 领域层(DO) → 基础设施层(DO→Entity) → 数据库
```

### 转换职责与规范

转换的**分层职责、Converter 位置与命名、简单转换封装在 BO/DO、复杂转换使用静态 Converter、禁止 @Component/注入、禁止调用服务实现类转换方法
**等，以 **[对象转换规范](specrules/03_coding/object_conversion_standards.md)** 为准。

| 转换                           | 职责层            | 简单转换                             | 复杂转换                                                                       |
|------------------------------|----------------|----------------------------------|----------------------------------------------------------------------------|
| DTO/VO ↔ BO                  | 用户接口层（starter） | —                                | 一律使用 Converter（`starter/.../converter/`），如 `xxDTO2xxBO`、`xxDTOList2BOList` |
| BO → DO                      | 应用层            | BO 内 `toXxxDO()` / `fromXxxDO()` | `application/.../converter/` 静态 Converter                                  |
| DO → Entity / Cache / 外部 DTO | 领域层            | DO 内 `toXxxEntity()` 等           | `domain/.../converter/` 静态 Converter                                       |

所有 Converter 均为**静态工具类**，**禁止** `@Component` 与 `@Resource`/`@Autowired` 注入。

## 禁止的错误用法

### ❌ 跨层使用数据对象

```java
// ❌ 错误：应用层直接使用DTO
@Service
public class TaskAppServiceImpl implements TaskAppService {
    public TaskDTO createTask(CreateTaskRequest request) {  // 错误！
        // ...
    }
}

// ✅ 正确：应用层使用BO
@Service
public class TaskAppServiceImpl implements TaskAppService {
    public TaskBO createTask(TaskBO taskBO) {  // 正确
        // ...
    }
}
```

### ❌ 命名后缀错误

```java
// ❌ 错误：应用层对象使用DO后缀
public class TaskDO {  // 在application层定义，错误！
}

// ✅ 正确：应用层对象使用BO后缀
public class TaskBO {  // 在application层定义，正确
}
```

### ❌ 缺少必要字段

```java
// ❌ 错误：领域对象缺少region字段
public class TaskDO {
    private Long id;
    private String taskCode;
    // 缺少 region 字段，错误！
}

// ✅ 正确：领域对象包含region字段
public class TaskDO {
    private Long id;
    private String taskCode;
    private String region;  // 正确
}
```

## 相关文档

- [分层架构核心原则](@specrules/00_general/architecture/layered_architecture_core.md)
- [API层标准](@specrules/00_general/architecture/api_layer_standards.md)
- [用户接口层标准](@specrules/00_general/architecture/ui_layer_standards.md)
- [应用层标准](@specrules/00_general/architecture/app_layer_standards.md)
- [领域层标准](@specrules/00_general/architecture/domain_layer_standards.md)
- [基础设施层标准](@specrules/00_general/architecture/infra_layer_standards.md)

---

## 版本与变更

- 1.1.0 (2025-02-06): 数据转换规则改为引用对象转换规范，明确 Converter 静态化与禁止注入
- 1.0.0 (2025-02-06): 初始化版本与变更记录
