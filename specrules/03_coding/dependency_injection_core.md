---
description: "依赖注入规范：接口与实现分离、注入方式、分层架构依赖、避免循环依赖、最小依赖原则、单元测试中的依赖注入"
alwaysApply: false
globs: ["**/*.java"]
version: "2.0.0"
---

# 依赖注入规范

## 接口与实现分离 (NON-NEGOTIABLE)

所有服务层都通过接口进行依赖注入，不直接依赖实现类。

**必须定义接口与实现类的组件（强约束）**：

- **AppService**：应用层服务必须存在接口（如 `TaskAppService`）与实现类（如 `TaskAppServiceImpl`
  ），详见 [应用层标准](specrules/00_general/architecture/app_layer_standards.md)。
- **DomainService**：领域服务必须存在接口（如 `TaskDomainService`）与实现类（如 `TaskDomainServiceImpl`
  ），详见 [领域层标准](specrules/00_general/architecture/domain_layer_standards.md)。
- **Proxy**：基础设施层外部服务代理必须存在接口（如 `GrowthServiceProxy`）与实现类（如 `GrowthServiceProxyImpl`
  ），详见 [基础设施层标准](specrules/00_general/architecture/infra_layer_standards.md)。

**✅ 正确示例**：

```java
@Service
public class TaskAppServiceImpl implements TaskAppService {

    @Resource
    private TaskDomainService taskDomainService;  // 依赖接口

    @Resource
    private RuleDomainService ruleDomainService;  // 依赖接口
}
```

**❌ 错误示例**：

```java
@Service
public class TaskAppServiceImpl implements TaskAppService {

    @Autowired
    private TaskDomainServiceImpl taskDomainService;  // 直接依赖实现类（禁止）
}
```

## 依赖注入方式 (NON-NEGOTIABLE)

**必须使用 `@Resource` 字段注入**，禁止构造器注入和 Setter 注入。

```java
@Service
public class TaskAppServiceImpl implements TaskAppService {

    @Resource
    private TaskDomainService taskDomainService;

    @Resource
    private RuleDomainService ruleDomainService;

    // Converter 为静态工具类，不注入，直接调用 XxxConverter.xxx() 或 BO.toXxxDO()/fromXxxDO()
}
```

### 禁止：构造器注入

```java
// ❌ 禁止：不允许使用构造器注入
@Service
public class TaskAppServiceImpl implements TaskAppService {

    private final TaskDomainService taskDomainService;
    private final RuleDomainService ruleDomainService;

    @Autowired
    public TaskAppServiceImpl(
            TaskDomainService taskDomainService,
            RuleDomainService ruleDomainService) {
        this.taskDomainService = taskDomainService;
        this.ruleDomainService = ruleDomainService;
    }
}
```

### 禁止：Setter 注入

```java
// ❌ 禁止：不允许使用 Setter 注入
@Autowired
public void setTaskDomainService(TaskDomainService taskDomainService) {
    this.taskDomainService = taskDomainService;
}
```

### 禁止：@Autowired 字段注入

```java
// ❌ 禁止：使用 @Autowired 字段注入
@Autowired
private TaskDomainService taskDomainService;

// ✅ 正确：使用 @Resource 字段注入
@Resource
private TaskDomainService taskDomainService;
```

## 分层架构依赖规范

各层允许和禁止依赖的完整规范见对应层 standards 文件：

- **API层**
  ：只定义接口契约，不包含实现，不需要依赖注入。详见 [API层标准](specrules/00_general/architecture/api_layer_standards.md)
- **用户接口层**：依赖 AppService 接口；Converter
  为静态工具类，不注入。详见 [用户接口层标准](specrules/00_general/architecture/ui_layer_standards.md)
- **应用层**：依赖 DomainService 接口；BO↔DO 简单转换用 BO 方法，复杂转换用静态
  Converter，不注入。详见 [应用层标准](specrules/00_general/architecture/app_layer_standards.md)
- **领域层**：依赖 Repository 接口和其他 DomainService 接口；允许依赖**本工程以外**的 API 模块，禁止依赖本工程 API
  模块。详见 [领域层标准](specrules/00_general/architecture/domain_layer_standards.md)
- **领域层 Repository**：依赖基础设施层提供的接口（DAO/Proxy/Cache），不关注 Infra 内具体实现（`*DAOImpl`、`*Proxy`）。
- **基础设施层**：依赖 DAO 接口、MyBatis Mapper 接口、外部服务
  Client。详见 [基础设施层标准](specrules/00_general/architecture/infra_layer_standards.md)

**Converter 统一规则**：Converter 为静态工具类，禁止 `@Component` 与 `@Resource`/`@Autowired`
注入，调用方直接使用静态方法。详见 [对象转换规范](specrules/03_coding/object_conversion_standards.md)。

---

## 避免循环依赖

### 识别循环依赖

```java
// ❌ 错误：循环依赖
@Service
public class TaskAppServiceImpl implements TaskAppService {
    @Autowired
    private RuleAppService ruleAppService;  // A依赖B
}

@Service
public class RuleAppServiceImpl implements RuleAppService {
    @Autowired
    private TaskAppService taskAppService;  // B依赖A（循环依赖）
}
```

### 解决方案

**方案 A: 提取共同逻辑到领域层**

```java
@Service
public class TaskRuleDomainServiceImpl implements TaskRuleDomainService {
    @Resource
    private TaskRepository taskRepository;
    @Resource
    private RuleRepository ruleRepository;

    public void validateTaskWithRules(TaskDO taskDO) {
        // 共同逻辑
    }
}

// AppService 分别依赖 DomainService，避免循环依赖
@Service
public class TaskAppServiceImpl implements TaskAppService {
    @Resource
    private TaskRuleDomainService taskRuleDomainService;
}
```

**方案 B: 重新审视领域划分**

如果 AppService 之间需要相互调用，说明领域划分可能有问题，考虑合并为一个更大的聚合或重新划分边界。

**方案 C: 使用事件机制解耦**

```java
@Service
public class TaskAppServiceImpl implements TaskAppService {
    @Resource
    private ApplicationEventPublisher eventPublisher;

    public void createTask(TaskBO taskBO) {
        TaskDO taskDO = taskDomainService.createTask(taskBO);
        eventPublisher.publishEvent(new TaskCreatedEvent(taskDO.getId()));
    }
}

@Service
public class RuleAppServiceImpl implements RuleAppService {
    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        // 处理任务创建后的规则初始化逻辑
    }
}
```

## 最小依赖原则

### 只注入必要的服务

```java
// ✅ 正确：只注入需要的服务（Converter 不注入，使用静态方法）
@Service
public class TaskAppServiceImpl implements TaskAppService {
    @Resource
    private TaskDomainService taskDomainService;
}

// ❌ 错误：注入不需要的服务
@Service
public class TaskAppServiceImpl implements TaskAppService {
    @Resource
    private TaskDomainService taskDomainService;
    @Resource
    private RuleDomainService ruleDomainService;  // 如果不使用，不要注入
}
```

### 避免过度依赖

如果一个服务依赖超过 5 个其他服务，需要考虑是否职责过重，可拆分为多个 AppService。

## 单元测试中的依赖注入

Converter 为静态工具类、不注入，单测中**无需 Mock Converter**；BO↔DO 若使用 BO 的 `toXxxDO`/`fromXxxDO` 或静态
Converter，直接按真实转换验证即可。

```java
@ExtendWith(MockitoExtension.class)
public class TaskAppServiceImplTest {

    @InjectMocks
    private TaskAppServiceImpl taskAppService;

    @Mock
    private TaskDomainService taskDomainService;

    @Test
    public void testCreateTask() {
        TaskBO taskBO = new TaskBO();
        taskBO.setTaskCode("TEST001");

        TaskDO taskDO = new TaskDO();
        taskDO.setId(1L);
        taskDO.setTaskCode("TEST001");

        // Mock 领域服务（Converter 为静态，无需 Mock）
        when(taskDomainService.createTask(any())).thenReturn(taskDO);

        // 执行测试（内部使用 taskBO.toTaskDO() / TaskBO.fromTaskDO() 或静态 Converter）
        TaskBO result = taskAppService.createTask(taskBO);

        assertNotNull(result);
        assertEquals("TEST001", result.getTaskCode());
        verify(taskDomainService).createTask(any());
    }
}
```

## 相关文档

- [分层架构核心原则](../00_general/architecture/layered_architecture_core.md)
- [应用层标准](../00_general/architecture/app_layer_standards.md)
- [领域层标准](../00_general/architecture/domain_layer_standards.md)
- [基础设施层标准](../00_general/architecture/infra_layer_standards.md)
- [对象转换规范](specrules/03_coding/object_conversion_standards.md)

---

## 版本与变更
- 2.0.0 (2026-05-07): 依赖注入方式升级为 NON-NEGOTIABLE：必须使用 @Resource 字段注入，禁止构造器注入、Setter 注入和 @Autowired 字段注入
- 1.2.0 (2026-03-25): 合并 dependency_injection_advanced.md；删除各层重复依赖列表（改为引用各层 standards）；内容无丢失，重复内容去除
- 1.1.0 (2025-02-06): Converter 改为静态、不注入；引用对象转换规范
- 1.0.0 (2025-02-06): 初始化版本与变更记录
