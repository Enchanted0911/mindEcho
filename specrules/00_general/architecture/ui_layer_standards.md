---
description: "用户接口层标准规范"
alwaysApply: false
globs: ["**/starter/**/*.java", "**/controller/**/*.java"]
version: "1.1.0"
---

# 用户接口层标准规范

## 职责定义

- 接口参数校验（参数非空、格式校验等）
- DTO 与 BO 之间的数据转换
- 消息队列消费者处理
- 外部接口适配

## 核心规范

### 1. 消息队列消费者位置 (REQUIRED)

**所有 MQ 消费者必须放在用户接口层（starter层）**

**包路径示例**：

```
starter/
└── consumer/
    ├── DailyCalcConsumer.java
    ├── MonthlySettleConsumer.java
    └── BenefitGrantConsumer.java
```

**消费者实现示例**：

```java
@Component
@Slf4j
public class DailyCalcConsumer {
    
    @Autowired
    private DailyCalcAppService dailyCalcAppService;
    
    @MQConsumer(topic = "merchant_growth_daily_calc", tag = "*")
    public void consume(DailyCalcMessage message) {
        log.info("收到Daily计算消息: {}", message);
        
        // 1. 参数校验
        validateMessage(message);
        
        // 2. DTO → BO 转换（使用静态 Converter）
        DailyCalcBO calcBO = DailyCalcDTOConverter.message2BO(message);
        
        // 3. 调用应用层服务
        dailyCalcAppService.executeDailyCalc(calcBO);
    }
}
```

### 2. 数据转换规范 (REQUIRED)

Starter 层**一律使用 Converter** 进行 DTO/VO 与 BO 之间的转换。Converter 为**静态工具类**，禁止 `@Component`、禁止
`@Resource`/`@Autowired` 注入；命名建议 `xxDTO2xxBO`、`xxDTOList2BOList`、`xxBO2xxDTO`
。详见 [对象转换规范](specrules/03_coding/object_conversion_standards.md)。

**Converter 位置**：`starter/.../converter/`（如 `TaskDTOConverter.java`）

**Converter 实现示例**（静态方法）：

```java
public final class TaskDTOConverter {

    private TaskDTOConverter() {}

    public static TaskBO request2BO(CreateTaskRequest request) {
        if (request == null) return null;
        TaskBO bo = new TaskBO();
        bo.setTaskCode(request.getTaskCode());
        bo.setTaskName(request.getTaskName());
        bo.setRegion(request.getRegion());
        return bo;
    }

    public static TaskDTO bo2DTO(TaskBO bo) {
        if (bo == null) return null;
        TaskDTO dto = new TaskDTO();
        dto.setTaskCode(bo.getTaskCode());
        dto.setTaskName(bo.getTaskName());
        dto.setRegion(bo.getRegion());
        return dto;
    }

    public static List<TaskBO> requestList2BOList(List<CreateTaskRequest> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(TaskDTOConverter::request2BO).collect(Collectors.toList());
    }
}
```

### 3. 参数校验规范

在用户接口层进行接口参数的基本校验。**禁止使用 Bean Validation 注解（`@Valid`、`@NotNull`、`@NotBlank` 等）**，统一使用代码方式校验（如 `Objects.requireNonNull`、`StringUtils.isNotBlank`、手动 if 判断）。

**REST API 普通接口实现示例**：

```java
@RestController
@RequestMapping("/api/merchant/growth")
@Slf4j
public class MerchantGrowthGatewayServiceImpl implements MerchantGrowthGatewayService {
    
    @Autowired
    private TaskAppService taskAppService;
    
    @Override
    @PostMapping("/task/create")
    public Result<TaskDTO> createTask(@RequestBody CreateTaskRequest request) {
        log.info("创建任务请求: {}", request);
        
        // 1. 参数校验（代码方式）
        if (!org.apache.commons.lang3.StringUtils.isNotBlank(request.getTaskCode())) {
            throw new IllegalArgumentException("任务编码不能为空");
        }
        if (!org.apache.commons.lang3.StringUtils.isNotBlank(request.getTaskName())) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        Objects.requireNonNull(request.getRegion(), "地区不能为空");
        
        // 2. DTO → BO 转换（静态 Converter）
        TaskBO taskBO = TaskDTOConverter.request2BO(request);
        
        // 3. 调用应用层服务
        TaskBO resultBO = taskAppService.createTask(taskBO);
        
        // 4. BO → DTO 转换（静态 Converter）
        TaskDTO taskDTO = TaskDTOConverter.bo2DTO(resultBO);
        
        // 5. 返回结果
        return Result.success(taskDTO);
    }
}
```

**分页接口实现示例**（REST / RPC 均适用）：

分页接口从 `PageResultBO` 转换为 `BasePageResultDTO`，使用 `BasePageResultDTO.success(list, pageNum, pageSize, total)`：

```java
@Override
public BasePageResultDTO<TaskListResponse> pageTask(TaskListRequest request) {
    try {
        // 1. Request → QueryBO（静态 Converter）
        TaskQueryBO queryBO = TaskDTOConverter.request2QueryBO(request);

        // 2. 调用 AppService，返回 PageResultBO
        PageResultBO<TaskBO> pageResultBO = taskAppService.pageTask(queryBO);

        // 3. BO List → Response List（静态 Converter）
        List<TaskListResponse> responseList = pageResultBO.getData().stream()
                .map(TaskDTOConverter::bo2ListResponse)
                .collect(Collectors.toList());

        // 4. 构建 BasePageResultDTO 返回
        return BasePageResultDTO.success(
                responseList,
                pageResultBO.getPageNum(),
                pageResultBO.getPageSize(),
                pageResultBO.getTotal()
        );
    } catch (RuntimeException e) {
        return BasePageResultDTO.error(e.getCode(), e.getMessage());
    } catch (Exception e) {
        return BasePageResultDTO.error(CommonErrorCodeEnum.SYSTEM_ERROR.getCode(), "system error,please retry");
    }
}
```

详见 [分页全链路规范](specrules/03_coding/db_pagination_and_test.md)。

### 4. 时序图要求

设计文档中的时序图必须体现分层架构，使用具体的实现类名称。

**示例**：

```
用户 → MerchantGrowthGatewayServiceImpl → TaskAppServiceImpl → TaskDomainServiceImpl → TaskRepositoryImpl → TaskDAOImpl → MySQL
```

## 包结构规范

```
starter/
├── controller/           # REST API 接口实现（不推荐，建议直接在starter根目录）
├── gateway/             # REST API 接口实现
├── thrift/              # RPC 接口实现
├── consumer/            # MQ消费者
├── converter/           # DTO转换器
└── config/              # 配置类
```

## 禁止事项

- ❌ **不能包含业务逻辑**: 业务逻辑属于领域层职责
- ❌ **不能直接调用 DAO**: 必须通过应用层和领域层
- ❌ **不能使用 DO 对象**: 只能使用 DTO 和 BO 对象
- ❌ **不能包含 BO ↔ DO 转换**: 该转换属于应用层职责

## 异常处理

用户接口层负责捕获异常并转换为统一的响应格式：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleBusinessException(RuntimeException e) {
        log.error("业务异常", e);
        return Result.error(e.getCode(), e.getMessage());
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数校验失败", e);
        return Result.error("PARAM_INVALID", e.getMessage());
    }
}
```

## 相关文档

- [分层架构核心原则](@specrules/00_general/architecture/layered_architecture_core.md)
- [API层标准](@specrules/00_general/architecture/api_layer_standards.md)
- [应用层标准](@specrules/00_general/architecture/app_layer_standards.md)
- [数据对象命名规范](@specrules/00_general/naming/data_object_naming.md)

---

## 版本与变更

- 1.2.0 (2026-04-02): 补充分页接口实现规范：PageResultBO → BasePageResultDTO.success(list, pageNum, pageSize, total) 转换示例
- 1.1.0 (2025-02-06): Converter 改为静态工具类、禁止注入；引用对象转换规范
- 1.0.0 (2025-02-06): 初始化版本与变更记录
