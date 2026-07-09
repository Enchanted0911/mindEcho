---
description: "核心分层架构原则和依赖规则"
alwaysApply: false
globs: ["**/*.java"]
version: "1.0.0"
---

# 分层架构核心原则

## 架构定义

本项目采用严格的四层分层架构：

```
API层 → 用户接口层 → 应用层 → 领域层 → 基础设施层
```

各层职责明确，主干依赖建议为单向；**不强制禁止跨层调用**，领域层在需要时可直接依赖**外层**（本工程以外）的 API 模块内容（见下）；
**不得**依赖本工程的 API 模块。

## 依赖约束

### 主干依赖与跨层例外

**建议的主干依赖方向**（上层 → 下层）：

```
API层 → 用户接口层 → 应用层 → 领域层 → 基础设施层
```

**具体约束**：

- ✅ 建议各层沿主干方向依赖下层，以保持清晰边界
- ❌ 禁止下层依赖上层（避免循环依赖）
- ✅ **允许领域层依赖外层 API 模块的内容**：可依赖**本工程以外**的 API 模块（其他工程/外部系统的
  API、client、Request/Response、DTO 等），不视为违规
- ❌ **禁止领域层依赖本工程 API 模块**：不得依赖本仓库内的 API 模块（如 growth-engine-api）的类型

### 核心依赖约束（保留）

1. **应用层** 不能依赖 **API层** 的 Request/Response 对象（应用层通过 UI 层入参接收已转换的 BO）
2. **领域层** 不能依赖 **应用层** 的 BO 对象
3. **基础设施层** 不能依赖 **领域层** 以上的任何对象

## 数据转换职责

每层都有明确的数据转换职责：

| 层级                   | 转换职责        | 说明                             |
|----------------------|-------------|--------------------------------|
| **用户接口层**            | DTO ↔ BO    | 使用 DTOConverter 进行转换           |
| **应用层**              | BO ↔ DO     | 使用 Converter 进行转换              |
| **领域层 (Repository)** | DO ↔ Entity | Repository 实现中进行转换，调用基础设施层 DAO |

## 架构验证清单

### 设计阶段检查

- [ ] 是否严格遵循四层分层架构？
- [ ] 各层职责是否明确？（允许 domain 依赖外层 API 模块；禁止依赖本工程 API 模块）
- [ ] 数据对象是否按层级正确命名？
- [ ] 依赖关系是否无循环？（下层不依赖上层）

### 实现阶段检查

- [ ] 接口与实现是否正确分离？
- [ ] 包结构是否符合规范？
- [ ] 转换器是否放在正确的层级？
- [ ] 是否使用了正确的数据对象？

### 质量保证检查

- [ ] 是否通过编译验证？
- [ ] 是否符合依赖注入规范？
- [ ] 是否实现了正确的异常处理？
- [ ] 是否满足 Region 数据隔离要求？

## 相关文档

- [API层标准](@specrules/00_general/architecture/api_layer_standards.md)
- [用户接口层标准](@specrules/00_general/architecture/ui_layer_standards.md)
- [应用层标准](@specrules/00_general/architecture/app_layer_standards.md)
- [领域层标准](@specrules/00_general/architecture/domain_layer_standards.md)
- [基础设施层标准](@specrules/00_general/architecture/infra_layer_standards.md)
- [数据对象命名规范](@specrules/00_general/naming/data_object_naming.md)

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
