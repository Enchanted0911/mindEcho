---
description: "枚举命名规范 - 定义枚举类名、值名和描述的统一命名标准，确保业务语义清晰"
alwaysApply: false
globs: ["**/*Enum.java", "**/*Status.java", "**/*Type.java", "**/*Level.java"]
version: "1.0.0"
---

# 枚举命名规范

## 概述

良好的枚举命名能够提高代码的可读性和可维护性，确保业务含义清晰。本规范定义了枚举类名、枚举值名和描述信息的统一命名标准。

## 枚举类命名

### 命名原则

**强制要求：**

1. ✅ **业务含义明确**：使用清晰的业务概念命名，避免技术术语
2. ✅ **统一后缀规范**：根据枚举的业务性质使用固定后缀
3. ✅ **PascalCase**：类名首字母大写，多个单词连接

### 后缀规范

| 枚举类型 | 后缀     | 示例                                            | 说明          |
|------|--------|-----------------------------------------------|-------------|
| 状态类  | Status | `CourseStatus`, `AuditStatus`, `OrderStatus`  | 表示对象的状态     |
| 类型类  | Type   | `AuditType`, `InteractionType`, `PaymentType` | 表示对象的分类     |
| 级别类  | Level  | `UserLevel`, `PriorityLevel`, `RiskLevel`     | 表示等级或优先级    |
| 通用枚举 | Enum   | `StatusEnum`, `TypeEnum`                      | 仅在无法确定类型时使用 |

### 命名示例

#### ✅ 正确的命名

```java
// 状态类 - 使用 Status 后缀
public enum CourseStatus implements IntValueEnum {
    DRAFT(1, "草稿"),
    PENDING(2, "待审核"),
    APPROVED(3, "审核通过");
}

// 审核状态 - 具体的业务状态
public enum AuditStatus implements IntValueEnum {
    PENDING(1, "待审核"),
    CREDIT_AUDITING(2, "外部审核中"),
    SCM_AUDITING(3, "审批中心审核中"),
    APPROVED(4, "审核通过"),
    REJECTED(5, "审核拒绝");
}

// 类型类 - 使用 Type 后缀
public enum AuditType implements IntValueEnum {
    MERCHANT_AUDIT(1, "商家审核"),
    PRODUCT_AUDIT(2, "产品审核"),
    FINANCIAL_AUDIT(3, "财务审核");
}

// 级别类 - 使用 Level 后缀
public enum UserLevel implements IntValueEnum {
    BRONZE(1, "青铜级"),
    SILVER(2, "白银级"),
    GOLD(3, "黄金级"),
    PLATINUM(4, "铂金级");
}
```

#### ❌ 错误的命名

```java
// ❌ 避免技术术语作为后缀
public enum StatusEnum implements IntValueEnum { ... }      // 冗余
public enum TypeEnum implements IntValueEnum { ... }        // 冗余
public enum CourseStatusEnum implements IntValueEnum { ... } // 双重冗余

// ❌ 避免过于简洁的命名
public enum Status implements IntValueEnum { ... }  // 不清晰
public enum Type implements IntValueEnum { ... }    // 不清晰
public enum State implements IntValueEnum { ... }   // 模糊

// ❌ 避免使用其他不规范的后缀
public enum CourseStatusType implements IntValueEnum { ... }    // 混合后缀
public enum CourseStatusVO implements IntValueEnum { ... }      // 不当使用
public enum CourseStatusData implements IntValueEnum { ... }    // 不当使用
```

## 枚举值命名

### 命名原则

**强制要求：**

1. ✅ **全大写下划线分隔**：使用 UPPER_CASE 风格
2. ✅ **业务语义清晰**：避免使用模糊或容易混淆的命名
3. ✅ **避免歧义**：同一名称在不同上下文中可能有不同含义
4. ✅ **英文术语统一**：同一业务术语在所有枚举中保持一致

### 命名示例

#### ✅ 正确的命名

```java
public enum CourseStatus implements IntValueEnum {
    DRAFT(1, "草稿"),                    // ✅ 清晰的初稿状态
    EDITING(2, "编辑中"),                // ✅ 正在编辑
    PENDING(3, "待审核"),                // ✅ 明确的审核前状态
    APPROVED(4, "审核通过"),             // ✅ 清晰的通过状态
    REJECTED(5, "审核拒绝"),             // ✅ 清晰的拒绝状态
    ONLINE(6, "已上线"),                 // ✅ 明确的发布状态
    OFFLINE(7, "已下线"),                // ✅ 明确的下线状态
}

public enum AuditStatus implements IntValueEnum {
    PENDING(1, "待审核"),                // ✅ 初始状态，待审核
    CREDIT_AUDITING(2, "外部审核中"),  // ✅ 具体的审核流程
    SCM_AUDITING(3, "审批中心审核中"),   // ✅ 具体的审核流程
    APPROVED(4, "审核通过"),             // ✅ 最终通过
    REJECTED(5, "审核拒绝"),             // ✅ 最终拒绝
}

public enum InteractionType implements IntValueEnum {
    LIKE(1, "点赞"),                     // ✅ 清晰的互动类型
    COMMENT(2, "评论"),                  // ✅ 清晰的互动类型
    SHARE(3, "分享"),                    // ✅ 清晰的互动类型
    BOOKMARK(4, "收藏"),                 // ✅ 清晰的互动类型
}
```

#### ❌ 错误的命名

```java
public enum BadStatus implements IntValueEnum {
    DRAFT(1, "草稿"),           // ❌ 在不同业务中含义不同（可能是课程、订单、草案等）
    PUBLISHED(2, "发布"),        // ❌ 含义与 ONLINE 重复，容易混淆
    ACTIVE(3, "激活"),           // ❌ 过于通用，不够具体
    INACTIVE(4, "非激活"),       // ❌ 过于通用，业务含义不清楚
    STATUS_1(5, "状态1"),       // ❌ 技术术语，毫无业务含义
    UNKNOWN(6, "未知"),         // ❌ 避免使用
}

public enum MixedStatus implements IntValueEnum {
    DRAFT(1, "草稿"),
    PENDING(2, "待审核"),
    ON_LINE(3, "已上线"),        // ❌ 使用了下划线分隔单词，应该是 ONLINE
    OFF_LINE(4, "已下线"),       // ❌ 使用了下划线分隔单词，应该是 OFFLINE
}
```

## 枚举值描述信息规范

### 描述信息原则

**强制要求：**

1. ✅ **必须使用中文**：便于问题排查和用户理解
2. ✅ **简洁明了**：通常 2-8 个字
3. ✅ **完整的业务含义**：让人能够理解这个状态代表什么
4. ✅ **避免重复**：不要重复类名或字段名

### 示例

```java
public enum CourseStatus implements IntValueEnum {
    DRAFT(1, "草稿"),                    // ✅ 简洁明了
    PENDING(2, "待审核"),                // ✅ 指示当前动作
    APPROVED(3, "审核通过"),             // ✅ 明确的结果
    REJECTED(4, "审核拒绝"),             // ✅ 明确的结果
    ONLINE(5, "已上线"),                 // ✅ 完成时的状态
    OFFLINE(6, "已下线"),                // ✅ 完成时的状态
}

// ❌ 不好的描述
public enum BadCourseStatus implements IntValueEnum {
    DRAFT(1, "CourseStatus.DRAFT"),           // ❌ 冗余，重复了类名
    PENDING(2, "pending status"),             // ❌ 使用英文，不符合规范
    APPROVED(3, "The course is approved"),    // ❌ 过长，使用英文
    UNKNOWN(4, ""),                           // ❌ 空描述
}
```

## 跨枚举命名一致性

### 同一业务术语必须保持一致

**强制要求：**

同一业务状态在不同的枚举中必须使用**相同的英文名称**。

```java
// ✅ 正确：所有地方都使用 PENDING
public enum CourseStatus implements IntValueEnum {
    PENDING(2, "待审核"),  // 课程状态中的待审核
    // ...
}

public enum AuditStatus implements IntValueEnum {
    PENDING(1, "待审核"),  // 审核状态中的待审核
    // ...
}

public enum OrderStatus implements IntValueEnum {
    PENDING(10, "待审核"),  // 订单状态中的待审核
    // ...
}

// ❌ 错误：使用了不同的名称
public enum CourseStatus implements IntValueEnum {
    AWAITING_REVIEW(2, "待审核"),  // ❌ 这里用 AWAITING_REVIEW
}

public enum AuditStatus implements IntValueEnum {
    PENDING(1, "待审核"),  // ❌ 这里用 PENDING
    // 相同含义，不同命名 → 容易混淆！
}
```

## 检查清单

- [ ] 枚举类名是否使用了正确的后缀（Status/Type/Level）？
- [ ] 枚举类名是否清晰表达业务含义（避免技术术语）？
- [ ] 枚举值是否使用全大写下划线分隔？
- [ ] 枚举值是否避免了模糊或歧义的命名？
- [ ] 枚举值描述信息是否使用中文？
- [ ] 描述信息是否简洁明了（通常 2-8 个字）？
- [ ] 同一业务概念在不同枚举中是否保持了命名一致性？
- [ ] 是否避免了技术术语作为后缀（如 Enum、VO、DTO）？

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
