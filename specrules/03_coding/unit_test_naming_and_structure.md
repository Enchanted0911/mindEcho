---
description: "单元测试命名和结构规范 - 定义测试类命名、包结构、测试方法命名规范"
alwaysApply: false
globs: ["**/*Test.java"]
version: "1.0.0"
---

# 单元测试命名和结构规范

## 概述

本规范定义了单元测试文件的命名规则、包结构安排和测试方法的命名约定，确保项目内的测试代码风格统一。

---

## 测试文件命名规范

### 测试类名规范

**命名规则**：`{被测试类名}Test`

**规则说明**：

- 测试类名 = 被测试类名 + `Test` 后缀
- 使用帕斯卡命名法（PascalCase）
- 名称应该清晰反映被测试类的功能

**正确示例**：

| 被测试类                       | 测试类名                           |
|:---------------------------|:-------------------------------|
| `CourseDomainServiceImpl`  | `CourseDomainServiceImplTest`  |
| `CourseAppServiceImpl`     | `CourseAppServiceImplTest`     |
| `CourseRepositoryImpl`     | `CourseRepositoryImplTest`     |
| `CourseGatewayServiceImpl` | `CourseGatewayServiceImplTest` |
| `CourseDAOImpl`            | `CourseDAOImplTest`            |

**错误示例**：

| ❌ 错误命名                  | 原因           |
|:------------------------|:-------------|
| `CourseServiceTest`     | 模糊不清，不知道测哪个类 |
| `testCourseService`     | 不符合类命名规范     |
| `Course_Test`           | 使用下划线        |
| `CourseServiceTestCase` | 过长，后缀应为 Test |

---

## 测试包结构规范

### 包结构对应原则

**核心规则**：测试类的包结构应与被测试类保持一致。

**对应关系**：

```
被测试类包结构：
  com.myapp.application.course.domain.service.impl
  
对应测试包结构：
  com.myapp.application.course.domain.service.impl  // ✅ 完全相同
```

### 具体示例

#### Domain 层示例

```
源代码：
  example-course-domain/src/main/java/
    com/myapp/application/course/domain/
      service/
        ├─ CourseDomainService.java (接口)
        └─ impl/
           └─ CourseDomainServiceImpl.java (实现)

测试代码：
  example-course-domain/src/test/java/
    com/myapp/application/course/domain/
      service/impl/
        └─ CourseDomainServiceImplTest.java  ✅ 包结构完全对应
```

#### Application 层示例

```
源代码：
  example-course-application/src/main/java/
    com/myapp/application/course/application/
      service/
        ├─ CourseAppService.java (接口)
        └─ impl/
           └─ CourseAppServiceImpl.java (实现)

测试代码：
  example-course-application/src/test/java/
    com/myapp/application/course/application/
      service/impl/
        └─ CourseAppServiceImplTest.java  ✅ 包结构完全对应
```

#### Infrastructure 层示例

```
源代码：
  example-course-infrastructure/src/main/java/
    com/myapp/application/course/infrastructure/
      repository/
        └─ CourseRepositoryImpl.java

测试代码：
  example-course-infrastructure/src/test/java/
    com/myapp/application/course/infrastructure/
      repository/
        └─ CourseRepositoryImplTest.java  ✅ 包结构完全对应
```

### 包结构检查清单

- [ ] 测试类文件夹路径与源代码完全相同？
- [ ] 包声明与源代码包完全相同？
- [ ] 没有创建额外的测试特定包？
- [ ] 所有测试文件都在 `src/test/java` 下？

---

## 测试方法命名规范

### 命名格式

**标准格式**：`test{BusinessFunction}_{Scenario}_{ExpectedResult}`

**格式解析**：

| 部分                     | 说明           | 示例                                       |
|:-----------------------|:-------------|:-----------------------------------------|
| **test**               | 固定前缀，标识为测试方法 | `test`                                   |
| **{BusinessFunction}** | 被测试的业务功能名    | `CreateCourse`, `UpdateCourse`           |
| **_{Scenario}**        | 测试场景         | `_Success`, `_NotFound`, `_InvalidParam` |
| **_{ExpectedResult}**  | 期望结果         | （包含在 Scenario 中）                         |

### 具体示例

#### 场景 1：成功场景

```java
// ✅ 正确：清晰表达成功场景
@Test
public void testCreateCourse_Success() {
    // 测试代码
}

@Test
public void testUpdateCourse_Success() {
    // 测试代码
}

@Test
public void testDeleteCourse_Success() {
    // 测试代码
}
```

#### 场景 2：失败场景

```java
// ✅ 正确：清晰表达失败场景
@Test
public void testFindById_NotFound() {
    // 测试代码
}

@Test
public void testCreateCourse_InvalidParam() {
    // 测试代码
}

@Test
public void testUpdateCourse_PermissionDenied() {
    // 测试代码
}
```

#### 场景 3：边界场景

```java
// ✅ 正确：清晰表达边界场景
@Test
public void testQueryCourses_EmptyResult() {
    // 测试代码
}

@Test
public void testCreateCourse_NullParameter() {
    // 测试代码
}

@Test
public void testBatchDelete_ZeroItems() {
    // 测试代码
}
```

#### 场景 4：异常场景

```java
// ✅ 正确：清晰表达异常场景
@Test(expected = RuntimeException.class)
public void testCreateCourse_ThrowException() {
    // 测试代码
}

@Test
public void testFindById_InvalidRegion() {
    // 测试代码
}
```

### 错误的命名示例

| ❌ 错误命名                             | 问题           | ✅ 正确命名                       |
|:-----------------------------------|:-------------|:-----------------------------|
| `test1()`                          | 无意义，无法识别测试内容 | `testCreateCourse_Success()` |
| `testXxx()`                        | 模糊，不知道测什么    | `testCreateCourse_Success()` |
| `createCourseTest()`               | 后缀在末尾不规范     | `testCreateCourse_Success()` |
| `test_create_course()`             | 使用下划线而非驼峰    | `testCreateCourse_Success()` |
| `shouldCreateCourseSuccessfully()` | 过长且冗余        | `testCreateCourse_Success()` |
| `testCreateCourse`                 | 缺少场景信息       | `testCreateCourse_Success()` |

---

## 测试方法结构规范

### 标准的三段式结构

所有测试方法都应遵循 **Given-When-Then** 结构：

```java
@Test
public void testCreateCourse_Success() {
    // ========== Given ==========
    // 准备测试数据
    CourseBO courseBO = new CourseBO();
    courseBO.setCode("COURSE001");
    courseBO.setName("测试课程");
    courseBO.setRegion("CN");

    // ========== When ==========
    // 执行被测试方法
    CourseBO result = courseAppService.createCourse(courseBO);

    // ========== Then ==========
    // 验证结果
    assertNotNull("结果不应为null", result);
    assertNotNull("课程ID不应为null", result.getId());
    assertEquals("课程编码应匹配", "COURSE001", result.getCode());
    
    // 验证 Mock 调用（如果有）
    verify(courseDomainService).createCourse(any(CourseDO.class));
}
```

### 三段式详解

| 段落        | 职责                 | 示例                                   |
|:----------|:-------------------|:-------------------------------------|
| **Given** | 准备测试数据、设置 Mock 返回值 | 创建 CourseBO、Mock DAO 返回值             |
| **When**  | 执行被测试的业务方法         | 调用 `courseAppService.createCourse()` |
| **Then**  | 验证结果和 Mock 调用      | 断言结果、verify Mock 调用                  |

### 不同场景的结构示例

#### 成功场景

```java
@Test
public void testCreateCourse_Success() {
    // Given
    CourseBO input = createTestCourseBO();
    when(courseDomainService.createCourse(any())).thenReturn(createTestCourseDO());

    // When
    CourseBO result = courseAppService.createCourse(input);

    // Then
    assertNotNull("结果不应为null", result);
    verify(courseDomainService, times(1)).createCourse(any());
}
```

#### 异常场景

```java
@Test(expected = RuntimeException.class)
public void testCreateCourse_InvalidParam() {
    // Given
    CourseBO input = new CourseBO();  // 空对象，缺少必要字段
    
    // When & Then
    courseAppService.createCourse(input);  // 应抛出异常
}
```

#### 边界场景

```java
@Test
public void testQueryCourses_EmptyResult() {
    // Given
    when(courseRepository.findList(any())).thenReturn(new ArrayList<>());

    // When
    List<CourseBO> result = courseAppService.queryCourses(new QueryBO());

    // Then
    assertNotNull("结果不应为null", result);
    assertEquals("结果应为空列表", 0, result.size());
}
```

---

## 测试类完整示例

### Domain 层单元测试示例

```java
package com.myapp.application.course.domain.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CourseDomainServiceImplTest extends UnitTestBase {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseDomainServiceImpl courseDomainService;

    private CourseDO testCourseDO;

    @Before
    public void setUp() {
        testCourseDO = new CourseDO();
        testCourseDO.setId(1L);
        testCourseDO.setCode("COURSE001");
        testCourseDO.setTitle("测试课程");
        testCourseDO.setRegion("CN");
    }

    @Test
    public void testCreateCourse_Success() {
        // Given
        when(courseRepository.insert(any(CourseDO.class))).thenReturn(1);

        // When
        CourseDO result = courseDomainService.createCourse(testCourseDO);

        // Then
        assertNotNull("结果不应为null", result);
        assertEquals("课程代码应匹配", "COURSE001", result.getCode());
        verify(courseRepository, times(1)).insert(any(CourseDO.class));
    }

    @Test(expected = RuntimeException.class)
    public void testCreateCourse_InvalidParam() {
        // Given
        CourseDO invalidCourse = new CourseDO();
        // 不设置必要字段

        // When & Then
        courseDomainService.createCourse(invalidCourse);
    }
}
```

### Application 层单元测试示例

```java
package com.myapp.application.course.application.service.impl;

import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CourseAppServiceImplTest extends UnitTestBase {

    @Mock
    private CourseDomainService courseDomainService;

    @InjectMocks
    private CourseAppServiceImpl courseAppService;

    @Test
    public void testCreateCourse_Success() {
        // Given
        CourseBO courseBO = new CourseBO();
        courseBO.setCode("COURSE001");
        courseBO.setName("测试课程");
        courseBO.setRegion("CN");

        CourseDO expectedDO = new CourseDO();
        expectedDO.setId(1L);
        when(courseDomainService.createCourse(any(CourseDO.class))).thenReturn(expectedDO);

        // When
        CourseBO result = courseAppService.createCourse(courseBO);

        // Then
        assertNotNull("结果不应为null", result);
        assertNotNull("ID不应为null", result.getId());
        verify(courseDomainService, times(1)).createCourse(any(CourseDO.class));
    }
}
```

---

## 命名和结构检查清单

### 测试类命名检查

- [ ] 测试类名是否以 `Test` 后缀结尾？
- [ ] 测试类名是否反映了被测试类的功能？
- [ ] 类名是否使用了帕斯卡命名法？
- [ ] 没有使用无意义的名称（如 `Test1`, `XXXTest`）？

### 包结构检查

- [ ] 测试包结构是否与源代码完全相同？
- [ ] 测试文件是否都在 `src/test/java` 下？
- [ ] 没有创建额外的测试特定包（如 `test`、`tests` 等）？

### 测试方法命名检查

- [ ] 每个测试方法是否以 `test` 开头？
- [ ] 测试方法名是否包含被测试的功能名？
- [ ] 测试方法名是否包含测试场景信息？
- [ ] 没有使用无意义的方法名（如 `test1`、`testXxx`）？
- [ ] 是否避免了过长的方法名？

### 测试方法结构检查

- [ ] 是否遵循了 Given-When-Then 结构？
- [ ] Given 部分是否准备了完整的测试数据？
- [ ] When 部分是否只执行了一个业务方法？
- [ ] Then 部分是否包含了必要的断言？
- [ ] 是否验证了关键 Mock 调用？

---

## 相关规范引用

- 单元测试编写规范总索引：`specrules/03_coding/index.md`
- 单元测试技术栈规范：`specrules/03_coding/unit_test_technology_stack.md`
- 测试基类标准规范：`specrules/03_coding/test_base_class_standards.md`
- Mock 和断言标准规范：`specrules/03_coding/mock_and_assertion_standards.md`

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
