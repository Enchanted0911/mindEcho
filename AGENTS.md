# Agent 行为规范

本文件是 OpenCode 在本项目中的**项目级行为索引**：定义什么时候触发哪个 Agent、核心产物契约是什么、哪些检查点不可跳过。

> 在 OpenCode 中，若当前 session 主 agent 为 `feature-dev-manager-agent`，则它是 feature / fix / 设计 / 任务规划 / 变更 / 开发执行 / 完成流程的**唯一运行时编排入口**。
> 本文件负责说明“项目希望发生什么”；`feature-dev-manager-agent` 负责说明“运行时具体怎么一步步执行”。
> 若两者在**步骤编排**上冲突，以 `feature-dev-manager-agent` 为准；若涉及业务事实、代码现状、接口定义，以仓库实际文件为准。
> `spec/template/STATUS-TEMPLATE.md` 是 `STATUS.md` 的模板来源；feature manager 创建或修复 `STATUS.md` 时应优先读取该文件。
> `spec/template/FEATURE-TEMPLATE.md` 是 `FEATURE.md` 的模板来源；`prd-analyzer-agent` 负责沉淀 feature 原始需求、PRD 理解摘要、待确定内容和澄清问题。
> **任何规模进入代码修改前都必须人工确认**；标准流程还必须在设计文档与自动审查通过后执行方案人工确认。

---

## 一、意图识别与自动触发

收到用户请求后，**优先**按以下规则识别意图，自动触发对应路径，无需用户手动指定：

| 用户描述包含 | 识别意图 | 触发路径 |
|------------|---------|---------|
| 「需求」「feature」「做一个」「新增功能」「设计方案」 | 中/大任务 | → 标准流程（见第二节） |
| 「修复」「fix」「改一下」「小调整」「加个字段」「改个配置」 | 小任务 | → 快速路径（见第三节） |
| 「修改」「调整」「不满意」「重新做」「回退」「变更」 | 变更请求 | → 变更请求流程（见第六节） |
| 「查看现状」「影响分析」「改动范围」「哪些地方会受影响」 | 独立分析 | → 直接调用 `context-analyzer-agent` |
| 「查架构」「当前模块」「系统结构」「README」 | 架构查阅 | → 直接调用 `readme-architecture-curator` |
| 「规范」「specrules」「架构不清楚」「文档」 | 规范查询 | → 直接读取 specrules/ 相关文档 |

---

## 二、标准流程（中/大任务）

适用条件：有接口变更 / 有新表 / 跨模块 / 需要设计文档。

> 下面描述的是**项目级预期流程与检查点**。若当前 Claude Code 会话已经以 `feature-dev-manager-agent` 作为主 agent 运行，则具体的运行时推进、断点恢复和 subagent 调度以该 agent 为准。

```
用户请求
    ↓
【Step 1】Feature 检测
  扫描 spec/ 目录，检查进行中的 feature
  ├── 有进行中 feature → 询问用户：归入哪个 feature？还是新建？
  └── 无 → 引导用户提供 feature 名称（英文小写+中划线）
    ↓
【Step 2】规范加载（强制，不可跳过）
  读取 specrules/rules/index.md
  - §1 前置基础层（必加载）：宪章 / 项目结构 / 分层架构 / 数据对象命名
  - §2 设计阶段规则（设计类任务追加）
  - §3 开发阶段规则（开发类任务追加）
  - §4 横切专题（枚举/接口/对象转换/测试，按需追加）
  - project-rules/project-rules.md（若存在，追加加载；不存在则跳过）
    ↓
【Step 3】PRD 理解与检查（所有规模必做）
  调用 prd-analyzer-agent
  输入：用户原始需求 / PRD / 参考资料
  输出：spec/{feature}/FEATURE.md
  ⚠️ 有 PRD 待确定内容、需求冲突或必须澄清问题 → 先澄清，不得进入设计或开发
    ↓
【Step 4】上下文分析（涉及已有模块时触发）
  调用 context-analyzer-agent
  输入：FEATURE.md + docs/ + 历史 spec
  读取：docs/{module}/overview.md / flows.md / data-model.md / interface.md
  读取：历史 spec/{feature}/[设计文档].md
  输出：spec/{feature}/context-report.md
  ⚠️ 全新独立模块可跳过此步
    ↓
【Step 5】技术方案设计
  调用 tech-design-architect-agent
  输入：FEATURE.md + context-report.md + 技术方案模板
  输出：spec/{feature}/[设计文档].md / [接口文档].md / [类图].md
  └── 需要图表时调用 mermaid_chart_generator skill
    ↓
【Step 6】设计文档审查
  调用 design-doc-reviewer-agent
  输入：[设计文档].md
  输出：spec/{feature}/review-design.md
  ⚠️ 不通过 → 返回技术方案设计修改，通过后继续
    ↓
【Step 7】接口文档检查
  调用 apidoc-checker-agent
  输入：[接口文档].md + feature 名称
  输出：spec/{feature}/review-apidoc.md
  仅生成审查报告，不在设计阶段写入 docs/
  ⚠️ 不通过 → 返回技术方案设计修改，通过后继续
    ↓
【Step 8】方案人工确认 Gate（强制，不可跳过）
  设计文档 + 接口文档 + 类图文档 + 自动审查全部通过后，必须等待用户明确确认方案
  ⚠️ 未确认不得进入任务拆解
    ↓
【Step 9】任务拆解
  调用 design-to-tasks-agent
  输入：三类设计文档（设计文档 / 接口文档 / 类图）
  输出：spec/{feature}/plan.md + task.md（初始状态全为「待执行」）
  └── 需要对外排期时可选调用 work_item_splitter skill
    ↓
【Step 10】开发执行确认 Gate（强制，不可跳过）
  向用户展示 plan.md / task.md 摘要、首个任务、影响范围和验证方式
  ⚠️ 未确认不得调用 dev-task-executor-agent
    ↓
【Step 11】开发执行循环（每个 Task 单独执行，无前置依赖的 Task 可并行）
  调用 dev-task-executor-agent
  输入（每次只传单条 Task）：
    feature / task_id / task_desc / spec_context / check_standard
  ⚠️ 禁止一次传入多条任务或整个 task.md
    ↓
  【Step 11a】架构审查（每个 Task 完成后必做）
    通常由 feature-dev-manager-agent 在收到 dev-task-executor-agent 回报后统一编排
    调用 architecture-reviewer-agent
    输入：changed_files（必须明确传入，禁止扫全库）
    输出：spec/{feature}/review-arch-{task_id}.md
    ⚠️ 不通过 → 返回修改 → 重新审查，循环直到通过
    ↓
  【Step 11b】知识库写入（每个 Task 完成后强制）
    调用 docs-curator skill（operation: task_done）
    写入：docs/{module}/flows.md / data-model.md / overview.md / interface.md（按改动类型；interface.md 必须基于真实代码生成请求 JSON 示例）
    ⚠️ 禁止以任何理由跳过
    ↓
  继续下一个 Task，直到所有 Task 完成
    ↓
【Step 12】Feature 完成流程（用户明确触发）
  ① readme-architecture-curator skill → 更新 README.md 架构文档
  ② docs-curator skill（operation: feature_done）→ 更新 docs/specIndex.md
  ③ spec-version-changelog skill → 记录版本与变更日志【必须】
  ④ doc_repository_generator skill → 归档到 Document Repository【可选】
```

---

## 三、快速路径（小任务）

**满足以下全部条件时**，跳过设计阶段，直接执行：

| 条件 | 说明 |
|------|------|
| ✅ 单文件或单方法改动 | 改动范围不超过 1-2 个文件 |
| ✅ 无接口新增/变更 | 不涉及 REST/RPC 接口 |
| ✅ 无新表/新字段 | 不涉及数据库结构变更 |
| ✅ 无跨模块影响 | 不影响其他业务模块行为 |

```
用户描述小改动
    ↓
【Step 1】Feature 检测（同标准流程）
    ↓
【Step 2】PRD 理解与检查（同标准流程，不可跳过）
  调用 prd-analyzer-agent 产出 FEATURE.md
    ↓
【Step 3】开发执行确认 Gate（强制，不可跳过）
  展示拟执行的单条任务、预计修改范围、验证方式和回滚建议
  ⚠️ 未确认不得调用 dev-task-executor-agent
    ↓
【Step 4】调用 dev-task-executor-agent
  输入：feature + task_id + task_desc + changed_files预估
    ↓
【Step 5】架构审查（必须，不可跳过）
  调用 architecture-reviewer-agent，传入 changed_files
  输出：spec/{feature}/review-arch-{task_id}.md
    ↓
【Step 6】知识库写入（必须，不可跳过）
  调用 docs-curator skill（operation: task_done）
    ↓
完成
```

**跳过**：context-analyzer / tech-design-architect / design-doc-reviewer / apidoc-checker / design-to-tasks / 方案人工确认 Gate

**不可跳过**：PRD 理解与检查 + 开发执行确认 + 规范加载 + 架构审查 + docs-curator 写入

> ⚠️ 执行中发现改动超出小任务范围（如需要新增接口），必须停止，升级为标准流程。

---

## 四、强制规则汇总

### 规范加载（任何设计/开发任务前）

```
读取 specrules/rules/index.md
  → §1 前置基础层（必加载）
  → §2 设计阶段规则（设计类任务）或 §3 开发阶段规则（开发类任务）
  → §4 横切专题（按需）
  → project-rules/project-rules.md（若存在，必加载，项目级规范补充）
未完成规范加载，不得开始设计或编码。
```

### PRD 理解与检查（任何规模）

```
feature 启动
  → prd-analyzer-agent
  → FEATURE.md
  → 若有 PRD 待确定内容 / 需求冲突 / 必须澄清问题，先澄清
```

未生成 `FEATURE.md`，不得开始上下文分析、设计或开发。

### 人工确认 Gate（任何规模）

```
标准流程：设计文档 + 自动审查通过 → 方案人工确认 → 任务拆解 → 开发执行确认 → 开发执行
快速路径：FEATURE.md → 开发执行确认 → 开发执行
```

未获得用户明确确认，不得调用 `dev-task-executor-agent`。

### 每个 Task 完成后（开发阶段）

```
架构审查通过
  → docs-curator（task_done）写入 docs/{module}/
两步均不可跳过。
```

### Feature 完成后

```
readme-architecture-curator → docs-curator（feature_done）→ spec-version-changelog
三步依次执行，spec-version-changelog 必须调用。
```

### 规范文件变更后

```
specrules/ 下文件变更
  → rules-curator（必须先执行）
  → spec-version-changelog（必须紧接执行）
```

### 审查产物落文件约定

| 审查类型 | 产物路径 |
|---------|---------|
| 设计文档审查 | `spec/{feature}/review-design.md` |
| 接口文档检查 | `spec/{feature}/review-apidoc.md` |
| 架构审查 | `spec/{feature}/review-arch-{task_id}.md` |

---

## 六、变更请求流程

当用户发起「修改」「调整」「不满意」「重新做」「回退」「变更」等请求时，**必须立即停止当前步骤**，进入变更请求流程：

```
用户变更描述
    ↓
【Step 1】识别变更类型
  根据用户描述自动判断变更类型：
  ├── 「补充」「加个字段」「加个参数」「补个边界条件」 → clarification（信息补充）
  │     → 当前步骤继续，记录到 question-clarification.md 后修正文档
  ├── 「实现方式不对」「代码结构不好」「这个写法不满意」 → implementation-change（实现变更）
  │     → 回退到 S5（开发执行）
  ├── 「任务拆得不对」「拆得太粗/太细」 → task-change（任务变更）
  │     → 回退到 S4（任务规划）
  ├── 「设计不对」「接口设计有问题」「状态流转不对」 → design-change（设计变更）
  │     → 回退到 S2.1（设计文档）
  ├── 「上下文理解错了」「影响范围不对」 → context-change（上下文认知变更）
  │     → 回退到 S2.0（上下文分析）
  └── 「需求变了」「加个新功能」「PRD 变了」 → scope-change（范围变更）
        → 回退到 S1.1（PRD 理解与检查），重新评估规模和流水线
    ↓
【Step 2】执行回退
  - 先调用 change-impact-analyzer-agent 读取当前 feature 产物并更新 CHANGELOG.md
  - 由 CHANGELOG.md 给出建议 agent、建议回退步骤、受影响内容
  - 再将 STATUS.md 中目标步骤及之后所有步骤状态重置为 pending
  - 回退到 PRD 理解：调用 prd-analyzer-agent 重新更新 FEATURE.md
  - 回退到设计阶段：调用 tech-design-architect-agent 重新产出三类文档
  - 回退到任务规划：调用 design-to-tasks-agent 重新拆解任务
  - 回退到开发执行：从 task.md 中重新选取任务执行
    ↓
【Step 3】变更记录
  在 spec/{feature}/CHANGELOG.md 中记录：
  - 变更类型（clarification/implementation-change/...）
  - 建议 agent
  - 建议回退步骤
  - 变更原因（用户描述）
  - 受影响内容（分析结果）
  - agent 反馈（执行后回写）
    ↓
【Step 4】强制重新审查
  - 回退到 PRD 理解后：必须重新处理待确定内容和澄清问题
  - 回退到设计阶段后：必须重新执行 S2.1-R、S2.2-R 审查
  - 回退到设计阶段后：必须重新执行方案人工确认 Gate
  - 回退到任务规划后：必须重新确认 plan.md 和 task.md，并重新执行开发执行确认 Gate
  - 回退到开发执行后：必须重新通过架构审查；若执行计划变化，必须重新执行开发执行确认 Gate
    ↓
继续执行回退后的步骤
```

> ⚠️ **变更优先原则**：用户发起变更请求时，**必须立即停止当前步骤**，先执行变更类型识别和回退，不得在完成当前步骤后才处理。

---

## 七、文档产物结构

```
spec/
├── template/
│   ├── FEATURE-TEMPLATE.md    ← Feature 语义模板
│   └── STATUS-TEMPLATE.md     ← 状态模板
└── {feature}/
    ├── FEATURE.md                 ← prd-analyzer-agent
    ├── context-report.md          ← context-analyzer-agent
    ├── [设计文档]{feature}.md     ← tech-design-architect-agent
    ├── [接口文档]{feature}.md     ← tech-design-architect-agent
    ├── [类图]{feature}.md         ← tech-design-architect-agent
    ├── review-design.md           ← design-doc-reviewer-agent
    ├── review-apidoc.md           ← apidoc-checker-agent
    ├── plan.md                    ← design-to-tasks-agent
    ├── task.md                    ← design-to-tasks-agent（dev-task-executor-agent 更新状态）
    ├── review-arch-{task_id}.md   ← architecture-reviewer-agent（每个 Task 一个）
    ├── STATUS.md                  ← feature-dev-manager-agent
    ├── CHANGELOG.md               ← change-impact-analyzer-agent（分析）/ feature-dev-manager-agent（结果回写）
    └── question-clarification.md  ← feature-dev-manager-agent

docs/
├── README.md                  ← readme-architecture-curator（系统架构总览）
├── specIndex.md               ← docs-curator（feature↔模块索引）
└── {module}/
    ├── overview.md            ← docs-curator
    ├── flows.md               ← docs-curator
    ├── data-model.md           ← docs-curator
    └── interface.md           ← docs-curator（task_done / feature_done 阶段维护，含基于真实代码的请求/响应 JSON 示例）
```

---

## Sub-Agent Delegation
When a task involves implementation work AND a dedicated sub-agent exists for that work (e.g., dev-task-executor-agent), delegate to that sub-agent only after the required human confirmation Gate is complete. Do NOT perform implementation directly in the main thread. If you are unsure which agent to delegate to, ask before proceeding.

## Docs-Curator Closure Step
When closing out ANY feature branch, the docs-curator closure step is MANDATORY. Run docs-curator before the final commit/push. Do not skip this step even if other closure tasks (tests, reviews) appear complete.

## 八、Agent / Skill 索引

### Agent（自主决策，可调用其他 Agent/Skill）

| Agent | 职责 | 触发方 |
|-------|------|--------|
| `feature-dev-manager-agent` | 全局统筹入口，编排 feature 全生命周期，判断小任务/标准流程 | 用户请求 / AGENTS.md 意图识别 |
| `prd-analyzer-agent` | PRD 理解与检查，输出 FEATURE.md，沉淀原始需求、摘要、待确定内容、澄清问题和冲突 | feature-dev-manager-agent（所有规模 S1.1） |
| `context-analyzer-agent` | 扫描已有模块文档，分析改动影响，输出 context-report.md | feature-dev-manager-agent（涉及已有模块时） |
| `change-impact-analyzer-agent` | 读取 feature 产物，分析澄清/变更影响范围，更新 CHANGELOG.md 并给出建议 agent | feature-dev-manager-agent（澄清/变更/回写需求时） |
| `tech-design-architect-agent` | 分阶段输出设计文档/接口文档/类图，基于 context-report.md | feature-dev-manager-agent |
| `design-doc-reviewer-agent` | 设计文档合规审查，产物写入 review-design.md | feature-dev-manager-agent |
| `apidoc-checker-agent` | 接口文档检查，输出 review-apidoc.md；不直接写入 docs/ | feature-dev-manager-agent |
| `design-to-tasks-agent` | 设计文档 → plan.md + task.md，只拆解不执行 | feature-dev-manager-agent |
| `dev-task-executor-agent` | 每次执行单条 Task，完成实现、本地自检、docs/ 同步并回报结构化结果 | feature-dev-manager-agent |
| `architecture-reviewer-agent` | 四层 DDD 架构审查，必须传入 changed_files | feature-dev-manager-agent（开发实现完成后统一编排） |
| `unit-test-agent` | 业务类单元测试编写与验证 | feature-dev-manager-agent（架构审查通过后） |

### Skill（原子操作，被 Agent 调用）

| Skill | 职责 | 调用方 |
|-------|------|--------|
| `docs-curator` | 写入 docs/{module}/ 知识库 | dev-task-executor-agent（task_done 强制）/ feature-dev-manager-agent（feature_done） |
| `mermaid_chart_generator` | 生成 Mermaid 图表 | tech-design-architect-agent |
| `readme-architecture-curator` | 更新 README.md 架构文档 | feature-dev-manager-agent（完成流程） |
| `spec-version-changelog` | 版本与变更日志记录【必须】 | feature-dev-manager-agent（完成流程） |
| `rules-curator` | specrules 索引刷新 | feature-dev-manager-agent（rules 变更时） |
| `object-conversion-checker` | DTO/BO/DO 转换检查 | dev-task-executor-agent（按需） |
| `enum_design_agent` | 枚举设计与实现 | dev-task-executor-agent（按需） |
| `work_item_splitter` | PRD→工作项清单 | feature-dev-manager-agent（可选） |

---

