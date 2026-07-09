---
description: "集合判空、判非空与遍历的通用编码规范，禁止使用 !list.isEmpty() 或 .size() == 0"
alwaysApply: false
globs: ["**/*.java"]
version: "1.0.0"
---

# 集合操作规范

## 背景

集合的判空与非空判断是日常开发中的高频操作。直接使用 `!list.isEmpty()`、`list.size() == 0` 等写法存在以下问题：

- **NullPointerException 风险**：未先判断 null 就调用 `.isEmpty()` / `.size()` 会在集合为 null 时抛出 NPE。
- **语义不够明确**：`!list.isEmpty()` 需要两步脑力解析，`CollectionUtils.isNotEmpty()` 语义直接。
- **遗漏 null 检查**：`list.size() == 0` 既不处理 null，又比 `isEmpty()` 性能略差（需计算 size）。

## 强制规范

### 1. 禁止使用原生 isEmpty / size 进行判空

**禁止（❌）**：

```java
// 危险：list 为 null 时 NPE
if (!list.isEmpty()) { ... }
if (list.isEmpty()) { ... }

// 错误：size() == 0 语义冗余且有 NPE 风险
if (list.size() == 0) { ... }
if (list.size() > 0) { ... }
```

**必须使用（✅）**：

```java
// 推荐：CollectionUtils 同时处理 null 和 empty
if (CollectionUtils.isEmpty(list)) { ... }
if (CollectionUtils.isNotEmpty(list)) { ... }
```

> 本规范同样适用于 `Map`、`Set`、数组包装集合等所有集合类型。

### 2. 推荐的工具类

优先使用以下工具类（按优先级）：

| 工具类                                               | 来源             | 适用场景               |
|---------------------------------------------------|----------------|--------------------|
| `org.springframework.util.CollectionUtils`        | Spring         | 项目统一引入 Spring，优先使用 |
| `org.apache.commons.collections4.CollectionUtils` | Apache Commons | Spring 不够用时使用      |

> ⚠️ 禁止混用两种 `CollectionUtils`，同一文件内保持一致。

### 3. 返回空集合而非 null

从方法返回集合时，**禁止返回 null**，应返回空集合：

**禁止（❌）**：

```java
public List<CourseBO> getCourseList(Long userId) {
    if (noData) {
        return null; // 调用方必须判 null，容易遗漏
    }
    ...
}
```

**必须使用（✅）**：

```java
public List<CourseBO> getCourseList(Long userId) {
    if (noData) {
        return Collections.emptyList();
    }
    ...
}
```

### 4. 遍历前无需额外判空

当使用 `CollectionUtils.isNotEmpty()` 或返回空集合的方法时，可以直接 `for-each` 遍历，**不需要额外再判 isEmpty**：

```java
// ✅ 正确：isNotEmpty 已保证非 null 非空
if (CollectionUtils.isNotEmpty(courseList)) {
    for (CourseBO course : courseList) {
        process(course);
    }
}

// ✅ 正确：空集合直接遍历不会进入循环体
List<CourseBO> courses = getCourseList(userId); // 保证返回非 null
for (CourseBO course : courses) {
    process(course);
}
```

## 规范检查清单

开发时请检查以下项目：

- [ ] 是否使用了 `CollectionUtils.isEmpty()` / `CollectionUtils.isNotEmpty()` 替代原生 `isEmpty()`？
- [ ] 是否杜绝了 `.size() == 0`、`.size() > 0` 的写法？
- [ ] 方法返回值是否使用 `Collections.emptyList()` / `Collections.emptyMap()` 而非 `null`？
- [ ] 同一文件内是否只使用了一种 `CollectionUtils`？

## 常见错误示例与修正

| 错误写法                    | 修正写法                                    |
|-------------------------|-----------------------------------------|
| `if (!list.isEmpty())`  | `if (CollectionUtils.isNotEmpty(list))` |
| `if (list.isEmpty())`   | `if (CollectionUtils.isEmpty(list))`    |
| `if (list.size() == 0)` | `if (CollectionUtils.isEmpty(list))`    |
| `if (list.size() > 0)`  | `if (CollectionUtils.isNotEmpty(list))` |
| `if (map.size() == 0)`  | `if (CollectionUtils.isEmpty(map))`     |
| `return null; // 集合返回`  | `return Collections.emptyList();`       |

---

## 版本与变更

- 1.0.0 (2026-03-23): 新增集合判空与非空操作规范，禁止 !list.isEmpty() / .size()==0 等写法
