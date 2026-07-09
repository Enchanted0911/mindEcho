---
description: "编译通过检查规范 - 定义编译检查的优先级、错误分类、错误处理流程和常见错误解决方案"
alwaysApply: false
globs: ["**/*Test.java"]
version: "1.0.0"
---

# 编译通过检查规范

## 概述

本规范定义了单元测试编译过程中的强制要求、错误检查优先级和处理流程。编译错误是第一优先级问题，必须立即修复。

---

## 编译检查优先级

### 优先级顺序（强制执行）

所有编译错误发生时，按以下顺序进行检查和修复：

```
优先级 1：编译错误零容忍
    ↓ 修复所有编译错误
    ↓
优先级 2：字段存在性检查
    ↓ 禁止使用不存在的字段
    ↓
优先级 3：方法存在性检查
    ↓ 禁止调用不存在的方法
    ↓
优先级 4：类型匹配检查
    ↓ 确保字段类型和参数类型一致
    ↓
编译通过 ✅
```

---

## 优先级详解

### 优先级 1：编译错误零容忍

**定义**：所有测试代码必须能够完全编译通过，不允许任何编译错误。

**包含项**：

- ✅ 语法错误必须修复
- ✅ import 错误必须修复
- ✅ 循环依赖必须消除
- ✅ 不完整的语句必须补全

**示例**：

```java
// ❌ 编译错误：缺少分号
courseDO.setId(1L)
courseDO.setTitle("test");

// ✅ 修复：添加分号
courseDO.setId(1L);
courseDO.setTitle("test");
```

### 优先级 2：字段存在性检查

**定义**：禁止使用被测试类中不存在的字段。

**检查步骤**：

1. 打开被测试类源代码
2. 找到类的字段定义部分
3. 确认测试中使用的所有字段都存在
4. 确认字段名拼写完全正确

**错误分类**：

- [ ] 字段拼写错误：`getXXX()` vs `getXxx()`
- [ ] 字段不存在：某个字段在实体类中根本没有定义
- [ ] 调用错误的 getter/setter：方法名对应的字段不存在

**示例**：

```java
// ❌ 编译错误：字段不存在
courseDO.setNonExistentField("value");
courseDO.getInvalidField();

// ✅ 正确：使用实际存在的字段
courseDO.setTitle("测试课程");
courseDO.getCode();
```

### 优先级 3：方法存在性检查

**定义**：禁止调用不存在的方法，包括 Mock 和 verify。

**检查步骤**：

1. 打开被测试类所依赖的接口
2. 查看接口的方法定义
3. 确认测试中的所有方法调用都存在
4. 确认 Mock 的方法也真实存在于接口中

**错误分类**：

- [ ] Mock 不存在的方法：接口中没有该方法定义
- [ ] verify 不存在的方法：被测试类不调用该方法
- [ ] 参数数量错误：方法参数个数不符

**示例**：

```java
// ❌ 编译错误：方法不存在
when(courseService.nonExistentMethod()).thenReturn(result);
verify(courseService).invalidMethod();

// ✅ 正确：使用实际存在的方法
when(courseService.getCourseById(anyLong(), anyString())).thenReturn(result);
verify(courseService).getCourseById(eq(1L), eq("CN"));
```

### 优先级 4：类型匹配检查

**定义**：字段类型、参数类型、返回值类型必须与实际定义完全匹配。

**检查项**：

- [ ] 字段类型必须与实际定义完全匹配
- [ ] 方法参数类型必须正确
- [ ] 返回值类型必须一致
- [ ] 集合泛型类型必须完全匹配

**示例**：

```java
// ❌ 编译错误：类型不匹配
courseDO.setId("1");              // ID 字段是 Long 类型
courseDO.setStatus("ACTIVE");     // status 是 Integer 类型
List<String> idList = ids;        // ids 是 List<Long> 类型

// ✅ 正确：使用正确的类型
courseDO.setId(1L);
courseDO.setStatus(1);
List<Long> idList = ids;
```

---

## 编译错误分类和处理

### 1. Import 语句错误

**错误信息**：

```
error: cannot find symbol
  symbol:   class XxxClass
  location: class YourTestClass
```

**排查步骤**：

1. 检查类是否真实存在
2. 检查包名是否正确
3. 检查是否有拼写错误

**处理方案**：

```java
// ❌ 错误：import 类不存在
import com.myapp.nonexistent.NonExistentClass;

// ✅ 正确：import 真实存在的类
import com.myapp.application.course.domain.model.CourseDO;
```

### 2. 字段不存在错误

**错误信息**：

```
error: cannot find symbol
  symbol:   method setNonExistentField(String)
  location: class CourseDO
```

**排查步骤**：

1. 打开 CourseDO 类
2. 搜索 `setNonExistentField` 方法
3. 确认字段是否存在

**处理方案**：

```java
// ❌ 编译错误：字段不存在
courseDO.setNonExistentField("value");

// ✅ 正确：检查 CourseDO 实际字段，只设置存在的字段
courseDO.setTitle("测试课程");
```

### 3. 方法不存在错误

**错误信息**：

```
error: cannot find symbol
  symbol:   method getCourseById(String)
  location: interface CourseService
```

**排查步骤**：

1. 打开 CourseService 接口
2. 查看所有方法签名
3. 确认被调用的方法是否存在

**处理方案**：

```java
// ❌ 编译错误：方法不存在或参数类型错误
when(courseService.getCourseById("1")).thenReturn(result);

// ✅ 正确：使用实际存在的方法签名
when(courseService.getCourseById(1L, "CN")).thenReturn(result);
```

### 4. 类型不匹配错误

**错误信息**：

```
error: incompatible types: String cannot be converted to Long
```

**排查步骤**：

1. 查看字段或参数的定义类型
2. 检查赋值的实际类型
3. 进行类型转换或修正

**处理方案**：

```java
// ❌ 类型不匹配
courseDO.setId("1");           // ID 是 Long，不是 String
courseDO.setStatus("ACTIVE");  // status 是 Integer，不是 String

// ✅ 正确类型
courseDO.setId(1L);
courseDO.setStatus(1);
```

### 5. 泛型类型错误

**错误信息**：

```
error: incompatible types: List<String> cannot be converted to List<Long>
```

**排查步骤**：

1. 确认泛型参数的实际类型
2. 检查集合中元素的类型
3. 进行必要的类型转换

**处理方案**：

```java
// ❌ 泛型类型错误
List<String> courseIds = new ArrayList<>(Arrays.asList("1", "2", "3"));
List<Long> expectedIds = courseIds;  // 类型不匹配

// ✅ 正确类型
List<Long> courseIds = new ArrayList<>(Arrays.asList(1L, 2L, 3L));
List<Long> expectedIds = courseIds;
```

---

## 编译错误处理流程

### 完整的错误处理流程

```
执行编译命令：mvn compile
    ↓
检查输出是否有 [ERROR] 标记
    ↓
有错误 → 进入错误分析
    |   ├─ 识别错误类型
    |   ├─ 定位错误代码
    |   ├─ 修复错误
    |   └─ 重新编译
    |   ↑ 循环直到没有错误
    ↓
无错误 → 编译成功 ✅
```

### 逐步修复策略

**第一步：停止开发**

- 发现任何编译错误时立即停止开发任何新功能

**第二步：错误分类**

- 识别是 import 错误、字段错误还是方法错误

**第三步：定位源头**

- 打开相关的源代码文件，查看真实定义

**第四步：修复错误**

- 根据实际代码修正测试代码（不修改被测试类）

**第五步：重新编译**

- 执行 `mvn compile` 验证修复

**第六步：完全通过**

- 确认所有错误都消除

---

## 常见编译错误及解决方案

### 场景 1：Repository 接口方法签名错误

**错误示例**：

```java
@Test
public void testFindById() {
    // ❌ 错误：CourseRepository.findById 实际需要两个参数
    when(courseRepository.findById(1L)).thenReturn(mockCourseDO);
}
```

**解决方案**：

```java
// 第一步：打开 CourseRepository 接口
interface CourseRepository {
    CourseDO findById(Long id, String region);  // 实际签名
}

// 第二步：修正测试代码
@Test
public void testFindById() {
    // ✅ 正确：包含 region 参数
    when(courseRepository.findById(1L, "CN")).thenReturn(mockCourseDO);
}
```

### 场景 2：Entity 字段类型错误

**错误示例**：

```java
@Test
public void testCreateEntity() {
    Course entity = new Course();
    // ❌ 错误：id 是 Long 类型
    entity.setId("123");
}
```

**解决方案**：

```java
// 第一步：打开 Course 实体类
class Course {
    private Long id;  // 类型是 Long
}

// 第二步：修正测试代码
@Test
public void testCreateEntity() {
    Course entity = new Course();
    // ✅ 正确：使用 Long 类型
    entity.setId(123L);
}
```

### 场景 3：DAO 方法不存在

**错误示例**：

```java
@Test
public void testFindByCode() {
    // ❌ 错误：CourseDAO 中没有 findByCode 方法
    when(courseDAO.findByCode("COURSE001")).thenReturn(mockEntity);
}
```

**解决方案**：

```java
// 第一步：打开 CourseDAO 接口
interface CourseDAO {
    Course selectById(Long id);
    List<Course> selectByCondition(QueryCondition condition);
    // 没有 findByCode 方法！
}

// 第二步：修正测试代码，使用实际存在的方法
@Test
public void testFindByCode() {
    CourseDAO.QueryCondition condition = new CourseDAO.QueryCondition();
    condition.setCourseCode("COURSE001");
    // ✅ 正确：使用实际存在的方法
    when(courseDAO.selectByCondition(condition)).thenReturn(mockList);
}
```

### 场景 4：Mock 的参数数量错误

**错误示例**：

```java
@Test
public void testService() {
    // ❌ 错误：courseDomainService.createCourse 实际需要一个参数
    when(courseDomainService.createCourse()).thenReturn(mockDO);
}
```

**解决方案**：

```java
// 第一步：打开 CourseDomainService 接口
interface CourseDomainService {
    CourseDO createCourse(CourseDO courseDO);  // 需要一个参数
}

// 第二步：修正测试代码
@Test
public void testService() {
    CourseDO inputDO = new CourseDO();
    // ✅ 正确：提供必要的参数
    when(courseDomainService.createCourse(inputDO)).thenReturn(mockDO);
}
```

---

## 编译验证命令

### Maven 编译命令

```bash
# 编译项目代码
mvn compile

# 编译测试代码
mvn test-compile

# 同时编译并运行测试
mvn test

# 编译并跳过测试
mvn compile -DskipTests
```

### 输出解读

**成功输出**：

```
[INFO] BUILD SUCCESS
```

**失败输出**：

```
[ERROR] COMPILATION ERROR
[ERROR] error: cannot find symbol
```

---

## 编译检查清单

### 开发前检查

- [ ] 所有被导入的类是否真实存在？
- [ ] 所有 Mock 的接口是否被测试类依赖？
- [ ] 所有 Mock 的方法是否真实存在于接口中？
- [ ] 所有字段访问是否基于实际类定义？

### 编译时检查

- [ ] 项目是否能够完全编译通过？
- [ ] 是否存在任何 [ERROR] 标记？
- [ ] 所有 import 语句是否正确？
- [ ] 所有使用的类是否真实存在？

### 编译后检查

- [ ] 所有字段类型是否与实际定义匹配？
- [ ] 所有方法参数类型是否正确？
- [ ] 所有返回值类型是否一致？
- [ ] 是否避免了使用不存在的枚举值？

---

## 常见错误排查矩阵

| 错误类型       | 症状                                               | 排查步骤           | 解决方案            |
|:-----------|:-------------------------------------------------|:---------------|:----------------|
| **类不存在**   | `cannot find symbol: class Xxx`                  | 检查 import 和类路径 | 修正 import 或类名拼写 |
| **字段不存在**  | `cannot find symbol: method setXxx`              | 打开源类查看字段       | 使用实际存在的字段       |
| **方法不存在**  | `cannot find symbol: method xxx()`               | 打开接口查看方法       | 使用实际存在的方法       |
| **类型不匹配**  | `incompatible types`                             | 检查字段类型定义       | 转换为正确类型         |
| **参数类型错误** | `method xxx cannot be applied`                   | 查看方法签名         | 修正参数类型          |
| **泛型类型错误** | `List<String> cannot be converted to List<Long>` | 检查泛型参数         | 转换为正确的泛型类型      |

---

## 相关规范引用

- 单元测试编写规范总索引：`specrules/03_coding/index.md`
- 单元测试技术栈规范：`specrules/03_coding/unit_test_technology_stack.md`
- 测试基类标准规范：`specrules/03_coding/test_base_class_standards.md`
- Mock 和断言标准规范：`specrules/03_coding/mock_and_assertion_standards.md`

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
