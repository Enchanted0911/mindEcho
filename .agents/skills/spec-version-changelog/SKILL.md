---
name: spec-version-changelog
description: "Record version and changelog for specrules, AGENTS.md, skills, and agents. Use when running rules-curator, or when specrules files, AGENTS.md, canonical skills under skill/skills, installed skill files under .agents/skills, or canonical agents under agent/agents change. Updates specrules/rules/index.md with global version table and per-scope file counts, and maintains version + changelog in each spec file."
version: "1.1.1"
---

# 规范与 Agent 版本与变更记录

在 **rules-curator** 执行后、或当 **specrules**、**AGENTS.md**、**skills**、**agents** 发生变更时，负责记录版本号与变更日志：在 `specrules/rules/index.md` 中维护全局版本与各范围文件统计，在每个规范文件内维护单文件版本与变更记录。

## 触发场景

- 使用 `/rules-curator` 或调用 rules-curator 完成规则/索引维护后
- `specrules/` 下任意规范文件新增、修改或删除
- `AGENTS.md` 内容变更
- `skill/skills/` 下 canonical 技能源文件，或 `.agents/skills/` 下安装后生效的技能文件变更
- `agent/agents/` 下 agent 定义文件变更

## 版本与变更记录位置

| 位置 | 内容 |
|------|------|
| **specrules/rules/index.md** | 更新其中的「规范与 Agent 版本与变更」章节：索引版本号、各范围文件数、各范围当前版本、变更日志摘要 |
| **specrules 下各规范文件** | 在现有 frontmatter 中增加 `version`；在文末增加「## 版本与变更」小节 |
| **AGENTS.md** | 在文末增加「## 版本与变更」小节（含当前版本号与条目列表） |
| **skill/skills/*/SKILL.md` / `.agents/skills/*`** | 对 canonical skill 源文件或安装后生效的 skill 文件，在 frontmatter 或文末增加/更新 `version` 与「## 版本与变更」 |
| **agent/agents/*.md** | 在 frontmatter 或文末增加「## 版本与变更」小节 |

## 执行流程

### 1. 检测变更范围

- 通过 git diff / 用户说明 / rules-curator 输出，确定本次涉及范围：`specrules`、`AGENTS.md`、`skills`、`agents` 的一种或多种。
- 统计当前各范围**文件数量**（仅统计参与索引或对外生效的文件，与 `specrules/rules/index.md` 的扫描来源保持一致）。

### 2. 更新 specrules/rules/index.md

- 在 `specrules/rules/index.md` 的 **「规范与 Agent 版本与变更」章节**（若不存在则新增）中：
  - **索引版本**：采用语义化版本（如 1.0.0），仅当本次有任意范围变更时递增（规则/索引结构性变更可升 minor，仅文案修正可升 patch）。
  - **各范围统计表**：列出范围名、当前文件数、该范围当前版本号。
  - **变更日志**：按时间倒序，每条包含日期、影响范围、简要说明。
- 格式与占位示例见 [reference.md](reference.md)。

### 3. 更新各规范文件（specrules）

- **有 YAML frontmatter 的文件**：在 frontmatter 中增加或更新 `version: "x.y.z"`。
- **文末**：增加或更新「## 版本与变更」小节；若已存在则追加新条目，保持按时间倒序。
- 仅对**本次实际修改过的文件**递增其 version 并写入一条 changelog；未改动的文件不修改版本号。

### 4. 更新 AGENTS.md

- 在文末增加或更新「## 版本与变更」小节，格式与 specrules 一致；若本次改动了 AGENTS.md，则递增其版本并写一条变更说明。

### 5. Skills / Agents（可选按需）

- 若本次变更涉及 `skill/skills/`、`.agents/skills/` 或 `agent/agents/`，则对变更过的 SKILL.md、安装后生效的 skill 文件或 agent 文件，在 frontmatter 或文末增加/更新 version 与「## 版本与变更」。

## 版本号约定

- **索引与范围版本**：语义化 `x.y.z`。索引版本随任意范围变更而递增；各范围版本仅在该范围有文件变更时递增。
- **单文件版本**：与范围版本一致或使用同一套 x.y.z，仅在该文件内容变更时递增。
- **变更日志条目格式**：`- x.y.z (YYYY-MM-DD): 简要说明`。

## 与 rules-curator 的协作

- 当 **rules-curator** 完成 Phase 1/Phase 2 或刷新 `specrules/rules/index.md` 后，应触发本技能：根据 rules-curator 输出的「文件变更列表」确定变更范围，再执行上述 1～5 步。
- 若用户仅执行本技能（未先执行 rules-curator），则根据 git 状态或用户描述确定变更范围并只更新版本与变更相关部分，不改变规则正文或索引结构。

## 约束

- 不修改规范文件除 frontmatter `version` 与文末「## 版本与变更」以外的内容。
- `specrules/rules/index.md` 中仅新增或更新「规范与 Agent 版本与变更」章节，不改变其余索引逻辑、加载顺序与引用路径。
- 统计文件数时与 specrules/rules/index.md 的「扫描来源与忽略规则」一致（例如包含 `specrules/constitution.md` 与各规则子目录）。

## 详细模板与示例

见 [reference.md](reference.md)。

---

## 版本与变更

- 1.1.1 (2026-03-17): 补充 `.agents/skills/` 下安装后生效文件的版本记录场景，与当前运行时安装目录保持一致。
- 1.1.0 (2026-03-13): 取消对 `specrules/rules/index.md` 固定章节号的依赖，改为更新其中的“规范与 Agent 版本与变更”章节，以兼容新的阶段化入口结构。
- 1.0.0 (2025-02-06): 初始化版本与变更记录
