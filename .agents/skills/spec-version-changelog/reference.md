# 规范与 Agent 版本与变更 — 格式与模板

## specrules/rules/index.md 中新增章节

在 `specrules/rules/index.md` 中新增（或更新）以下章节，建议放在「§ 5. 使用说明」之前或「§ 6. Cursor 规则约定」之后，保持与现有章节编号一致（若插入为新 § 则后续章节顺延）。

```markdown
---

## § X. 规范与 Agent 版本与变更

- **索引版本**：`x.y.z`（随任意范围变更递增）
- **最后更新**：YYYY-MM-DD

### 各范围统计与版本

| 范围 | 路径 | 文件数 | 当前版本 |
|------|------|--------|----------|
| 规范（specrules） | `specrules/` | N | x.y.z |
| Agent 约束 | `AGENTS.md` | 1 | x.y.z |
| Skills | `skill/skills/` | N | x.y.z |
| Agents | `agent/agents/` | N | x.y.z |

### 变更日志（倒序）

- **x.y.z** (YYYY-MM-DD): 本次变更简述（如：rules-curator 刷新索引；specrules 增 api_layer_standards 修订）
- **x.y.z-1** (YYYY-MM-DD): …
```

## specrules 规范文件

### Frontmatter 增加 version

```yaml
---
description: "…"
alwaysApply: false
globs: ["…"]
version: "1.0.0"
---
```

### 文末「版本与变更」小节

```markdown
---

## 版本与变更

- 1.0.0 (YYYY-MM-DD): 初始或本次修改说明
```

若已存在该小节，仅追加新条目并保持倒序，例如：

```markdown
## 版本与变更

- 1.1.0 (2025-02-06): 补充 BaseResultDTO 示例
- 1.0.0 (2025-01-15): 初始
```

## AGENTS.md

在文末追加（无 frontmatter 时）：

```markdown
---

## 版本与变更

- 1.0.0 (YYYY-MM-DD): 初始或本次修改说明
```

## skill/skills/*/SKILL.md

在 frontmatter 中增加 `version`；文末可选增加「## 版本与变更」，格式同上。

## .cursor/agents/*.md

在文末增加「## 版本与变更」小节，格式与 specrules 一致。

## 文件数统计口径

- **specrules**：统计根目录规范文件与 `00_general/`、`02_design/`、`03_coding/` 下参与索引的 .md（与 specrules/rules/index.md 的扫描来源章节一致）。
- **AGENTS.md**：1。
- **Skills**：`skill/skills/` 下每个技能目录计 1（以 SKILL.md 存在为准）。
- **Agents**：`agent/agents/` 下 .md 文件个数。
