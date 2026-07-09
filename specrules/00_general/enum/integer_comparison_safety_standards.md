---
description: "Integer 对象安全比较规范 - 禁止直接使用 ==、!= 比较 Integer 对象，强制使用 Objects.equals()"
alwaysApply: false
globs: ["**/*.java"]
version: "1.1.0"
---

# Integer 对象安全比较规范

## 概述

在 Java 中，Integer 是包装类型，直接使用 `==` 或 `!=` 进行比较会产生意外的行为。本规范定义了 Integer
对象比较的强制要求，确保代码的正确性和安全性。

## 核心问题

### Integer 对象的缓存特性

Java 中 Integer 对象在 -128 到 127 范围内会被缓存，使用 `==` 比较时可能得到预期结果，但超出范围时会产生错误。

```java
Integer a = 100;
Integer b = 100;
System.out.println(a == b);  // ✅ true（在缓存范围内）

Integer c = 200;
Integer d = 200;
System.out.println(c == d);  // ❌ false（超出缓存范围，结果不确定）
```

### 空指针异常风险

直接使用 `==` 比较时，如果对象为 null，可能导致业务逻辑错误。

```java
Integer status = null;
if (status == 1) {  // ❌ 如果 status 为 null，比较结果为 false，但代码可能预期是 false
    // 业务逻辑
}

// 更危险的情况
if (status != 1) {  // 当 status 为 null 时，结果为 true
    // 业务逻辑错误！
}
```

## 强制规范

### 规则 1: 禁止直接比较 Integer 对象

**强制要求：**

- ❌ 禁止使用 `status == 1`
- ❌ 禁止使用 `status != null ? status == 1 : false`
- ❌ 禁止使用 `status.intValue() == 1`（隐藏转换）
- ✅ 必须使用 `Objects.equals(status, 1)`

### 规则 2: 使用 Objects.equals() 进行安全比较

```java
import java.util.Objects;

// ✅ 正确方式 - 自动处理 null 值
if (Objects.equals(status, 1)) {
    // status == 1
}

if (!Objects.equals(status, 1)) {
    // status != 1
}

// ✅ 推荐：比较枚举值
if (Objects.equals(auditStatus, AuditStatus.PENDING.getValue())) {
    // 状态为审核待处理
}
```

### 规则 3: 优先使用枚举类型比较

最安全的做法是使用枚举类型直接比较，而不是比较 Integer 值。

```java
// ✅ 最佳方式 - 直接枚举比较，类型安全
if (auditInfoDO.getAuditStatusEnum() == AuditStatus.PENDING) {
    // 状态为待审核
}

// ✅ 次优方式 - 通过业务判断方法
if (auditInfoDO.isPending()) {
    // 状态为待审核
}

// ⚠️ 可接受 - 使用 Objects.equals()
if (Objects.equals(auditInfoDO.getAuditStatus(), AuditStatus.PENDING.getValue())) {
    // 状态为待审核
}
```

### 规则 4: 比较前必须进行空值检查

当需要使用 Integer 值进行业务逻辑判断时，必须在适当的地方进行空值检查。

```java
public class AuditInfoDO {
    private Integer auditStatus;

    /**
     * 安全的状态判断方法
     */
    public boolean isPending() {
        return Objects.equals(auditStatus, AuditStatus.PENDING.getValue());
    }

    /**
     * 获取枚举对象（处理空值）
     */
    public AuditStatus getAuditStatusEnum() {
        return AuditStatus.getByValue(auditStatus); // getByValue() 已处理 null
    }

    /**
     * 安全的状态设置
     */
    public void setAuditStatusEnum(AuditStatus status) {
        this.auditStatus = status != null ? status.getValue() : null;
    }
}
```

## 详细对比

### ❌ 错误方式 vs ✅ 正确方式

#### 场景 1: 简单的相等比较

```java
// ❌ 错误：可能因缓存导致不可预测的结果
if (courseStatus == 1) {
    // ...
}

// ✅ 正确
if (Objects.equals(courseStatus, 1)) {
    // ...
}

// ✅ 最佳（如果可能）
if (course.getStatusEnum() == CourseStatus.DRAFT) {
    // ...
}
```

#### 场景 2: 不相等比较

```java
// ❌ 错误：当 courseStatus 为 null 时行为不确定
if (courseStatus != 1) {
    // ...
}

// ✅ 正确
if (!Objects.equals(courseStatus, 1)) {
    // ...
}

// ✅ 最佳
CourseStatus status = course.getStatusEnum();
if (status == null || status != CourseStatus.DRAFT) {
    // ...
}
```

#### 场景 3: 多值比较

```java
// ❌ 错误：多次错误的 == 比较
if (status == 1 || status == 2 || status == 3) {
    // ...
}

// ✅ 正确：使用 Objects.equals()
if (Objects.equals(status, 1) || 
    Objects.equals(status, 2) || 
    Objects.equals(status, 3)) {
    // ...
}

// ✅ 最佳：使用 List 包含检查
if (Arrays.asList(1, 2, 3).contains(status)) {
    // ...
}

// ✅ 最佳：使用枚举
CourseStatus statusEnum = course.getStatusEnum();
if (statusEnum == CourseStatus.DRAFT || 
    statusEnum == CourseStatus.PENDING || 
    statusEnum == CourseStatus.APPROVED) {
    // ...
}

// ✅ 最佳：在枚举中定义判断方法
if (course.getStatusEnum() != null && 
    course.getStatusEnum().isEditableStatus()) {
    // ...
}
```

#### 场景 4: 条件判断中的空值处理

```java
// ❌ 错误：隐含的空值处理
Integer status = null;
if (status != null && status == 1) {  // 第二个条件的 == 仍然不安全
    // ...
}

// ✅ 正确
if (Objects.equals(status, 1)) {
    // ...
}

// ✅ 最佳：在 DAO/DO 方法中处理
public boolean isActive() {
    return Objects.equals(this.status, StatusEnum.ACTIVE.getValue());
}
```

DO 中枚举互操作方法（`getStatusEnum`/`setStatusEnum`/`canEdit` 等）以及 Service
层使用模式，详见 [枚举使用规范](./enum_usage_rules.md)。

## 常见错误及修复

### 错误 1: 在循环中进行不安全的比较

```java
// ❌ 错误
for (Integer statusValue : statusList) {
    if (statusValue == targetStatus) {  // 不安全
        // ...
    }
}

// ✅ 正确
for (Integer statusValue : statusList) {
    if (Objects.equals(statusValue, targetStatus)) {
        // ...
    }
}
```

### 错误 2: 在 Lambda 表达式中进行不安全的比较

```java
// ❌ 错误
List<CourseDO> draftCourses = courseList.stream()
    .filter(c -> c.getStatus() == 1)  // 不安全
    .collect(Collectors.toList());

// ✅ 正确
List<CourseDO> draftCourses = courseList.stream()
    .filter(c -> Objects.equals(c.getStatus(), 1))
    .collect(Collectors.toList());

// ✅ 最佳
List<CourseDO> draftCourses = courseList.stream()
    .filter(c -> c.isDraft())
    .collect(Collectors.toList());
```

### 错误 3: 在 Map 值的比较中

```java
// ❌ 错误
Map<String, Integer> statusMap = getStatusMap();
if (statusMap.get("course") == 1) {  // 不安全
    // ...
}

// ✅ 正确
if (Objects.equals(statusMap.get("course"), 1)) {
    // ...
}
```

## 检查清单

- [ ] 代码中是否存在 `Integer == value` 的比较？
- [ ] 代码中是否存在 `Integer != value` 的比较？
- [ ] 是否所有 Integer 比较都使用了 `Objects.equals()`？
- [ ] DO/Entity 类中是否提供了类型安全的业务判断方法？
- [ ] 是否优先使用枚举类型进行比较（而不是 Integer 值）？
- [ ] Service 层是否使用了 DO 的业务方法进行状态判断？
- [ ] 是否避免了在 Lambda 表达式中进行不安全的比较？
- [ ] 是否所有列表/Map 中的 Integer 比较都使用了安全方法？

---

## 版本与变更

- 1.1.0 (2026-03-25): 删除 §在领域对象中的实现 和 §在 Service 中的使用（已在 enum_usage_rules.md 覆盖），改为引用
- 1.0.0 (2025-02-06): 初始化版本与变更记录
