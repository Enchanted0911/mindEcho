# {Feature} 状态

**状态**: 开发中 | 已完成
**规模**: small | large | large-no-context
**分支**: feature/{feature} | {当前分支名（未创建独立分支时）}
**创建时间**: yyyy-mm-dd
**最后更新**: yyyy-mm-dd

## 流水线

| 步骤 | 名称 | Agent/Skill | 依赖 | 状态 | 备注 |
|------|------|-------------|------|------|------|
| S1   | 需求建立 | feature-dev-manager-agent | - | pending | |
| S1.1 | PRD 理解与检查 | prd-analyzer-agent | S1 | pending | 产出 FEATURE.md |
| S2.0 | 上下文分析 | context-analyzer-agent | S1.1 | pending | |
| S2.1 | 设计文档 | tech-design-architect-agent | S1.1,S2.0 | pending | |
| S2.2 | 接口文档 | tech-design-architect-agent | S1.1,S2.0 | pending | 与S2.1同步产出 |
| S2.3 | 类图文档 | tech-design-architect-agent | S1.1,S2.0 | pending | 与S2.1同步产出 |
| S2.1-R | 设计文档审查 | design-doc-reviewer-agent | S2.1,S2.2,S2.3 | pending | |
| S2.2-R | 接口文档审查 | apidoc-checker-agent | S2.1,S2.2,S2.3 | pending | |
| S2.3-R | 类图文档审查 | architecture-reviewer-agent | S2.1,S2.2,S2.3 | pending | 可选，复杂领域模型时启用 |
| S2-H | 方案人工确认 | feature-dev-manager-agent | S2.1-R,S2.2-R | pending | 必须人工确认后才能进入 S4 |
| S4   | 任务规划 | design-to-tasks-agent | S2-H | pending | |
| S5-H | 开发执行确认 | feature-dev-manager-agent | S4 或 S1.1(small) | pending | 必须人工确认后才能进入 S5 |
| S5   | 开发执行 | dev-task-executor-agent | S5-H | pending | |

## 进度概要

- 已完成: 无
- 当前断点: S1（需求建立）
- 剩余步骤: S1 → S1.1 → S2.0 → S2.1 → S2.1-R → S2.2 → S2.2-R → S2.3 → S2.3-R → S2-H → S4 → S5-H → S5

## 状态值说明

- `pending`：未开始
- `in-progress`：执行中
- `done`：已完成
- `blocked`：被阻塞
- `waiting-confirmation`：等待人工确认
- `skipped`：已跳过
