---
name: astrology
description: 当用户询问星盘、占星、星座、本命盘、合盘、流运、行星相位、命盘解读等相关内容时使用此技能。包括：计算本命盘、和盘（合盘）、流运，以及对星盘进行 AI 解读分析。
---

# 占星技能（Astrology Skill）

## 功能说明

本技能提供完整的占星分析能力，支持以下三种核心功能：

1. **本命盘（Natal Chart）**：基于出生时间和地点计算个人星盘，解读性格、潜能与人生主题。
2. **合盘 / 和盘（Synastry）**：分析两人星盘的相互关系，解读感情兼容性与缘分。
3. **流运（Transit）**：分析当前行星过境对本命盘的影响，解读近期运势变化。

## 可用工具

激活本技能后，以下工具将可供使用：

### astrology_calculate_natal
计算用户本命盘。出生信息自动从用户档案中读取，无需额外提供。
- 参数：无
- 返回：完整星盘数据（行星位置、上升点、宫位等）

### astrology_interpret_natal
对本命盘进行 AI 解读。星盘数据自动从用户档案读取，无需传入 chart JSON。
- 参数：
  - focus（可选）：解读焦点，如 personality（性格）、career（事业）、emotion（情感）、growth（成长），留空则全面解读
  - tone（可选）：解读语气，gentle（温柔）/ rational（理性）/ deep（深度心理），默认 gentle
- 返回：温柔有洞察力的占星解读文字

### astrology_calculate_synastry
计算两人合盘。双方出生信息均自动从用户档案中读取（自己的出生信息 + 上次设置的合盘对方信息），无需额外提供。
- 参数：无
- 返回：合盘数据与兼容性分析

### astrology_interpret_synastry
对合盘进行 AI 解读。合盘数据及对方信息自动从用户档案读取，无需传入 chart JSON。
- 参数：
  - relationshipType（可选）：关系类型，romantic（恋人）/ friend（朋友）/ family（家人）/ colleague（同事），默认 romantic
  - focus（可选）：解读焦点，compatibility（兼容性）/ dynamic（关系动力）/ challenge（关系挑战）/ growth（共同成长），留空则全面解读
  - tone（可选）：解读语气，gentle（温柔）/ rational（理性）/ deep（深度），默认 gentle
- 返回：两人关系的占星解读

### astrology_calculate_transit
计算指定日期的流运。出生信息自动从用户档案读取，只需提供目标查询日期（可选）。
- 参数：
  - targetDate（可选）：目标查询日期，格式 yyyy-MM-dd，如 2026-05-29。留空则使用今天
- 返回：流运行星相位数据

### astrology_interpret_transit
对流运进行 AI 解读。流运数据自动从用户档案读取，无需传入 chart JSON。
- 参数：
  - focus（可选）：解读焦点，current（当下状态）/ love（情感变化）/ career（事业机遇）/ advice（近期建议），留空则全面解读
  - tone（可选）：解读语气，gentle（温柔）/ rational（理性）/ deep（深度），默认 gentle
- 返回：近期运势的占星解读

## 使用指引

1. 用户询问"帮我算星盘"、"我的上升星座"、"最近运势如何"等问题时，**无需要求用户再次提供出生信息**，直接调用对应工具即可（系统会自动从用户档案读取）。
2. 若调用工具后返回错误提示"出生信息未设置"，则引导用户先在个人中心设置出生信息。
3. 获取星盘数据后，可直接调用解读工具生成解读，无需将 chart JSON 作为参数传入。
4. 若用户只想了解星座知识，可直接回答，无需调用计算工具。

## 工具调用顺序建议

- **首次查询本命盘**：先调用 `astrology_calculate_natal`，再调用 `astrology_interpret_natal`
- **首次查询合盘**：先调用 `astrology_calculate_synastry`，再调用 `astrology_interpret_synastry`
- **首次查询流运**：先调用 `astrology_calculate_transit`，再调用 `astrology_interpret_transit`
- **重复查询**（已有缓存）：可直接调用解读工具，系统自动从缓存读取

## 示例对话引导

用户说"帮我算星盘"时，直接调用工具，若返回出生信息缺失错误，则回复：
> 你的出生信息还没有设置哦 🌟 请前往"我的 → 个人设置 → 占星信息"填写：
> - 出生年月日（如：1998年5月20日）
> - 出生时间（24小时制，如：14:30）
> - 出生城市（如：北京、上海）

