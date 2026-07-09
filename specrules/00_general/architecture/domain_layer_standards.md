---
description: "领域层标准规范"
alwaysApply: false
globs: ["**/domain/**/*.java"]
version: "1.0.0"
---

# 领域层标准规范

## 职责定义

- 核心业务逻辑实现
- 业务规则验证
- 领域对象行为定义
- 业务状态管理

## 领域服务接口与实现 (REQUIRED)

**领域服务（DomainService）必须定义接口与对应实现类，禁止仅有实现类无接口。**

- **接口**：放在 `domain/service/`，命名以 `DomainService` 结尾（如 `TaskDomainService`、`TaskGroupCalcDomainService`）。
- **实现类**：放在 `domain/service/impl/`，命名以 `DomainServiceImpl` 结尾（如 `TaskDomainServiceImpl`），并 `implements`
  对应接口。
- **调用方**：应用层只依赖 DomainService **接口**，不直接依赖实现类。

## 包结构规范

```
domain/
├── model/          # 领域对象（DO）
│   ├── TaskDO.java
│   └── RuleDO.java
├── enums/          # 枚举类
│   ├── TaskStatusEnum.java
│   └── RuleOperatorEnum.java
├── service/        # 领域服务接口
│   ├── TaskDomainService.java
│   └── RuleDomainService.java
├── service/impl/   # 领域服务实现
│   ├── TaskDomainServiceImpl.java
│   └── RuleDomainServiceImpl.java
└── repository/     # Repository接口和实现（数据访问层）
    ├── TaskRepository.java
    ├── TaskRepositoryImpl.java
    ├── RuleRepository.java
    └── RuleRepositoryImpl.java
```

## Domain 对 Infrastructure 的依赖 (REQUIRED)

- **原则**：领域层依赖的是**基础设施层提供的接口**，不关注 Infra 的具体实现（如 DAOImpl、Proxy 实现类等）。Domain 只依赖”接口契约”，由
  Infra 模块提供实现并注入。
- **Repository**：
    - **接口与实现均在领域层**（`domain/repository/`）。领域服务（DomainService）**直接依赖 Repository 接口**，不依赖 Infra。
    - Repository **实现类**在领域层内，通过依赖 Infra **提供的接口**（如 DAO、Proxy、Cache 等）对接持久化或外部服务；Domain
      不关心这些接口在 Infra 内是由 DAOImpl、Proxy 还是 Cache 实现。
- **总结**：Domain Service → Repository（接口 + 实现均在 domain）→ Infra 提供的接口（DAO/Proxy/Cache 等）；Infra 的具体实现（如
  `*Proxy`、`*DAOImpl`）只在 Infra 层，Domain 不直接依赖。

### 是否存在 Repository 层的条件性约束

上述约束**仅在项目已引入 Repository 层时适用**。判断标准：项目中存在 `domain/repository/` 包且有 Repository 接口定义。

| 项目是否有 Repository 层 | DomainService 对 Entity 的依赖          | 说明                                      |
|--------------------|-------------------------------------|-----------------------------------------|
| ✅ 有                | ❌ 禁止直接引用 `*Entity`                  | 必须通过 Repository 抽象，DomainService 只感知 DO |
| ❌ 无                | ✅ 允许直接依赖 Infra 的 `*Entity` 和 DAO 接口 | 尚未引入 Repository 抽象，直接读写 Entity 不视为违规    |

**架构审查时**：检测到 DomainService 直接引用 `*Entity` 时，先确认项目是否存在 Repository 层，再判断是否为 BLOCKING 违规。

## 领域对象 (DO) 规范

### 1. 命名和位置

- 必须放在 `domain/model/` 包下
- 使用 `DO` 后缀命名（如: `TaskDO`, `RuleDO`）

### 2. Region 字段要求 (REQUIRED)

**所有核心业务领域对象都必须包含 `region` 字段，类型为 `String`**

```java
@Data
public class TaskDO {
    private Long id;
    private String taskCode;
    private String taskName;
    private String region;  // 必须包含
    private Integer status;
    // ... 其他字段
}
```

### 3. 业务行为方法

领域对象应包含业务行为方法和状态流转逻辑：

```java
@Data
public class TaskDO {
    private Long id;
    private String taskCode;
    private Integer status;
    private String region;
    
    /**
     * 发布任务（状态流转）
     */
    public void publish() {
        // 业务规则校验
        if (TaskStatusEnum.PUBLISHED.value().equals(this.status)) {
            throw new RuntimeException("任务已发布，不能重复发布");
        }
        
        if (!TaskStatusEnum.DRAFT.value().equals(this.status)) {
            throw new RuntimeException("只有草稿状态的任务才能发布");
        }
        
        // 状态流转
        this.status = TaskStatusEnum.PUBLISHED.value();
    }
    
    /**
     * 归档任务
     */
    public void archive() {
        if (TaskStatusEnum.ARCHIVED.value().equals(this.status)) {
            throw new RuntimeException("任务已归档");
        }
        
        this.status = TaskStatusEnum.ARCHIVED.value();
    }
    
    /**
     * 检查任务是否有效
     */
    public boolean isValid() {
        return !TaskStatusEnum.ARCHIVED.value().equals(this.status);
    }
}
```

### 4. 对象转换方法

领域对象应提供与数据库实体之间的转换方法：

```java
/**
 * DO → Entity
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
 * Entity → DO
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
```

## 枚举设计规范

### 1. 位置和命名

- 必须放在 `domain/enums/` 包下
- 使用 `Enum` 后缀命名（如: `TaskStatusEnum`, `RuleOperatorEnum`）

### 2. 标准枚举结构

```java
public enum TaskStatusEnum {
    
    DRAFT(1, "草稿"),
    PUBLISHED(2, "已发布"),
    RUNNING(3, "运行中"),
    PAUSED(4, "已暂停"),
    ARCHIVED(5, "已归档");
    
    private final Integer value;
    private final String desc;
    
    TaskStatusEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }
    
    public Integer value() {
        return value;
    }
    
    public String desc() {
        return desc;
    }
    
    /**
     * 根据值获取枚举
     */
    public static TaskStatusEnum getByValue(Integer value) {
        for (TaskStatusEnum status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid TaskStatus value: " + value);
    }
}
```

### 3. 扩展性要求

状态枚举要考虑业务扩展性，预留足够的状态值。

**✅ 良好的设计**：

```java
DRAFT(1, "草稿"),          // 初始状态
PUBLISHED(2, "已发布"),     // 发布状态
RUNNING(3, "运行中"),       // 运行状态
PAUSED(4, "已暂停"),        // 暂停状态
ARCHIVED(5, "已归档"),      // 归档状态
// 预留 6-10 用于未来扩展
```

## 领域服务规范

### 1. 接口与实现分离 (REQUIRED)

所有 DomainService 必须定义接口和实现。

**接口位置**: `domain/service/`

```java
public interface TaskDomainService {
    
    /**
     * 创建任务
     */
    TaskDO createTask(TaskDO taskDO);
    
    /**
     * 发布任务
     */
    TaskDO publishTask(String taskCode, String region);
    
    /**
     * 验证任务规则
     */
    boolean validateTaskRules(TaskDO taskDO);
}
```

**实现位置**: `domain/service/impl/`

```java
@Service
@Slf4j
public class TaskDomainServiceImpl implements TaskDomainService {
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private RuleRepository ruleRepository;
    
    @Override
    public TaskDO createTask(TaskDO taskDO) {
        log.info("创建任务, taskDO: {}", taskDO);
        
        // 1. 业务规则校验
        validateTaskUniqueness(taskDO);
        
        // 2. 设置初始状态
        taskDO.setStatus(TaskStatusEnum.DRAFT.value());
        
        // 3. 保存到数据库
        TaskDO savedTaskDO = taskRepository.save(taskDO);
        
        log.info("任务创建成功, taskId: {}", savedTaskDO.getId());
        return savedTaskDO;
    }
    
    @Override
    public TaskDO publishTask(String taskCode, String region) {
        log.info("发布任务, taskCode: {}, region: {}", taskCode, region);
        
        // 1. 查询任务
        TaskDO taskDO = taskRepository.getByCodeAndRegion(taskCode, region);
        if (taskDO == null) {
            throw new RuntimeException("任务不存在");
        }
        
        // 2. 验证规则完整性
        if (!validateTaskRules(taskDO)) {
            throw new RuntimeException("任务规则不完整，不能发布");
        }
        
        // 3. 状态流转（领域对象行为）
        taskDO.publish();
        
        // 4. 更新到数据库
        TaskDO updatedTaskDO = taskRepository.update(taskDO);
        
        log.info("任务发布成功, taskId: {}", updatedTaskDO.getId());
        return updatedTaskDO;
    }
    
    @Override
    public boolean validateTaskRules(TaskDO taskDO) {
        // 业务规则验证逻辑
        List<RuleDO> ruleList = ruleRepository.getByTaskId(taskDO.getId());
        
        if (CollectionUtils.isEmpty(ruleList)) {
            return false;
        }
        
        // 检查规则完整性
        for (RuleDO ruleDO : ruleList) {
            if (!ruleDO.isValid()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 私有方法：校验任务唯一性
     */
    private void validateTaskUniqueness(TaskDO taskDO) {
        TaskDO existingTask = taskRepository.getByCodeAndRegion(
                taskDO.getTaskCode(), taskDO.getRegion());
        
        if (existingTask != null) {
            throw new RuntimeException("任务编码已存在: " + taskDO.getTaskCode());
        }
    }
}
```

### 2. 命名规范

- **接口命名**: 直接使用业务名称 + `DomainService`（如: `TaskDomainService`）
- **实现命名**: 接口名 + `Impl`（如: `TaskDomainServiceImpl`）

### 3. 职责边界

领域服务应包含：

- ✅ 业务规则验证（如唯一性校验、完整性校验）
- ✅ 复杂业务逻辑（如多步骤的业务处理）
- ✅ 领域对象的协调（如多个DO之间的交互）
- ✅ 状态管理和流转（调用DO的行为方法）

领域服务不应包含：

- ❌ 数据库操作细节（通过Repository抽象）
- ❌ 外部服务调用（通过Proxy在基础设施层）
- ❌ BO ↔ DO 转换（属于应用层职责）
- ❌ 事务管理（事务在Repository或更底层）

## Repository 规范

### 1. 接口与实现

**Repository 接口和实现都定义在领域层** (`domain/repository/`)。领域服务直接依赖 Repository 接口；Repository
实现类在领域层内，通过依赖基础设施层**提供的接口**（如 DAO、Proxy、Cache）对接持久化或外部服务，不关注 Infra 内具体由谁实现（如
`*DAOImpl`、`*Proxy` 等）。

```java
public interface TaskRepository {
    
    /**
     * 保存任务
     */
    TaskDO save(TaskDO taskDO);
    
    /**
     * 更新任务
     */
    TaskDO update(TaskDO taskDO);
    
    /**
     * 根据编码和地区查询
     */
    TaskDO getByCodeAndRegion(String taskCode, String region);
    
    /**
     * 根据ID查询
     */
    TaskDO getById(Long id);

    /**
     * 非分页列表查询：返回 List
     */
    List<TaskDO> listByQuery(TaskQueryDO queryDO);

    /**
     * 分页查询：返回 PageResult，方法名以 pageBy 开头以区分非分页查询
     */
    PageResult<TaskDO> pageByQuery(TaskQueryDO queryDO);
}
```

**命名约定**：

- `listByXxx`：非分页，返回 `List<TaskDO>`
- `pageByXxx`：分页，返回 `PageResult<TaskDO>`

通过方法名即可区分是否分页，禁止用同一个方法名靠参数中是否有 `pageNum` 来区分。

### 2. 查询参数对象

使用 `QueryDO` 后缀封装查询条件。**仅分页查询（`pageByXxx` 方法）时**，QueryDO 才包含 `pageNum`/`pageSize` 字段；非分页查询不需要添加：

```java
// 分页查询参数（含 pageNum/pageSize）
@Data
public class TaskQueryDO {
    private String taskCode;
    private String region;
    private Integer status;
    private Long startTime;
    private Long endTime;
    // 分页字段
    private Integer pageNum;
    private Integer pageSize;
}
```

### 3. PageResult 定义位置

分页查询结果使用 `PageResult<T>` 承载，定义位置取决于项目是否有 Repository 层：

- **有 Repository 层**：`PageResult` 定义在 `domain/model/PageResult.java`
- **无 Repository 层**：`PageResult` 定义在 `infrastructure/commons/PageResult.java`

详细实现规范见 [分页全链路规范](specrules/03_coding/db_pagination_and_test.md)。

### 3. Repository 实现示例

```java
// Repository 接口
public interface TaskRepository {
    TaskDO save(TaskDO taskDO);
    TaskDO update(TaskDO taskDO);
    TaskDO getByCodeAndRegion(String taskCode, String region);
    TaskDO getById(Long id);
    List<TaskDO> listByQuery(TaskQueryDO queryDO);
}

// Repository 实现（同样在 domain/repository/ 下）
@Repository
@Slf4j
public class TaskRepositoryImpl implements TaskRepository {
    
    @Resource
    private TaskDAO taskDAO;  // 依赖基础设施层提供的 DAO 接口，不关心 Infra 内实现
    
    @Override
    @Transactional
    public TaskDO save(TaskDO taskDO) {
        log.info("保存任务, taskDO: {}", taskDO);
        
        // 1. DO → Entity 转换
        TaskEntity entity = taskDO.toEntity();
        
        // 2. 调用 DAO 保存
        taskDAO.insert(entity);
        
        // 3. 回填ID
        taskDO.setId(entity.getId());
        
        log.info("任务保存成功, taskId: {}", taskDO.getId());
        return taskDO;
    }
    
    @Override
    public TaskDO getByCodeAndRegion(String taskCode, String region) {
        TaskDAO.QueryCondition condition = new TaskDAO.QueryCondition();
        condition.setTaskCode(taskCode);
        condition.setRegion(region);
        
        List<TaskEntity> entityList = taskDAO.selectByCondition(condition);
        
        if (CollectionUtils.isEmpty(entityList)) {
            return null;
        }
        
        return TaskDO.fromEntity(entityList.get(0));
    }
}
```

### 4. 命名规范

- **接口命名**: 使用 `Repository` 后缀（如: `TaskRepository`）
- **实现命名**: 加 `Impl` 后缀（如: `TaskRepositoryImpl`）
- **位置**: 接口和实现都放在 `domain/repository/` 包下

### 5. Repository 职责

Repository 负责：

- ✅ DO ↔ Entity 转换
- ✅ 聚合 MySQL 和 ES 数据（如需要）
- ✅ 调用基础设施层 DAO 进行数据库操作
- ✅ 参数对象转换（QueryDO → DAO.QueryCondition）
- ✅ 事务管理（使用 `@Transactional` 注解）

Repository 不负责：

- ❌ 业务逻辑处理
- ❌ 业务规则验证
- ❌ 复杂的数据计算

## 事务管理

**DomainService 不添加 @Transactional，事务由 Repository 管理**。

**✅ 正确做法**：

```java
// DomainService 不添加 @Transactional
@Service
public class TaskDomainServiceImpl implements TaskDomainService {
    
    @Resource
    private TaskRepository taskRepository;
    
    @Override
    public TaskDO createTask(TaskDO taskDO) {
        // 业务逻辑校验
        validateTaskUniqueness(taskDO);
        
        // 调用 Repository（事务在 Repository 中管理）
        return taskRepository.save(taskDO);
    }
}

// Repository 实现添加事务
@Repository
public class TaskRepositoryImpl implements TaskRepository {
    
    @Resource
    private TaskDAO taskDAO;
    
    @Override
    @Transactional  // ✅ 事务在这里管理
    public TaskDO save(TaskDO taskDO) {
        TaskEntity entity = taskDO.toEntity();
        taskDAO.insert(entity);
        taskDO.setId(entity.getId());
        return taskDO;
    }
}
```

## 相关文档

- [分层架构核心原则](@specrules/00_general/architecture/layered_architecture_core.md)
- [应用层标准](@specrules/00_general/architecture/app_layer_standards.md)
- [基础设施层标准](@specrules/00_general/architecture/infra_layer_standards.md)
- [状态机设计规范](@specrules/02_design/state_machine_design.md)
- [数据对象命名规范](@specrules/00_general/naming/data_object_naming.md)

---

## 版本与变更

- 1.2.0 (2026-04-02): Repository 分页方法命名约定（pageByXxx 返回 PageResult，listByXxx 返回 List）；QueryDO
  分页字段按需添加说明；PageResult 定义位置规范
- 1.1.0 (2026-04-01): 新增「是否存在 Repository 层的条件性约束」——无 Repository 层时允许 DomainService 直接依赖 Infra
  Entity
- 1.0.0 (2025-02-06): 初始化版本与变更记录
