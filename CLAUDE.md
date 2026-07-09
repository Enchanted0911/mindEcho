@AGENTS.md

# Claude Code 项目配置

本文件是 Claude Code 在本项目中的运行配置。

上面的 `@AGENTS.md` 会把项目级行为规则一起加载进 Claude Code。

若当前 session 以 `feature-dev-manager-agent` 作为主 agent 运行，则 feature / fix / 设计整理 / 任务规划 / 变更请求 / 开发执行 / 完成 feature 统一以该 agent 的步骤编排为准；本文件只补充项目级的技术约定。

---

## 关键文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| **行为规范** | `AGENTS.md` | Agent 触发规则、调用顺序、强制约束——从这里开始 |
| 全局宪章 | `specrules/constitution.md` | 最高开发原则，任何任务前必读 |
| 规范索引 | `specrules/rules/index.md` | 规范统一入口（§1前置→§2设计/§3开发→§4横切） |
| **项目级规范** | `project-rules/project-rules.md` | 本项目特有规范（命名特例、框架约定），补充全局 specrules/，由项目团队维护 |
| 系统架构 | `README.md` | 模块列表、架构说明 |
| 系统知识库 | `docs/` | 业务模块文档（overview/flows/data-model/interface） |
| Feature 设计文档 | `spec/{feature}/` | 设计文档、审查产物、任务列表 |
| Feature 语义锚点 | `spec/{feature}/FEATURE.md` | 原始需求、PRD 摘要、待确定内容、澄清问题、人工确认记录 |

---

## 代码规范

### 架构约束（NON-NEGOTIABLE）

- 严格遵循四层 DDD：API → UI → App → Domain → Infrastructure
- 禁止跨层调用（App 层不得直接调用 DAO）
- Domain 层禁止依赖本工程 API 模块
- 外部服务调用类必须以 `Proxy` 结尾

### 数据对象命名

| 层 | 命名 |
|----|------|
| API 层 | `*Request` / `*Response` / `*DTO` |
| UI 层 | `*VO` |
| App 层 | `*BO` |
| Domain 层 | `*DO` / `*QueryDO` |
| Infra 层 | `*Entity` |
| 集合 | `xxxList`（禁止 `xxxs` / `xxxes`） |

### API 层强制约束

- 返回类型必须为 `BaseResultDTO<T>` 或 `BasePageResultDTO<T>`
- 禁止 `BaseResultDTO<Void>`，void 语义用 `BaseResultDTO<Boolean>`
- 参数超过 3 个必须封装为 Request 对象
- 禁止基本类型参数（使用 `Integer`/`Long`/`Boolean`）

### 通用规范

- 禁止 `Map<String, Object>` 构建数据结构
- 所有领域对象使用 Lombok `@Data`、`@Builder`
- 所有 public 类和方法必须有 JavaDoc
- 单文件 ≤ 500 行

---

## 常用命令

```bash
# 编译
mvn compile -Dcheckstyle.skip=true

# 运行测试
mvn test -Dtest=TestClassName

# 清理构建
mvn clean install -DskipTests

# Git 提交
git add <files>
git commit -m "feat: 描述" -m "Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## 注意事项

1. **不要猜测**：不确定时用 `Read` 工具查看实际代码
2. **不要过度设计**：只实现当前需求
3. **不要跳过测试**：所有新功能必须有单元测试
4. **API 变更需确认**：接口变更前需与团队确认兼容性

---

## 项目级规范（补充加载）

若项目根目录存在 `project-rules/` 目录，其中的规范文件会在全局规范加载完成后**追加加载**：

| 文件 | 说明 |
|------|------|
| `project-rules/project-rules.md` | 项目级规范主文件（命名特例、框架约定、禁止项等） |

加载时机：所有 agent 在完成 `specrules/rules/index.md` 的四层加载后，自动检查并读取此文件。若文件不存在则跳过，不报错，向后兼容。

> `project-rules/` 目录由 `sailor-transaction-devkit` 在首次 apply 时自动创建骨架，内容由项目团队填写维护，**不会被 devkit 同步覆盖**。

---

## 规范文件健康检测

**每次任务开始前**，检查 `specrules/rules/index.md` 是否可读：

- 可读 → 正常继续
- 不可读 → **禁止开始任何设计或开发任务**，向用户说明：

> ⚠️ 规范文件 `specrules/rules/index.md` 不可读。
> - **Copy 模式**：请重新执行 `/sailor-transaction-devkit` 同步规范
> - **Submodule 模式**：请执行 `git submodule update --init specrules`

---


## 版本历史

- **1.7.2** (2026-04-14): 关键文档索引新增 `FEATURE.md`，作为 feature 原始需求、PRD 摘要、待确定内容、澄清问题与人工确认记录的语义锚点。
- **1.7.1** (2026-04-09): 顶部增加 `@AGENTS.md` 导入；明确当 session 绑定 `feature-dev-manager-agent` 时，feature/fix/设计/任务/变更/完成流程以该 agent 的步骤编排为准。
- **1.7.0** (2026-03-25): 删除 Submodule 健康检测（基于 devkit/）和规范文件路径章节（两种模式路径对比）；规范路径统一为 `specrules/rules/index.md`；新增简化版规范文件健康检测。
- **1.6.0** (2026-03-25): Submodule 健康检测改为以 `rules/index.md` 为检测锚点。
- **1.5.0** (2026-03-25): 新增「项目级规范（补充加载）」章节，说明 project-rules/project-rules.md 的加载时机和维护方式；关键文档索引新增项目级规范条目。
- **1.3.0** (2026-03-19): 重构为轻量配置文件，行为规范统一迁移到 AGENTS.md，本文件只保留技术约定和命令。
- **1.2.0** (2026-03-18): 新增 docs-curator skill 说明。
- **1.1.1** (2026-03-17): 清理 skill 路径说明。
- **1.1.0** (2026-03-13): 对齐阶段化规范入口。
- **1.0.0** (2026-03-04): 初始版本。
