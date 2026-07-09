---
description: "测试基类标准规范 - 定义 UnitTestBase 和 TransactionTestBase 的标准实现、位置和初始化流程"
alwaysApply: false
globs: ["**/*Test.java"]
version: "1.0.0"
---

# 测试基类标准规范

## 概述

本规范定义了两个核心测试基类的标准实现、位置要求和使用场景。所有单元测试和集成测试都必须继承相应的基类。

---

## 单元测试基类：UnitTestBase

### 类定位

- **用途**：所有单元测试的基类
- **适用层**：Domain、Application、API 层的单元测试
- **不适用**：Infrastructure 层集成测试

### 位置要求

**标准路径模式**：

```
{module}/src/test/java/{package}/UnitTestBase.java
```

**具体示例**：

```
example-course-domain/src/test/java/com/myapp/application/course/domain/UnitTestBase.java
example-course-application/src/test/java/com/myapp/application/course/application/UnitTestBase.java
```

### 初始化流程

**第一步：检查基类是否存在**

1. 打开当前模块的 `src/test/java` 目录
2. 导航至对应的包路径（通常是 `com.myapp.application.{module}.domain` 或 `application`）
3. 检查是否存在 `UnitTestBase.java` 文件

**第二步：如果不存在，创建基类**

- 在对应包路径下创建 `UnitTestBase.java`
- 使用下面的标准模板内容

### 标准模板

```java
package {对应的包路径};

import com.google.common.collect.Lists;
import org.junit.AfterClass;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

/**
 * 单元测试基类
 * 
 * 所有单元测试都必须继承此类。该基类提供：
 * 1. Mockito JUnit Runner 支持
 * 2. 静态 Mock 的生命周期管理
 * 3. 测试间隔离
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class UnitTestBase {

    /**
     * 存储所有创建的 MockedStatic 实例
     * 用于在测试完成后自动关闭，避免内存泄漏
     */
    private static final List<MockedStatic<?>> MOCKED_STATICS = Lists.newArrayList();

    /**
     * 创建并管理静态 Mock
     * 
     * @param clazz 要 Mock 的类
     * @param <T> 泛型类型
     * @return MockedStatic 实例
     * 
     * 使用示例：
     * <pre>
     * try (MockedStatic<ApiContextHelper> mock = mockStatic(ApiContextHelper.class)) {
     *     mock.when(ApiContextHelper::getCommonParam).thenReturn(mockParam);
     *     // 测试逻辑
     * }
     * </pre>
     */
    public static <T> MockedStatic<T> mockStatic(Class<T> clazz) {
        MockedStatic<T> mockedStatic = Mockito.mockStatic(clazz);
        MOCKED_STATICS.add(mockedStatic);
        return mockedStatic;
    }

    /**
     * 清理所有 MockedStatic 实例
     * 在所有测试完成后执行，确保资源正确释放
     */
    @AfterClass
    public static void afterClass() {
        synchronized (MOCKED_STATICS) {
            MOCKED_STATICS.forEach(o -> {
                if (!o.isClosed()) {
                    o.close();
                }
            });
            MOCKED_STATICS.clear();
        }
    }
}
```

### 使用示例

**Domain 层测试示例**：

```java
package com.myapp.application.course.domain;

import org.junit.Test;
import org.mockito.Mock;

public class CourseDomainServiceImplTest extends UnitTestBase {
    
    @Mock
    private CourseRepository courseRepository;

    @Test
    public void testCreateCourse_Success() {
        // 测试逻辑
    }
}
```

**Application 层测试示例**：

```java
package com.myapp.application.course.application;

import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CourseAppServiceImplTest extends UnitTestBase {
    
    @Mock
    private CourseDomainService courseDomainService;

    @InjectMocks
    private CourseAppServiceImpl courseAppService;

    @Test
    public void testCreateCourse_Success() {
        // 测试逻辑
    }
}
```

---

## 集成测试基类：TransactionTestBase

### 类定位

- **用途**：所有集成测试的基类
- **适用层**：**仅限** Infrastructure 层
- **特点**：自动事务回滚，避免测试数据污染

### 位置要求

**标准路径**：

```
{module}-infrastructure/src/test/java/com/myapp/application/{module}/infrastructure/TransactionTestBase.java
```

**具体示例**：

```
example-course-infrastructure/src/test/java/com/myapp/application/course/infrastructure/TransactionTestBase.java
```

### 关键特性

| 特性                  | 说明                      |
|:--------------------|:------------------------|
| **@SpringBootTest** | 加载完整 Spring 上下文         |
| **@Transactional**  | 启用事务管理                  |
| **@Rollback**       | 每个测试后自动回滚，保证数据隔离        |
| **@MockBean**       | Mock 外部依赖（如 Redis、MQ 等） |

### 标准使用示例

```java
package com.myapp.application.course.infrastructure;

import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TestApplication.class)
@RunWith(SpringRunner.class)
@Rollback
@Transactional
public abstract class TransactionTestBase {
    // 集成测试的通用配置
}
```

### 外部依赖 Mock 配置

```java
package com.myapp.application.course.infrastructure;

import com.myapp.credit.api.RiskInfoMultiService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TestApplication.class)
@RunWith(SpringRunner.class)
@Rollback
@Transactional
public abstract class TransactionTestBase {

    /**
     * Mock 外部服务和组件
     * 确保集成测试不依赖真实的外部服务
     */
    @MockBean
    private com.alibaba.otter.canal.client.CanalConnector canalConnector;

    @MockBean
    private RiskInfoMultiService riskInfoMultiService;

    @MockBean(name = "courseIndexSyncProducer")
    private com.myapp.messaging.api.IProducerProcessor courseIndexSyncProducer;
}
```

---

## 基类继承规则

### ✅ 正确的继承关系

| 层              | 测试类型 | 继承的基类                 | 示例                                                     |
|:---------------|:-----|:----------------------|:-------------------------------------------------------|
| Domain         | 单元测试 | `UnitTestBase`        | `CourseDomainServiceImplTest extends UnitTestBase`     |
| Application    | 单元测试 | `UnitTestBase`        | `CourseAppServiceImplTest extends UnitTestBase`        |
| API            | 单元测试 | `UnitTestBase`        | `CourseGatewayServiceImplTest extends UnitTestBase`    |
| Infrastructure | 集成测试 | `TransactionTestBase` | `CourseRepositoryImplTest extends TransactionTestBase` |

### ❌ 错误的继承关系

| 错误示例                              | 问题          | 修正                         |
|:----------------------------------|:------------|:---------------------------|
| Domain 层继承 `TransactionTestBase`  | 过度集成化       | 改为继承 `UnitTestBase`        |
| Infrastructure 层继承 `UnitTestBase` | 无法进行真实数据库操作 | 改为继承 `TransactionTestBase` |
| 测试类直接继承 `MockitoJUnitRunner`      | 绕过基类管理      | 改为继承 `UnitTestBase`        |

---

## 生命周期管理

### UnitTestBase 生命周期

```
测试执行开始
    ↓
MockitoJUnitRunner 初始化所有 @Mock 字段
    ↓
执行 @Before 方法（如果有）
    ↓
执行 @Test 方法
    ↓
执行 @After 方法（如果有）
    ↓
执行 @AfterClass 静态清理方法
    ↓
关闭所有 MockedStatic 实例
    ↓
测试执行结束
```

### TransactionTestBase 生命周期

```
Spring ApplicationContext 初始化
    ↓
加载所有 @MockBean
    ↓
开启事务
    ↓
执行 @Before 方法（如果有）
    ↓
执行 @Test 方法
    ↓
执行 @After 方法（如果有）
    ↓
回滚事务（@Rollback）
    ↓
测试完成
```

---

## 验证清单

### UnitTestBase 创建检查

- [ ] 文件位置：`{module}/src/test/java/{package}/UnitTestBase.java`
- [ ] 类注解：`@RunWith(MockitoJUnitRunner.class)`
- [ ] 静态列表：`private static final List<MockedStatic<?>> MOCKED_STATICS`
- [ ] MockedStatic 列表：已初始化
- [ ] afterClass 方法：存在且能正确关闭所有 Mock 实例
- [ ] 导入语句：来自 `org.mockito` 包
- [ ] 访问修饰符：`public abstract`

### 测试类继承检查

- [ ] Domain 层单元测试继承 `UnitTestBase`
- [ ] Application 层单元测试继承 `UnitTestBase`
- [ ] Infrastructure 层集成测试继承 `TransactionTestBase`
- [ ] 没有跨层继承错误
- [ ] 没有直接继承 `MockitoJUnitRunner` 的测试类

---

## 相关规范引用

- 单元测试编写规范总索引：`specrules/03_coding/index.md`
- 单元测试技术栈规范：`specrules/03_coding/unit_test_technology_stack.md`
- Mock 和断言规范：`specrules/03_coding/mock_and_assertion_standards.md`
- 编译通过检查规范：`specrules/03_coding/unit_test_compilation_standards.md`

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
