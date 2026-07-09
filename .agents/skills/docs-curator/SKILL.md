---
name: docs-curator
description: "系统知识库维护。将开发改动同步到 docs/ 目录，维护系统架构文档、业务模块子文档和 specIndex。在每次开发任务完成后由 dev-task-executor-agent 强制调用。"
version: "3.1.3"
---

# docs-curator

将每次开发改动持久化到 `docs/` 目录，确保系统文档与代码改动始终同步，信息不丢。

---

## docs/ 目录结构

```
docs/
├── modules.yml                # 模块注册表（单一事实来源）：模块定义、入口类/topic 映射
├── README.md                  # 系统架构总览（模块列表、顶层流程图、核心功能概述）
├── specIndex.md               # feature ↔ 业务模块 关联索引
└── {business-module}/
    ├── overview.md            # 模块职责、边界、对外依赖
    ├── flows.md               # 核心业务流程图（Mermaid）
    ├── data-model.md          # 数据模型、关键表结构
    └── interface.md           # 模块对外接口（REST/RPC 接口列表 + 请求/响应 JSON 示例）
                               # 由 docs-curator 在 task_done / feature_done 阶段维护
```

> ⚠️ **`docs/modules.yml` 是模块划分的单一事实来源**。所有项目级 docs 写入都必须从此文件查找模块归属，不得各自推断。首次使用前必须先执行 `operation: init` 完成初始化。

**各文档维护方**：

| 文档 | 写入方 | 时机 |
|------|--------|------|
| `README.md` | docs-curator | 新模块出现时追加模块条目 |
| `specIndex.md` | docs-curator | feature 完成时更新状态 |
| `overview.md` | docs-curator | 每次 task 完成，涉及模块职责/边界变更时 |
| `flows.md` | docs-curator | 每次 task 完成，涉及业务流程变更时 |
| `data-model.md` | docs-curator | 每次 task 完成，涉及数据模型变更时 |
| `interface.md` | docs-curator | 每次 task 完成或 feature 完成，涉及对外接口变更时 |

---

## 调用入参约定

调用方（dev-task-executor-agent 或 feature-dev-manager-agent）调用本 skill 时，必须提供：

| 字段 | 说明 |
|------|------|
| `feature` | 当前 feature 名称（如 `payment-refund`） |
| `task_id` | 当前任务 ID（如 `T-03`） |
| `changed_files` | 本次任务改动的文件列表 |
| `change_summary` | 1-3 句话描述本次改动了什么 |
| `operation` | 操作类型，见下方说明 |

`operation` 取值：
- `init`：项目文档初始化（首次使用时由用户手动触发，见下方 init 流程）
- `task_done`：单个任务完成后更新（由 dev-task-executor-agent 调用）
- `feature_done`：feature 完成后更新 specIndex（由 feature-dev-manager-agent 调用）

---

## 执行流程

### operation = `init`：项目文档初始化

> 首次使用时由用户手动触发。执行完成后，后续 `task_done` / `feature_done` 才能正常工作。

#### Init Step 1：扫描项目入口

扫描以下入口类型，收集候选模块信息：

| 入口类型 | 扫描目标 | 判断依据 |
|----------|---------|---------|
| HTTP 接口 | 类上有 `@RpcServerPublisher`，**或**类名匹配 `*RestApiServiceImpl` | 路径/类名含 `rest`，**或**方法上均无 `@RpcMethod` |
| RPC 接口 | 类上有 `@RpcServerPublisher`，**或**类名匹配 `*RpcServiceImpl` | 路径含 `rpc`，**或**存在至少一个方法带 `@RpcMethod` |
| MQ Consumer | `@MQConsumer` / `implements MessageListener` | topic: `order-paid` |
| MQ Producer | `@Autowired MQProducer` / `mqClient.send(` | topic: `refund-created` |
| 定时任务 | `@ScheduledTask` 注解的方法所在类 | `PaymentSettleJob` |

**HTTP vs Thrift 判断优先级**（同一个类可能同时满足多条规则，按优先级取最高匹配）：

1. 类路径含 `rpc`，**或**类名匹配 `*RpcServiceImpl` → **RPC 接口**
2. 类路径含 `rest`，**或**类名匹配 `*RestApiServiceImpl` → **HTTP 接口**
3. 类中存在至少一个方法带 `@RpcMethod` → **Thrift RPC**
4. 以上均不满足 → **HTTP**（兜底）

> 两种识别方式（注解 + 命名约定）取**并集**，互为补充，避免遗漏。
> 若同一个类既含 `@RpcMethod` 方法又含普通方法，仍归类为 Thrift RPC，但在展示时注明「含混合方法」。

对每个发现的入口，提取：
- 类名及识别来源（注解 / 命名约定 / 两者均有）
- 所在 Java 包路径
- 类中定义的方法名列表（接口方法），并标注哪些方法带有 `@RpcMethod`

若项目存在 `spec/` 目录，同时扫描已有 feature 列表作为辅助参考。

#### Init Step 2：生成模块划分建议

基于扫描结果，按**业务语义**对入口归类，生成建议的模块划分，展示给用户：

```
📋 扫描到以下入口，建议模块划分如下：

模块: payment（支付主流程）
  - [HTTP] PaymentRestApiServiceImpl  ← @RpcServerPublisher，路径含 gateway
      方法: createPayment, queryPayment, cancelPayment
  - [RPC] PaymentRpcServiceImpl  ← @RpcServerPublisher，路径含 thrift
      方法: getPaymentStatus (@RpcMethod)
  - [MQ Producer] topic: payment-created

模块: refund（退款流程）
  - [HTTP] RefundRestApiServiceImpl  ← @RpcServerPublisher，方法无 @RpcMethod
      方法: applyRefund, queryRefund
  - [MQ Consumer] topic: refund-callback
  - [Scheduled Job] RefundSettleJob
      方法: settleExpiredRefunds（cron: 0 2 * * *）

未归类入口（请告知归属模块）:
  - [RPC] CommonThriftServiceImpl  ← @RpcServerPublisher，含 @RpcMethod（混合方法）
      方法: healthCheck, getVersion (@RpcMethod)

是否需要调整？可以：
  1. 修改模块名称或描述
  2. 将入口移到其他模块
  3. 新增/合并/拆分模块
  4. 直接确认，不需要修改
```

**WAIT**：等待用户确认或提供修改意见，不得继续执行。

---

#### Init Step 3：按用户确认结果写入注册表

用户确认后，写入 `docs/modules.yml`：

```yaml
# docs/modules.yml
# 模块注册表 — 单一事实来源
# 由 docs-curator init 生成，后续模块变更请手动维护此文件
# docs-curator (task_done/feature_done) 和 apidoc-checker-agent 均从此文件查找模块归属

version: "1.0"
initialized_at: "yyyy-mm-dd"

modules:
  - id: payment
    desc: 支付主流程
    entrypoints:
      - type: rest
        class: PaymentRestApiServiceImpl
      - type: rpc
        class: PaymentRpcServiceImpl
      - type: mq
        topic: payment-created
        role: producer

  - id: refund
    desc: 退款流程
    entrypoints:
      - type: rest
        class: RefundRestApiServiceImpl
      - type: mq
        topic: refund-callback
        role: consumer
```

#### Init Step 4：创建 docs/ 骨架

按 modules.yml 中的模块列表，批量创建：
- `docs/README.md`（用模板初始化，填入模块列表）
- `docs/specIndex.md`（用模板初始化）
- 每个模块的 `docs/{module-id}/overview.md`、`flows.md`、`data-model.md`、`interface.md`（用模板初始化，填入模块描述）

#### Init Step 5：回填历史 spec（如有）

若 `spec/` 目录存在且有历史 feature，将其回填到 `docs/specIndex.md`：
- 扫描 `spec/*/STATUS.md`，提取 feature 名称和状态
- 按模块注册表推断每个 feature 的归属模块（见下方「模块查找规则」）
- 写入 specIndex，状态沿用 STATUS.md 中的值

#### Init Step 6：存量代码深读与内容回填（并行子 agent）

> 这一步是存量系统初始化的核心。骨架建好后，为每个模块启动独立子 agent，并行读取真实代码和知识库文档，生成有实质内容的文档，而不是留空模板。

**WAIT**：在开始 Step 6 之前，先询问用户：「是否现在进行存量代码深读（会为每个模块启动独立子 agent 并行分析，生成 overview/flows/data-model 内容）？如果项目较大可能耗时较长。」用户确认后再继续。

---

##### 6a. 准备子 agent 上下文

主 agent 在启动子 agent 之前，准备好每个模块的上下文包：

```
{
  module_id: "payment",
  module_desc: "支付主流程",
  entrypoints: [...],           // 来自 modules.yml
  docs_dir: "docs/payment/",   // 子 agent 写入目标
  date: "yyyy-mm-dd"
}
```

##### 6b. 并行启动子 agent

主 agent 使用 Agent 工具，**同时**为所有模块各启动一个子 agent，每个子 agent 负责完成一个模块的全部文档生成。

子 agent 任务描述模板：

```
你是 docs-curator 的模块深读子 agent，负责为模块 [{module_id}]（{module_desc}）生成完整文档。

## 任务

1. **定位模块代码范围**
   - 从以下入口类出发：{entrypoints}（含 REST/RPC/MQ Consumer/Scheduled Job）
   - 找到入口类源文件，从包路径推断模块根包
   - 递归收集该包下所有 Java 文件，按 DDD 分层归类（api/app/domain/infra）
   - 若有 Scheduled Job 入口，额外提取 `@ScheduledTask` 注解上的 cron 表达式和任务描述

2. **生成 overview.md**
   - 读取入口类、AppService 接口、DomainService 接口
   - 提取职责（从类名/方法名/JavaDoc 归纳）、边界（上下游模块）、对外依赖（MQ/RPC/DB）
   - 写入 {docs_dir}/overview.md（覆盖模板内容）

3. **生成 flows.md**
   - 读取 AppService 实现类和 DomainService 实现类
   - 识别核心业务方法（入口类暴露的方法 / 含业务动词 / 方法体超 20 行）
   - 对每个核心方法生成 Mermaid 时序图，从真实调用链还原，不猜测
   - 若知识库文档中有流程描述，对比代码后决定是否补充到 flows.md 中
   - 写入 {docs_dir}/flows.md（覆盖模板内容）

4. **生成 data-model.md**
   - 扫描 *Entity.java / *DO.java / *Mapper.java / *Repository.java / *.xml
   - 生成 Mermaid ER 图和核心表结构说明
   - 写入 {docs_dir}/data-model.md（覆盖模板内容）

5. **生成 interface.md**
   - 读取 Gateway / Thrift 入口类、Request / Response / DTO / Enum 定义、Controller/Service 实现、参数校验、Converter、枚举定义、业务常量、单元测试，以及相关 `@InterfaceDoc` / `@MethodDoc` / `@ParamDoc` / `@FieldDoc` / `@TypeDoc` 注解
   - 按接口方法生成接口记录，包含接口类型、类名、方法名、URL/HTTP Method（Gateway）、Thrift Service/Method（Thrift）、请求类型、返回类型、字段说明
   - 每个接口方法必须生成“请求 JSON 示例”和“响应 JSON 示例”；示例生成必须先结合真实代码语义，再把 `@FieldDoc(example=...)` / 接口文档 example 作为候选值校验，不得直接照抄
   - 示例值来源优先级：真实测试用例 / 调用方代码 / Controller 或 Thrift 实现入参校验与默认值 / Converter 字段映射 / Enum 与业务常量 / 历史真实接口文档 / 注解 example
   - 若注解 example 与字段类型、枚举值、校验规则、业务常量或测试用例不一致，必须以代码语义为准，并在接口记录中注明“注解 example 疑似不准”
   - 示例必须是合法 JSON，禁止使用 `"test"`、`"xxx"`、`0`、`1` 等无业务含义占位值；若真实示例无法从代码或文档推导，标注「待补充：缺少真实业务示例来源」并列出缺失字段
   - 写入 {docs_dir}/interface.md（覆盖模板内容）

## 约束
- 所有内容必须基于真实源文件，禁止凭方法名猜测；无法读出的逻辑标注「待补充」
- 每个文档末尾追加变更记录：`<!-- init | {date} -->`
- 完成后回报：「模块 {module_id} 完成，生成了 overview/flows/data-model/interface」

```

##### 6c. 主 agent 等待并汇总

主 agent 等待所有子 agent 完成，收集回报结果：
- 统计成功/失败的模块数
- 若某个子 agent 失败，记录原因并提示用户手动处理
- 更新 `docs/README.md` 的模块列表（从各模块 overview.md 提取职责第一句）

```markdown
## 模块列表

| 业务模块 | 职责概述 | 文档 |
|---------|---------|------|
| {module-id} | {overview.md 中的职责第一句} | [文档](docs/{module-id}/) |
```

向用户汇报：「存量代码深读完成：{N} 个模块成功，{M} 个模块待处理」

---

### operation = `task_done` / `feature_done`

#### Step 1：从 modules.yml 查找业务模块

> ⚠️ **必须从 `docs/modules.yml` 查找，禁止自行推断**。若 modules.yml 不存在，停止执行并提示用户先运行 `operation: init`。

**模块查找规则**（优先级从高到低）：

1. **按入口类名匹配**：从 `changed_files` 中找到 REST/RPC 实现类名，在 modules.yml 的 `entrypoints[].class` 中查找
2. **按 MQ topic 匹配**：从 `change_summary` 或 `changed_files` 中识别 topic，在 modules.yml 的 `entrypoints[].topic` 中查找
3. **按 feature 名称前缀匹配**：将 `feature` 前缀与 modules.yml 中的 `id` 做前缀匹配（如 `payment-refund` → `payment`）
4. **兜底**：使用 `general`，在文档中注明来源 feature，并提示用户更新 modules.yml

#### Step 2：确认 docs/ 目录结构存在

- 检查 `docs/modules.yml` 是否存在，不存在则停止并提示执行 init
- 检查 `docs/{business-module}/` 是否存在，不存在则创建并用模板初始化（说明：模块可能是 init 后新增的）
- 检查 `docs/README.md`、`docs/specIndex.md` 是否存在，不存在则用模板初始化

#### Step 3：按 operation 执行写入

##### operation = `task_done`

**3a. 更新模块子文档**

读取 `docs/{business-module}/` 下的模块子文档，判断本次改动涉及哪些维度：

| 改动类型 | 更新目标 |
|----------|---------|
| 新增/修改业务流程、时序、状态机 | `flows.md` |
| 新增/修改数据表、实体、ER 关系 | `data-model.md` |
| 新增/修改模块职责、对外依赖、接口边界 | `overview.md` |
| 新增/修改 Gateway、Thrift、Request/Response/DTO/Enum 等对外接口契约 | `interface.md` |

写入规则：
- **追加变更记录**，不覆盖已有内容
- 每条记录格式：`<!-- {date} | {feature} | {task_id} -->` 作为变更标记
- 若涉及 Mermaid 图更新，在原图下方追加新版本图，注明版本和来源
- 若是对已有内容的修正，在原内容旁用注释标注，并追加修正说明

**interface.md 专项写入要求（涉及对外接口契约变更时必须执行）**：
- 为每个新增/修改的 Gateway / Thrift 方法追加或更新一条接口记录，记录中必须包含接口类型、类名、方法名、请求类型、返回类型、字段说明和来源 feature/task
- Gateway 接口必须写 URL、HTTP Method、请求位置（path/query/body）；Thrift 接口必须写 Service、Method、Request Struct、Response Struct
- 每条接口记录必须包含“请求 JSON 示例”；有返回值时必须包含“响应 JSON 示例”
- 请求 JSON 示例必须覆盖本次新增/修改字段，且字段值应使用真实业务含义示例；生成示例时必须先读取实际代码语义，包括接口实现、参数校验、Converter、枚举、业务常量、调用方和单元测试
- `@FieldDoc(example=...)`、`@ParamDoc`、spec 接口文档中的 example 只能作为候选值，必须经过字段类型、校验规则、枚举合法值、默认值和业务常量校验；发现不一致时以代码为准，并记录“注解 example 疑似不准”
- 禁止使用 `"test"`、`"xxx"`、`"todo"`、`0`、`1` 等泛化占位值充当示例；无法确定真实示例时，示例块内保留字段结构并在下方写明「待补充真实示例来源」
- 若本次任务只改 DTO/Enum 字段而没有新增方法，也必须更新受影响方法的请求/响应 JSON 示例，确保下游能直接复制联调

**3b. 更新 docs/README.md 模块列表**

- 若推断出的业务模块是新模块（`docs/{business-module}/` 之前不存在），在 `docs/README.md` 的模块列表中追加该模块条目

##### operation = `feature_done`

**3b-1. 更新 docs/specIndex.md**

在 specIndex 中追加或更新该 feature 的记录：

```markdown
| {feature} | {business-module} | 已完成 | spec/{feature}/ | {date} |
```

若该 feature 已存在记录（开发中状态），将状态更新为「已完成」，更新日期。

---

## 文档模板

### docs/modules.yml 模板

```yaml
# docs/modules.yml
# 模块注册表 — 单一事实来源
# 由 docs-curator init 生成，后续模块变更请手动维护此文件

version: "1.0"
initialized_at: "yyyy-mm-dd"
doc_sync: false               # true 表示启用文档同步，init 时由用户选择

modules:
  - id: {module-id}
    desc: {模块职责一句话描述}
    doc_url: ""               # 文档链接（可选，init 时由用户提供或 init 自动创建后写入）
    entrypoints:
      - type: rest          # rest（HTTP）| rpc（RPC）| mq | scheduled
        class: {ClassName}     # @RpcServerPublisher 实现类名（不含包路径）
      - type: mq
        topic: {topic-name}
        role: producer         # producer | consumer
      - type: scheduled
        class: {JobClassName}  # @ScheduledTask 注解所在类名
        cron: "{cron表达式}"   # 从 @ScheduledTask 注解中提取
```

### docs/README.md 初始模板

```markdown
# 系统架构总览

## 模块列表

| 业务模块 | 职责概述 | 文档 |
|---------|---------|------|
| （由 docs-curator 自动维护） | | |

## 核心功能

<!-- 由 docs-curator 根据各模块 overview.md 汇总 -->

## 顶层流程图

<!-- 由 docs-curator 在模块增加时更新 -->
```

### docs/specIndex.md 初始模板

```markdown
# Spec Index — Feature ↔ 业务模块关联

| Feature | 业务模块 | 状态 | 设计文档路径 | 最后更新 |
|---------|---------|------|------------|---------|
```

### docs/{module}/overview.md 初始模板

```markdown
# {module} 模块概述

## 职责

<!-- 本模块负责什么 -->

## 边界

<!-- 本模块不负责什么，与哪些模块有交互 -->

## 对外依赖

<!-- 依赖的外部服务、中间件 -->

## 变更记录

<!-- 由 docs-curator 自动追加 -->
```

### docs/{module}/flows.md 初始模板

```markdown
# {module} 核心业务流程

<!-- 由 docs-curator 根据各 feature 改动自动追加 -->
```

### docs/{module}/data-model.md 初始模板

```markdown
# {module} 数据模型

<!-- 由 docs-curator 根据各 feature 改动自动追加 -->
```

### docs/{module}/interface.md 初始模板

````markdown
# {module} 对外接口

<!-- 由 docs-curator 在 task_done / feature_done 阶段维护。每条接口记录必须带来源 feature/task 和可复制的请求 JSON 示例。 -->

## Gateway 接口

### {GatewayClass}.{methodName}
<!-- {date} | {feature} | {task_id} -->

| 字段 | 内容 |
|------|------|
| 接口类型 | Gateway / HTTP |
| URL | {method + path，如 POST /api/order/create} |
| Java 方法 | `{GatewayClass}#{methodName}({RequestType})` |
| 请求类型 | `{RequestType}` |
| 返回类型 | `{ResponseType}` |
| API artifact | `{groupId}:{artifactId}:{version}`（如适用） |
| 说明 | {接口用途、调用方、兼容性说明} |

#### 请求字段

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| {field} | {type} | 是/否 | {description} | {business example} |

#### 请求 JSON 示例

```json
{
  "region": "SA",
  "orderId": 1234567890123
}
```

#### 响应 JSON 示例

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

## Thrift 接口

### {ThriftService}.{methodName}
<!-- {date} | {feature} | {task_id} -->

| 字段 | 内容 |
|------|------|
| 接口类型 | Thrift RPC |
| Service | `{ThriftService}` |
| Method | `{methodName}` |
| Request Struct | `{RequestType}` |
| Response Struct | `{ResponseType}` |
| API artifact | `{groupId}:{artifactId}:{version}`（如适用） |
| 说明 | {接口用途、调用方、兼容性说明} |

#### 请求字段

| 字段 | 类型 | ThriftField | 必填 | 说明 | 示例 |
|------|------|-------------|------|------|------|
| {field} | {type} | {index} | 是/否 | {description} | {business example} |

#### 请求 JSON 示例

```json
{
  "region": "SA",
  "orderId": 1234567890123
}
```

#### 响应 JSON 示例

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

## 变更记录

<!-- 由 docs-curator 自动追加 -->
````

---

## 硬约束

1. **只追加，不删除**：已有文档内容不得删除，只能追加或在原内容旁注释修正
2. **必须标注来源**：每次写入都要带 feature 名称和 task_id，确保可溯源
3. **不得跳过**：即使 change_summary 很简短，也必须执行写入
4. **模块查找必须走 modules.yml**：`task_done` / `feature_done` 时禁止自行推断模块，必须从 `docs/modules.yml` 查找；若文件不存在，停止并提示用户执行 init
5. **init 必须等用户确认后才写文件**：Init Step 2 展示建议后必须 WAIT；Step 2.5 收集链接后必须 WAIT；Step 2.7 同步配置后必须 WAIT，不得自动写入
6. **Step 6 必须基于真实代码**：生成 flows/data-model/overview/interface 内容时，子 agent 必须读取实际源文件，禁止凭方法名或注解 example 猜测流程/示例；若某段逻辑无法从代码读出，明确标注「待补充」
10. **interface.md 必须有请求示例**：涉及对外接口契约变更时，`interface.md` 中每个受影响方法都必须包含合法 JSON 请求示例；示例必须结合实际代码生成，不得只参考注解 example；无法确认真实示例时必须标注缺失来源，不得用泛化占位值糊弄

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本

