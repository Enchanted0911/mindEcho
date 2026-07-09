---
description: "应用层标准规范"
alwaysApply: false
globs: ["**/application/**/*.java", "**/app/**/*.java"]
version: "1.3.0"
---

# 应用层标准规范

## 应用层服务接口与实现 (REQUIRED)

**应用层服务（AppService）必须定义接口与对应实现类，禁止仅有实现类无接口。**

- **接口**：放在 `application/service/`，命名以 `AppService` 结尾（如 `TaskAppService`、`TaskGroupCalcAppService`）。
- **实现类**：放在 `application/service/impl/`，命名以 `AppServiceImpl` 结尾（如 `TaskAppServiceImpl`），并 `implements`
  对应接口。
- **调用方**：上层（如用户接口层、MQ 消费者）只依赖 AppService **接口**，不直接依赖实现类。

## 职责定义

- **基本参数校验**: 只进行基本的参数空值检查，调用参数对象的 `doCheck()` 方法
- **业务用例编排**: 协调多个领域服务完成业务用例，不包含具体业务逻辑
- **数据转换**: BO 与 DO 之间简单转换封装在 BO（toXxxDO/fromXxxDO），复杂转换使用静态
  Converter；详见 [对象转换规范](specrules/03_coding/object_conversion_standards.md)
- **日志记录**: 记录关键业务操作的日志

## 禁止的行为 (NON-NEGOTIABLE)

- ❌ **业务逻辑校验**: 不能包含分类存在性校验、唯一性校验等业务逻辑
- ❌ **事务注解**: 应用层不允许使用 `@Transactional` 注解
- ❌ **异常包装**: 不要用 try-catch 包装业务异常，让异常自然向上传播
- ❌ **状态判断**: 不能包含复杂的业务状态判断逻辑
- ❌ **直接调用 DAO**: 必须通过领域层的 Repository 或 DomainService
- ❌ **使用 DTO 对象**: 只能使用 BO 和 DO 对象

## 标准应用层方法结构

### 普通写操作

```java
@Service
@Slf4j
public class TaskAppServiceImpl implements TaskAppService {
    
    @Autowired
    private TaskDomainService taskDomainService;
    
    @Override
    public TaskBO createTask(TaskBO taskBO) {
        log.info("创建任务, taskBO: {}", taskBO);
        
        // 1. 基本参数校验
        taskBO.doCheck();
        
        // 2. BO → DO 转换（简单转换用 BO.toTaskDO()，复杂用 XxxConverter 静态方法）
        TaskDO taskDO = taskBO.toTaskDO();
        
        // 3. 调用领域服务（业务逻辑在 DomainService 中处理）
        TaskDO resultDO = taskDomainService.createTask(taskDO);
        
        // 4. DO → BO 转换并返回
        TaskBO resultBO = TaskBO.fromTaskDO(resultDO);
        
        log.info("任务创建成功, taskCode: {}", resultBO.getTaskCode());
        return resultBO;
    }
}
```

### 分页查询

分页查询方法返回 `PageResultBO<XxxBO>`，负责将 Domain 层的 `PageResult<XxxDO>` 转换为 `PageResultBO<XxxBO>`：

```java
@Override
public PageResultBO<TaskBO> pageTask(TaskQueryBO queryBO) {
    log.info("分页查询任务, queryBO: {}", queryBO);

    // 1. 基本参数校验
    queryBO.doCheck();

    // 2. QueryBO → QueryDO
    TaskQueryDO queryDO = TaskConverter.queryBO2DO(queryBO);

    // 3. 调用领域服务，返回 PageResult<TaskDO>
    PageResult<TaskDO> pageResult = taskDomainService.pageTask(queryDO);

    // 4. DO List → BO List
    List<TaskBO> taskBOList = pageResult.getData().stream()
            .map(TaskBO::fromTaskDO)
            .collect(Collectors.toList());

    // 5. 构建 PageResultBO 返回
    return new PageResultBO<>(taskBOList, pageResult.getPageNum(),
            pageResult.getPageSize(), pageResult.getTotal());
}
```

**`PageResultBO` 定义在 `application/model/PageResultBO.java`**
，详见 [分页全链路规范](specrules/03_coding/db_pagination_and_test.md)。

## 依赖注入规范

### 1. 接口依赖 (REQUIRED)

只能依赖领域层的 Service 接口，不能直接依赖实现类。

**✅ 正确示例**：

```java
@Autowired
private TaskDomainService taskDomainService;  // 依赖接口

@Autowired
private RuleDomainService ruleDomainService;  // 依赖接口
```

**❌ 错误示例**：

```java
@Autowired
private TaskDomainServiceImpl taskDomainService;  // 直接依赖实现类（禁止）

@Autowired
private TaskDAO taskDAO;  // 直接依赖DAO（禁止）
```

### 2. 转换器使用（静态，禁止注入）

BO 与 DO 的转换：简单转换封装在 BO（如 `taskBO.toTaskDO()`、`TaskBO.fromTaskDO(taskDO)`），复杂转换使用
`application/.../converter/` 下的**静态工具类**，直接调用静态方法，**禁止** `@Component` 与 `@Resource`/`@Autowired`
注入。详见 [对象转换规范](specrules/03_coding/object_conversion_standards.md)。

### 3. 避免循环依赖

应用层服务之间避免相互依赖。如果必须协作，考虑：

- 将共同逻辑下沉到领域层
- 通过事件机制解耦
- 重新审视领域划分

### 4. 最小依赖原则

只注入必要的服务，避免过度依赖。

## 转换器使用规范

### Converter 位置与实现方式

- **位置**：`application/.../converter/`（仅复杂转换时使用；简单转换在 BO 内 toXxxDO/fromXxxDO）。
- **实现方式**：Converter 为**仅含静态方法的工具类**，禁止 `@Component`、禁止 `@Resource`/`@Autowired` 注入；调用方直接使用
  `XxxConverter.boToDO(bo)` 等静态方法。
- **命名**：方法名表达语义即可，如 `boToDO`、`doToBO`、`doListToBOList`；简单转换在 BO 上提供 `toTaskDO()`、
  `static fromTaskDO(TaskDO do)`。

### Converter 规范要求

- **方法命名**: 使用 `doToBO()`、`boToDO()`、`doListToBOList()` 等
- **空值处理**: 转换器方法必须处理 null 值情况
- **字段映射**: 只转换两层都存在的字段，技术字段在 Repository 层设置
- **完整约定**: 见 [对象转换规范](specrules/03_coding/object_conversion_standards.md)

## 复杂用例编排示例

当一个业务用例需要协调多个领域服务时：

```java
@Service
@Slf4j
public class MonthlySettleAppServiceImpl implements MonthlySettleAppService {
    
    @Autowired
    private TaskDomainService taskDomainService;
    
    @Autowired
    private MerchantRecordDomainService merchantRecordDomainService;
    
    @Autowired
    private BenefitDomainService benefitDomainService;
    
    @Override
    public MonthlySettleResultBO executeMonthlySettle(MonthlySettleBO settleBO) {
        log.info("开始月度定级, settleBO: {}", settleBO);
        
        // 1. 基本参数校验
        settleBO.doCheck();
        
        // 2. 加载任务配置（协调领域服务）
        TaskDO taskDO = taskDomainService.getTaskByCode(settleBO.getTaskCode());
        
        // 3. 获取商家记录列表（协调领域服务）
        List<MerchantRecordDO> recordList = merchantRecordDomainService
                .getRecordsByTaskAndCycle(taskDO.getId(), settleBO.getCycleStr());
        
        // 4. 批量定级（协调领域服务）
        List<MerchantRecordDO> settledRecordList = merchantRecordDomainService
                .batchSettle(recordList);
        
        // 5. 生成权益发放记录（协调领域服务）
        List<BenefitRecordDO> benefitRecordList = benefitDomainService
                .generateBenefitRecords(settledRecordList);
        
        // 6. 构建返回结果
        MonthlySettleResultBO resultBO = new MonthlySettleResultBO();
        resultBO.setTotalCount(recordList.size());
        resultBO.setSuccessCount(settledRecordList.size());
        resultBO.setBenefitCount(benefitRecordList.size());
        
        log.info("月度定级完成, result: {}", resultBO);
        return resultBO;
    }
}
```

## 日志记录规范

应用层负责记录关键业务操作的日志：

```java
// 方法入口：记录输入参数
log.info("创建任务, taskBO: {}", taskBO);

// 关键步骤：记录中间状态
log.info("任务配置加载完成, taskId: {}", taskDO.getId());

// 方法出口：记录返回结果
log.info("任务创建成功, taskCode: {}", resultBO.getTaskCode());

// 异常情况：由全局异常处理器统一记录，应用层不需要 try-catch
```

## 参数对象规范

应用层使用的参数对象以 `BO` 后缀命名：

- **业务对象**: `TaskBO`, `RuleBO`
- **查询参数**: `TaskQueryBO`, `RuleQueryBO`
- **操作参数**: `TaskUpdateBO`, `RuleUpdateBO`

参数对象必须提供 `doCheck()` 方法：

```java
public class TaskBO {
    private String taskCode;
    private String taskName;
    private String region;
    
    /**
     * 基本参数校验
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
```

## 包结构规范

```
application/
├── service/              # 应用服务接口
│   ├── TaskAppService.java
│   └── MonthlySettleAppService.java
├── service/impl/         # 应用服务实现
│   ├── TaskAppServiceImpl.java
│   └── MonthlySettleAppServiceImpl.java
├── converter/            # BO转换器
│   └── BOConverter.java
└── bo/                   # 业务对象（可选，也可放在单独的模块）
    ├── TaskBO.java
    └── MonthlySettleBO.java
```

## 相关文档

- [分层架构核心原则](@specrules/00_general/architecture/layered_architecture_core.md)
- [领域层标准](@specrules/00_general/architecture/domain_layer_standards.md)
- [数据对象命名规范](@specrules/00_general/naming/data_object_naming.md)
- [依赖注入规范](@specrules/03_coding/dependency_injection_core.md)

---

## 版本与变更

- 1.3.0 (2026-04-09): 移除已删除的 dependency_injection_advanced.md 引用，合并为 dependency_injection_core.md（该文件已于
  index v1.9.0 变更中删除）
- 1.2.0 (2026-04-02): 补充分页查询方法规范：返回 PageResultBO<XxxBO>，PageResult<XxxDO> → PageResultBO<XxxBO> 转换示例
- 1.1.0 (2025-02-06): Converter 改为静态工具类、禁止注入；引用对象转换规范
- 1.0.0 (2025-02-06): 初始化版本与变更记录
