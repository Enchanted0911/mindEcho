---
description: "API层设计规范和接口标准"
alwaysApply: false
globs: ["**/api/**/*.java"]
version: "1.2.0"
---

# API层标准规范

## 职责定义

- 定义对外接口契约
- 提供 REST API 接口（API Gateway）和 RPC 接口
- 定义请求和响应数据结构

## 包结构规范

```
api/
├── gateway/        # REST API 接口定义
├── thrift/         # RPC 接口定义
├── request/        # 请求DTO
├── response/       # 响应DTO
└── dto/           # 通用DTO
```

## 接口设计规范

### 1. 双接口要求 (REQUIRED)

所有对外接口都需要同时提供 REST API 接口和 RPC 接口。

**示例**：

```java
// Gateway接口
@RestController
@RequestMapping("/api/merchant/growth")
public interface MerchantGrowthGatewayService {
    @PostMapping("/task/create")
    Result<TaskDTO> createTask(@RequestBody CreateTaskRequest request);
}

// RPC接口（与 REST API 一致，返回 ResultWrapper 包装）
@RpcService
public interface MerchantGrowthRpcService {
    @RpcMethod
    ResultWrapper<TaskDTO> createTask(CreateTaskRequest request);
}
```

### 2. 返回值规范

- **REST API 接口**: 返回统一的 Result 包装类
- **RPC 接口**: 与 REST API 一致，必须使用统一返回对象包装（禁止直接返回业务 DTO）

**统一返回对象**（REST API 与 RPC 均需遵守）：

- 普通接口使用 `ResultWrapper<T>`
- 分页接口使用 `BasePageResultDTO<T>`
- 分页信息使用 `PageDTO` 对象（`BasePageResultDTO` 内部通过 `PageDTO` 承载
  pageNum/pageSize/total 等分页元信息，调用 `BasePageResultDTO.success(list, pageNum, pageSize, total)`
  时框架自动填充，开发者无需手动构造）
- **禁止返回 `ResultWrapper<Void>`**：无业务数据（void 语义）的接口应返回 `ResultWrapper<Boolean>`，例如仅表示成功/失败的写操作
- **禁止**使用 `ResultWrapper<PageDTO<T>>` 或 `ResultWrapper<List<T>>` 作为分页接口返回类型

### 3. 参数对象化与参数个数 (REQUIRED)

#### 3.1 参数个数：超过 3 个必须用对象封装

- 接口方法参数**超过 3 个**时，必须使用 Request/Param 对象封装，不得在方法签名上罗列多个单独参数。
- 建议：即使 ≤ 3 个参数，也优先使用单一 Request 对象，便于扩展与文档化。

**✅ 正确示例**：

```java
// 参数封装为单一 Request 对象
public ResultWrapper<TaskDTO> createTask(CreateTaskRequest request);

// 即使 2～3 个参数，也推荐对象封装
public BasePageResultDTO<TaskDTO> listTask(TaskListRequest request);  // request 内含 pageNum, pageSize, region 等
```

**❌ 错误示例**：

```java
// 错误：超过 3 个参数仍平铺在方法签名上
public ResultWrapper<TaskDTO> createTask(String taskCode, String taskName, Integer status, String region, Long operatorId);

// 错误：多个零散参数，难以维护和扩展
public ResultWrapper<TaskDTO> updateTask(String taskCode, String region, Integer status, String remark);
```

**修正**：将上述参数封装为 `CreateTaskRequest`、`UpdateTaskRequest` 等，方法签名仅保留一个 Request 参数。

#### 3.2 禁止基本类型：必须使用包装类型

- 接口方法参数**禁止使用 Java 基本类型**（如 `int`、`long`、`short`、`boolean`、`float`、`double`、`byte`、`char`）。
- 必须使用**包装类型**：`Integer`、`Long`、`Short`、`Boolean`、`Float`、`Double`、`Byte`；或业务语义更明确的类型（如 `String`
  、枚举、BigDecimal）。
- 理由：包装类型可表示 null，便于区分「未传」与「传了默认值」；RPC 序列化与可选字段语义更一致。

**✅ 正确示例**：

```java
@RpcStruct
public class TaskQueryRequest implements Serializable {
    @RpcField(1)
    private String taskCode;
    @RpcField(2)
    private Integer status;          // 使用 Integer，不用 int
    @RpcField(3)
    private Long pageNum;           // 使用 Long，不用 long
    @RpcField(4)
    private Boolean published;     // 使用 Boolean，不用 boolean
}
```

**❌ 错误示例**：

```java
// 错误：方法参数使用基本类型
public ResultWrapper<TaskDTO> getTask(long taskId, boolean includeDetail);

// 错误：Request/DTO 字段使用基本类型
private int status;      // 应使用 Integer
private long taskId;     // 应使用 Long
private boolean active; // 应使用 Boolean
private short type;      // 应使用 Short 或 Integer
```

**修正**：方法参数改为 Request 对象；Request/DTO 字段改为 `Integer`、`Long`、`Boolean`、`Short` 等包装类型。

### 4. RPC 注解规范

**接口注解**：

```java
@RpcService
public interface MerchantGrowthRpcService {
    @RpcMethod
    ResultWrapper<TaskDTO> createTask(CreateTaskRequest request);  // 与 REST API 一致，使用 ResultWrapper 包装
}
```

**数据对象注解**：

```java
@RpcStruct
public class CreateTaskRequest {
    @RpcField(1)
    private String taskCode;
    
    @RpcField(2)
    private String taskName;
    
    @RpcField(3)
    private Integer status;
}
```

**关键要求**：

- 接口使用 `@RpcService` 和 `@RpcMethod` 注解
- 数据对象使用 `@RpcStruct` 和 `@RpcField` 注解
- RpcField 必须指定序号，从 1 开始递增

## 数据传输对象 (DTO) 规范

### 命名规范

- **Request**: 以 `Request` 结尾 (如: `CreateTaskRequest`)
- **Response**: 以 `Response` 结尾 (如: `TaskDetailResponse`)
- **通用DTO**: 以 `DTO` 结尾 (如: `TaskDTO`, `StageDTO`)

### 字段要求

- 所有字段必须提供 getter/setter 方法
- 必须实现 `Serializable` 接口
- 必须提供无参构造函数
- 必须重写 `toString()` 方法（便于日志记录）

### 校验说明

**禁止在 Request/DTO 对象上使用 Bean Validation 注解**（`@NotNull`、`@NotBlank`、`@Length`、`@Valid` 等）。参数校验统一在用户接口层（starter）通过代码方式完成，详见 `ui_layer_standards.md` §3。

```java
@RpcStruct
public class CreateTaskRequest implements Serializable {
    
    @RpcField(1)
    private String taskCode;
    
    @RpcField(2)
    private String taskName;
    
    @RpcField(3)
    private String region;
}
```

## 禁止事项

- ❌ **不能包含业务逻辑**: API层只定义接口契约，不包含任何业务逻辑
- ❌ **不能直接调用下层服务**: API层只定义接口，实现在用户接口层
- ❌ **不能包含数据库操作**: 数据库操作属于基础设施层职责
- ❌ **不能包含外部服务调用**: 外部服务调用属于基础设施层职责
- ❌ **参数超过 3 个仍平铺**: 接口方法参数超过 3 个时必须用 Request/Param 对象封装（见 §3.1）
- ❌ **使用基本类型作为参数或 DTO 字段**: 禁止 `int`/`long`/`short`/`boolean`/`float`/`double`/`byte`/`char`，须使用
  `Integer`/`Long`/`Short`/`Boolean`/`Float`/`Double`/`Byte` 等包装类型（见 §3.2）

## 对外 API artifact 版本管理（REQUIRED）

当 REST / RPC 接口或 API 层 Request / Response / DTO / Enum 等对外契约发生字段或方法调整时，必须同步处理对外 API
artifact 的 Maven 版本。

> 本节中的"版本"指 Java API 包的 Maven artifact 版本，例如 `groupId:artifactId:1.2.3-SNAPSHOT`，不是 HTTP URL 中的 `/v2`。

### 必须升级 API artifact 版本的场景

- 新增 / 删除 / 重命名 Request、Response、DTO 字段
- 修改字段类型、字段含义、必填/选填语义、默认值、单位、取值范围
- 新增 / 删除 / 修改 REST 或 RPC 方法签名
- 修改枚举值、枚举语义或错误码语义
- 修改 `@RpcField` 编号、字段顺序、字段含义或序列化兼容性

### 版本升级要求

1. 设计文档和接口文档必须记录 API artifact 坐标、当前版本、目标版本、需要修改的 `pom.xml` 或 version property。
2. 目标版本必须来自用户确认、PRD、发布计划或项目规则；若缺失，必须先澄清，禁止自行猜测。
3. 目标版本通常应为 `*-SNAPSHOT`，用于让下游依赖新的接口契约进行联调。
4. 若版本由根 `pom.xml` 的 `<dependencyManagement>` 或 property 管理，必须更新统一版本来源；不得在子模块重复写散落版本。
5. 旧版本兼容、下游升级、灰度与回滚策略必须在接口文档或设计文档中说明。

### 不允许的做法

- ❌ 只改 API 字段，不升级 `*-api` / `*-client` artifact 版本
- ❌ 在没有用户或发布计划确认时自行编造 `*-SNAPSHOT` 版本号
- ❌ 把 artifact 版本升级误写成新增 `/v2` HTTP 路径
- ❌ 修改已发布 RPC 字段编号或复用已删除编号

## API 文档注解要求 (REQUIRED)

所有 API 接口（REST 和 RPC）以及 DTO 类必须按照 apidoc 规范添加完整的文档注解。

**关键要求**：

- ✅ 接口类必须有 `@InterfaceDoc` 注解
- ✅ 每个方法必须有 `@MethodDoc` 注解（包含完整的参数和返回值描述）
- ✅ 所有 DTO 类必须有 `@TypeDoc` 注解
- ✅ DTO 的每个字段必须有 `@FieldDoc` 注解
- ✅ 必须提供完整的 JSON 格式 `returnExample`

**详细规范参考**: [API 文档注解标准](@specrules/03_coding/api_documentation_standards.md)

## 相关文档

- [分层架构核心原则](@specrules/00_general/architecture/layered_architecture_core.md)
- [数据对象命名规范](@specrules/00_general/naming/data_object_naming.md)
- [用户接口层标准](@specrules/00_general/architecture/ui_layer_standards.md)
- [API 文档注解标准](@specrules/03_coding/api_documentation_standards.md)

---

## 版本与变更

- 1.2.0 (2026-04-14): 将版本管理明确为 Java 对外 API artifact 版本规则：API 字段或方法契约变更时必须定位 Maven
  坐标并升级到明确的目标 SNAPSHOT 版本，不再以 `/v2` 路径作为默认解释。
- 1.1.0 (2026-04-02): 补充 PageDTO 说明（BasePageResultDTO 内部结构，框架自动填充，开发者无需手动构造）；分页接口示例统一为
  BasePageResultDTO<T>；明确禁止 ResultWrapper<PageDTO<T>> 作为分页接口返回类型
- 1.0.0 (2025-02-06): 初始化版本与变更记录
