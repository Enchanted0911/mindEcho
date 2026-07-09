---
description: "枚举继承规范 - 定义所有业务枚举必须继承 IntValueEnum 接口、实现关键方法和依赖配置要求"
alwaysApply: false
globs: ["**/*Enum.java", "**/*Status.java", "**/*Type.java", "**/*Level.java"]
version: "1.0.0"
---

# 枚举继承规范

## 概述

所有业务枚举都必须继承公司通用的枚举基类 `IntValueEnum`，确保类型安全和统一的接口规范。这是类型安全和业务表达能力的基础。

## 强制继承要求

### IntValueEnum 接口继承

所有业务枚举必须实现 `IntValueEnum` 接口。

**强制要求：**

1. ✅ 所有业务枚举都必须 `implements IntValueEnum`
2. ✅ 必须包含 `Integer value` 字段存储枚举值
3. ✅ 必须在构造函数中初始化 `value` 字段
4. ✅ 必须实现 `@Override public Integer getValue()` 方法

### 标准实现示例

```java
/**
 * 课程状态枚举 - 必须继承 IntValueEnum
 */
public enum CourseStatus implements IntValueEnum {
    DRAFT(1, "草稿"),
    PENDING(2, "待审核"),
    APPROVED(3, "审核通过"),
    REJECTED(4, "审核拒绝"),
    ONLINE(5, "已上线"),
    OFFLINE(6, "已下线");

    private final Integer value;
    private final String description;

    CourseStatus(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
```

## 必须实现的方法

### 1. getValue() 方法（必须）

```java
/**
 * 返回枚举对应的整数值
 * @return 枚举值
 */
@Override
public Integer getValue() {
    return value;
}
```

**强制要求：**

- 必须是 `@Override` 注解
- 必须返回 `Integer` 类型（不能是 int）
- 必须使用字段 `value` 直接返回，不能进行任何转换

### 2. getByValue() 静态方法（必须）

```java
/**
 * 根据值获取枚举实例
 * @param value 枚举值
 * @return 枚举实例，如果值为null则返回null
 * @throws IllegalArgumentException 如果值不存在对应的枚举
 */
public static CourseStatus getByValue(Integer value) {
    if (value == null) {
        return null;
    }
    for (CourseStatus status : values()) {
        if (Objects.equals(status.getValue(), value)) {
            return status;
        }
    }
    throw new IllegalArgumentException("未知的课程状态值: " + value);
}
```

**强制要求：**

1. ✅ **方法名必须是 `getByValue`**，不能使用其他名称（如 `getEnumByValue`）
2. ✅ **参数必须是 `Integer value`**（可以为 null）
3. ✅ **必须处理 null 值**：如果参数为 null，返回 null
4. ✅ **比较必须使用 `Objects.equals()`**：不能使用 `==` 或 `!=`
5. ✅ **必须遍历所有枚举值**：使用 `values()` 遍历
6. ✅ **值不存在时必须抛出异常**：使用 `IllegalArgumentException`
7. ✅ **异常信息必须包含:**
    - 错误的值是什么
    - 有效的值列表（可选，但推荐）

#### 增强的 getByValue() 实现

```java
/**
 * 审核状态枚举值查询 - 包含详细的错误信息
 */
public static AuditStatus getByValue(Integer value) {
    if (value == null) {
        return null;
    }
    for (AuditStatus status : values()) {
        if (Objects.equals(status.getValue(), value)) {
            return status;
        }
    }
    // 详细的错误信息，包含所有有效值
    String validValues = Arrays.stream(values())
        .map(s -> s.getValue() + "(" + s.getDescription() + ")")
        .collect(Collectors.joining(", "));
    throw new IllegalArgumentException("未知的审核状态值: " + value + "，有效值为: " + validValues);
}
```

## 依赖配置

### Maven 依赖

确保项目中包含公司通用枚举基类的依赖：

```xml
<dependency>
    <groupId>com.example.business</groupId>
    <artifactId>commons-langs</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**强制要求：**

1. ✅ 必须在 `pom.xml` 中添加此依赖
2. ✅ 不能跳过或删除此依赖
3. ✅ 必须使用指定的版本或更新版本

### 导入必要的类

```java
import java.util.Objects;
import java.util.Arrays;
import java.util.stream.Collectors;
import com.myapp.commons.enums.IntValueEnum;
```

## 检查清单

- [ ] 枚举是否继承了 `IntValueEnum` 接口？
- [ ] 是否包含 `Integer value` 字段？
- [ ] 是否实现了 `getValue()` 方法并使用 `@Override` 注解？
- [ ] 是否实现了 `getByValue(Integer value)` 静态方法？
- [ ] `getByValue()` 是否处理了 null 值？
- [ ] `getByValue()` 是否使用了 `Objects.equals()` 进行比较？
- [ ] 值不存在时是否抛出了 `IllegalArgumentException`？
- [ ] 异常信息是否清晰明了（包含错误值和有效值）？
- [ ] 是否在 `pom.xml` 中添加了 `commons-langs` 依赖？

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
