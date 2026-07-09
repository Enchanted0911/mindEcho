---
description: "枚举方法设计规范 - 定义枚举中业务判断方法、错误处理和状态流转方法的设计标准"
alwaysApply: false
globs: ["**/*Enum.java", "**/*Status.java", "**/*Type.java", "**/*Level.java"]
version: "1.1.0"
---

# 枚举方法设计规范

## 概述

枚举方法是实现业务逻辑的关键。通过在枚举中定义业务判断方法，可以避免在 Service 层中分散的条件判断，提高代码的可读性和可维护性。

## 必须实现的方法

### 1. getByValue() 静态方法（必须）

详见 [枚举继承规范 - getByValue() 方法](./enum_inheritance_standards.md#2-getbyvalue-静态方法必须)

**强制要求：**

- 必须存在 `getByValue(Integer value)` 方法
- 必须处理 null 值
- 必须使用 `Objects.equals()` 进行比较
- 必须抛出详细的异常信息

### 2. 业务判断方法（必须）

为常用的业务判断提供便捷方法，避免在 Service 层中重复编写相同的判断逻辑。

#### 2.1 状态流转判断

```java
/**
 * 判断是否可以提交审核
 */
public boolean canSubmitAudit() {
    return this == DRAFT || this == REJECTED;
}

/**
 * 判断是否可以上线
 */
public boolean canGoOnline() {
    return this == APPROVED;
}

/**
 * 判断是否可以编辑
 */
public boolean canEdit() {
    return this == DRAFT || this == REJECTED;
}

/**
 * 判断是否可以下线
 */
public boolean canOffline() {
    return this == ONLINE;
}
```

#### 2.2 状态分类判断

```java
/**
 * 判断是否为完成状态
 */
public boolean isCompleted() {
    return this == ONLINE || this == OFFLINE;
}

/**
 * 判断是否在审核中
 */
public boolean isAuditing() {
    return this == PENDING || this == CREDIT_AUDITING || this == SCM_AUDITING;
}

/**
 * 判断是否为终态（不可更改）
 */
public boolean isFinal() {
    return this == APPROVED || this == REJECTED || this == ONLINE || this == OFFLINE;
}
```

#### 2.3 业务操作判断

```java
/**
 * 判断是否为用户主动互动（相对于系统自动操作）
 */
public boolean isUserInitiated() {
    return getValue() <= 20;
}

/**
 * 判断是否需要审核
 */
public boolean requiresAudit() {
    return this == PENDING || this == CREDIT_AUDITING;
}
```

### 3. 标准实现示例

枚举基础结构（`implements IntValueEnum`、构造器、`getValue()`、`getByValue()`
）见 [枚举继承规范](./enum_inheritance_standards.md)。在此基础上追加业务判断方法即为完整枚举，无需重复。

## 错误处理规范

### 1. getByValue() 中的错误处理

`getByValue()` 的完整实现（含 null 处理、`Objects.equals()`
比较、增强错误信息）见 [枚举继承规范 §2](./enum_inheritance_standards.md)。

### 2. 业务判断方法中的错误处理

业务判断方法**不应该抛异常**，而应该返回 boolean 结果。

```java
// ✅ 正确：返回 boolean 结果
public boolean canEdit() {
    return this == DRAFT || this == REJECTED;
}

// ❌ 错误：在判断方法中抛异常
public boolean canEdit() {
    if (this == ONLINE) {
        throw new BaseBizException("已上线的课程不能编辑");
    }
    return this == DRAFT || this == REJECTED;
}
```

**异常处理应该在调用层进行：**

```java
public class CourseDO {
    public void editCourse(String newTitle) {
        CourseStatus status = getStatusEnum();
        if (status == null || !status.canEdit()) {
            throw new BaseBizException("课程不能编辑，当前状态：" + 
                (status != null ? status.getDescription() : "未知"));
        }
        this.title = newTitle;
    }
}
```

## 方法设计最佳实践

### 1. 方法命名规范

| 前缀      | 含义       | 示例                               | 说明                    |
|---------|----------|----------------------------------|-----------------------|
| `can`   | 能否执行某个操作 | `canEdit()`, `canSubmitAudit()`  | 返回 boolean，表示前置条件是否满足 |
| `is`    | 是否属于某个分类 | `isAuditing()`, `isFinal()`      | 返回 boolean，表示状态分类     |
| `get`   | 获取某个值    | `getValue()`, `getDescription()` | 返回具体的值                |
| `getBy` | 根据条件获取   | `getByValue()`                   | 静态方法，根据条件查询           |

### 2. 方法的文档注释

```java
/**
 * 判断是否可以编辑
 * 
 * 编辑条件：
 * - 状态为 DRAFT（草稿）
 * - 状态为 REJECTED（审核拒绝）
 * 
 * @return true 表示可以编辑，false 表示不能编辑
 */
public boolean canEdit() {
    return this == DRAFT || this == REJECTED;
}
```

### 3. 复杂业务判断的提取

对于复杂的业务判断，应该创建单独的方法。

```java
// ✅ 正确：清晰的方法拆分
public boolean canSubmitForApproval() {
    return this == DRAFT || this == REJECTED;
}

public boolean requiresHigherApproval() {
    return this == PENDING && getValue() > 50; // 示例：金额超过50的需要更高级别审批
}

// ❌ 错误：过于复杂的条件
public boolean canProcess() {
    return (this == DRAFT || this == REJECTED) && 
           (getValue() < 50 || getValue() > 100) &&
           !this.toString().contains("FINAL");  // 这样的复杂逻辑难以理解
}
```

## 避免的反模式

### 1. 在业务判断方法中修改状态

```java
// ❌ 反模式：方法名表示查询，但实际修改了状态
public boolean canEdit() {
    if (this == ONLINE) {
        this = DRAFT;  // ❌ 不能修改
    }
    return this == DRAFT;
}
```

### 2. 过度设计业务判断方法

```java
// ❌ 反模式：为每个可能的操作都定义一个方法，导致方法爆炸
public boolean canEditTitle() { ... }
public boolean canEditDescription() { ... }
public boolean canEditCategory() { ... }
public boolean canEditPrice() { ... }
// ... 导致数十个方法

// ✅ 正确：统一为 canEdit()
public boolean canEdit() {
    return this == DRAFT || this == REJECTED;
}
```

### 3. 在业务判断方法中访问外部对象

```java
// ❌ 反模式：判断方法依赖外部对象
private CourseDO course;  // 枚举中存储 DO 对象是错误的！

public boolean canEdit() {
    return this == DRAFT && course.getApprovedTime() == null;
}

// ✅ 正确：只依赖自身状态
public boolean canEdit() {
    return this == DRAFT || this == REJECTED;
}

// 在 DO 中进行更复杂的判断
public class CourseDO {
    public boolean canEdit() {
        CourseStatus status = getStatusEnum();
        return status != null && status.canEdit() && 
               this.approvedTime == null;  // DO 可以访问自己的字段
    }
}
```

## 检查清单

- [ ] 是否实现了 `getByValue(Integer value)` 方法？
- [ ] `getByValue()` 是否处理了 null 值？
- [ ] `getByValue()` 是否使用了 `Objects.equals()` 进行比较？
- [ ] 值不存在时是否抛出了详细的异常？
- [ ] 异常信息是否包含错误值和有效值列表？
- [ ] 是否为常用的业务操作定义了判断方法（如 canEdit、canSubmit 等）？
- [ ] 业务判断方法的命名是否遵循规范（can/is 前缀）？
- [ ] 业务判断方法是否只返回 boolean，不抛异常？
- [ ] 业务判断方法是否包含了清晰的 JavaDoc 注释？
- [ ] 是否避免了在枚举中访问外部对象或修改状态？

---

## 版本与变更

- 1.1.0 (2026-03-25): 删除 §3 重复的 CourseStatus 完整类（引用 enum_inheritance_standards.md）；删除 §错误处理 §1 重复的
  getByValue() 实现代码（引用 enum_inheritance_standards.md §2）
- 1.0.0 (2025-02-06): 初始化版本与变更记录
