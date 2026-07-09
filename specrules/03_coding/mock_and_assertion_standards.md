---
description: "Mock 和断言标准规范 - 定义 Mock 使用的核心原则、静态 Mock 管理、断言方法规范"
alwaysApply: false
globs: ["**/*Test.java"]
version: "1.0.0"
---

# Mock 和断言标准规范

## 概述

本规范定义了单元测试中 Mock 对象的使用原则、静态 Mock 的管理方式以及断言的标准用法。

---

## Mock 基本原则

### 原则 1：真实性检查（强制）

**定义**：只能 Mock 真实存在的接口和方法。

**验证步骤**：

1. 打开被测试类源代码
2. 确认被 Mock 的接口确实被依赖注入（`@Autowired` 或构造器注入）
3. 确认被 Mock 的方法真实存在于该接口
4. 确认方法签名（参数类型、返回值类型）完全一致

**示例**：

```java
// ✅ 正确：被测试类确实依赖 CourseRepository
@Service
public class CourseAppServiceImpl {
    @Autowired
    private CourseRepository courseRepository;  // 真实依赖
}

// Mock 声明
@Mock
private CourseRepository courseRepository;  // ✅ 正确

// ❌ 错误：Mock 不存在的接口
@Mock
private NonExistentService service;  // 禁止
```

### 原则 2：方法签名一致（强制）

**定义**：Mock 的方法签名必须与实际接口定义完全一致。

**检查项**：

- [ ] 方法名完全相同
- [ ] 参数数量相同
- [ ] 参数类型顺序完全一致
- [ ] 返回值类型一致

**示例**：

```java
// 被测试类中的实际调用
CourseDO result = courseRepository.findById(1L, "CN");

// Mock 配置（必须完全匹配）
when(courseRepository.findById(eq(1L), eq("CN"))).thenReturn(mockCourseDO);

// ✅ 参数数量和类型一致
// ❌ 错误：方法签名不一致
when(courseRepository.findById(1L)).thenReturn(mockCourseDO);  // 参数数量错误
```

### 原则 3：依赖验证（强制执行）

**执行步骤**：

```
开发 Mock 前
    ↓
打开被测试类
    ↓
查看所有 @Autowired 或构造器参数
    ↓
确认是否有要 Mock 的依赖
    ↓
检查接口中是否有要调用的方法
    ↓
验证方法签名
    ↓
开始编写 Mock 代码
```

### 原则 4：禁止虚构（绝对禁止）

**禁止清单**：

| 禁止项         | 说明              | 后果      |
|:------------|:----------------|:--------|
| Mock 不存在的接口 | 接口在被测试类中根本没有被依赖 | 编译错误    |
| Mock 不存在的方法 | 接口中不存在该方法定义     | 编译错误    |
| 错误的参数类型     | 参数类型与实际方法不符     | 运行时类型错误 |
| 虚构的返回值类型    | 返回值类型不符         | 类型转换失败  |

### 原则 5：静态 Mock 管理（强制）

**核心规则**：

- ❌ **禁止**在类级别创建静态 Mock
- ❌ **禁止**在 `@Before/@setUp` 方法中创建静态 Mock
- ✅ **必须**在测试方法中使用 try-with-resources 管理
- ✅ **必须**保证每个测试方法独立的 Mock 实例

---

## Static Mock 管理规范

### ✅ 正确的静态 Mock 使用

```java
@Test
public void testMethodWithStaticMock() {
    // 在测试方法中使用 try-with-resources 管理静态 Mock
    try (MockedStatic<ApiContextHelper> apiMock = mockStatic(ApiContextHelper.class);
         MockedStatic<MessageBuilder> messageMock = mockStatic(MessageBuilder.class)) {

        // Mock 配置
        ApiCommonParam mockParam = mock(ApiCommonParam.class);
        when(mockParam.getRegion()).thenReturn("CN");
        apiMock.when(ApiContextHelper::getCommonParam).thenReturn(mockParam);

        messageMock.when(() ->
            MessageBuilder.buildMessage(any(), any()))
            .thenReturn(mockMessage);

        // 执行测试逻辑
        CourseBO result = courseAppService.createCourse(courseBO);

        // 验证结果
        assertNotNull("结果不应为null", result);

        // 验证 Mock 调用
        messageMock.verify(() ->
            MessageBuilder.buildMessage(any(), any()), times(1));

    } // try-with-resources 自动关闭所有 Mock 实例
}
```

### ❌ 错误的静态 Mock 使用

```java
public class BadExampleTest extends UnitTestBase {

    // ❌ 错误：在类级别声明静态 Mock 变量
    private MockedStatic<ApiContextHelper> apiMock;
    private MockedStatic<MessageBuilder> messageMock;

    @Before
    public void setUp() {
        // ❌ 错误：在 setUp 中创建静态 Mock
        apiMock = mockStatic(ApiContextHelper.class);
        messageMock = mockStatic(MessageBuilder.class);
    }

    @After
    public void tearDown() {
        // ❌ 错误：手动管理关闭
        if (apiMock != null) {
            apiMock.close();
        }
        if (messageMock != null) {
            messageMock.close();
        }
    }

    @Test
    public void testMethod() {
        // ❌ 错误：使用全局变量
        apiMock.when(ApiContextHelper::getCommonParam).thenReturn(mockParam);
        // 后续测试可能失败，因为 Mock 未正确隔离
    }
}
```

### 静态 Mock 最佳实践

| 实践        | 说明                               |
|:----------|:---------------------------------|
| **方法级隔离** | 每个测试方法都有独立的 MockedStatic 实例      |
| **自动释放**  | 使用 try-with-resources 自动管理资源生命周期 |
| **避免冲突**  | 防止测试间的 Mock 污染                   |
| **内存安全**  | 确保没有内存泄漏或资源未释放                   |

---

## 常见静态 Mock 使用场景

### 场景 1：Mock 工具类静态方法

```java
@Test
public void testWithUtilityClass() {
    try (MockedStatic<DateUtils> mock = mockStatic(DateUtils.class)) {
        mock.when(DateUtils::now).thenReturn(1693478400000L);
        
        // 执行测试
        CourseBO result = courseAppService.createCourse(courseBO);
        
        // 验证结果
        assertEquals("时间戳应为 Mock 值", 1693478400000L, result.getCreatedAt());
    }
}
```

### 场景 2：Mock 消息构建器静态方法

```java
@Test
public void testWithMessageBuilder() {
    try (MockedStatic<AuditMessageBuilder> mock = mockStatic(AuditMessageBuilder.class)) {
        AuditMessage mockMessage = new AuditMessage();
        mockMessage.setId(1L);
        
        mock.when(() ->
            AuditMessageBuilder.buildMessage(any(AuditInfoDO.class), any(Integer.class)))
            .thenReturn(mockMessage);
        
        // 执行测试
        // ...
        
        // 验证调用
        mock.verify(() ->
            AuditMessageBuilder.buildMessage(any(AuditInfoDO.class), any(Integer.class)), times(1));
    }
}
```

### 场景 3：Mock 多个静态类

```java
@Test
public void testWithMultipleStaticClasses() {
    try (MockedStatic<ClassA> mockA = mockStatic(ClassA.class);
         MockedStatic<ClassB> mockB = mockStatic(ClassB.class);
         MockedStatic<ClassC> mockC = mockStatic(ClassC.class)) {
        
        // 配置多个 Mock
        mockA.when(ClassA::staticMethodA).thenReturn("resultA");
        mockB.when(ClassB::staticMethodB).thenReturn("resultB");
        mockC.when(() -> ClassC.staticMethodC(any())).thenReturn("resultC");
        
        // 执行测试
        // ...
    }
}
```

---

## 静态 Mock 参数匹配规范

### ✅ 正确的参数匹配

```java
// 规则：要么全部使用具体值，要么全部使用匹配器
// 规则：使用 Objects.requireNonNull 直接校验，无需 Mock 标准库方法
    // ✅ 正确：直接使用 Objects.requireNonNull 进行验证
    Objects.requireNonNull(param, "参数不能为null");
    
    // 或使用 if-throw 模式手动校验
    if (!org.apache.commons.lang3.StringUtils.isNotBlank(param)) {
        throw new IllegalArgumentException("参数不能为空");
    }
```

### ❌ 错误的参数匹配

```java
// ❌ 错误：Mock 标准 JDK 方法（Objects.requireNonNull 是 final 方法，不应 Mock）
// ✅ 正确做法：直接验证参数值，无需 Mock
Object param = null;
try {
    Objects.requireNonNull(param, "参数不能为null");
    fail("应抛出 NullPointerException");
} catch (NullPointerException e) {
    // 预期异常
}
```

---

## 断言规范

### 标准断言方法

```java
// ✅ 必须使用 JUnit 4 Assert
import static org.junit.Assert.*;

// 基础断言方法
assertEquals("错误消息", expected, actual);           // 相等性
assertNotEquals("错误消息", unexpected, actual);      // 不相等
assertNull("错误消息", object);                       // 为 null
assertNotNull("错误消息", object);                    // 不为 null
assertTrue("错误消息", condition);                    // 真
assertFalse("错误消息", condition);                   // 假
assertSame("错误消息", expected, actual);             // 引用相同
assertNotSame("错误消息", unexpected, actual);        // 引用不同
assertArrayEquals("错误消息", expectedArray, actualArray);  // 数组相等

// ❌ 禁止使用 JUnit 5 风格
import static org.junit.jupiter.api.Assertions.*;     // 禁止
```

### 断言消息规范

| 规范       | 说明              | 示例                                            |
|:---------|:----------------|:----------------------------------------------|
| **必须提供** | 所有断言都应包含清晰的错误消息 | `assertEquals("课程ID不匹配", 1L, result.getId())` |
| **消息内容** | 描述期望的结果         | `"用户应该创建成功"`                                  |
| **消息语言** | 统一使用中文，便于理解     | ✅ `"课程不能为空"`                                  |
| **避免模糊** | 不要使用模糊的消息       | ❌ `"测试失败"`                                    |

### 断言使用示例

```java
@Test
public void testCreateCourse_Success() {
    // Given
    CourseBO courseBO = createTestCourseBO();
    
    // When
    CourseBO result = courseAppService.createCourse(courseBO);
    
    // Then
    assertNotNull("创建后结果不应为null", result);
    assertNotNull("课程ID不应为null", result.getId());
    assertEquals("课程编码应匹配", courseBO.getCode(), result.getCode());
    assertEquals("课程名称应匹配", courseBO.getName(), result.getName());
    assertTrue("课程状态应为待审核", result.getStatus() == CourseStatus.PENDING.getValue());
}
```

---

## Mock 违规处理流程

### 发现违规时的处理步骤

```
发现 Mock 了不存在的方法
    ↓
立即停止开发
    ↓
重新打开被测试类
    ↓
检查实际代码结构
    ↓
修正 Mock 对象（只 Mock 真实存在的）
    ↓
验证方法调用（确保都真实存在）
    ↓
编译验证
    ↓
运行测试
```

---

## Mock 和断言检查清单

### Mock 使用检查

- [ ] Mock 的接口是否对应被测试类的真实依赖？
- [ ] Mock 的方法签名是否与实际接口完全一致？
- [ ] Mock 的返回值类型是否正确？
- [ ] 是否避免了 PowerMockito 的使用？
- [ ] 是否避免了 Mock 不存在的方法？
- [ ] verify 调用的方法是否真实存在？
- [ ] verify 的参数类型是否与实际调用一致？

### 静态 Mock 检查

- [ ] 静态 Mock 是否使用 try-with-resources 方式管理？
- [ ] 是否避免了在 setUp 方法中创建静态 Mock？
- [ ] 是否避免了全局静态 Mock 变量？
- [ ] 静态 Mock 的作用域是否限制在单个测试方法内？
- [ ] 是否正确使用了参数匹配器？

### 断言检查

- [ ] 所有断言都包含清晰的错误消息吗？
- [ ] 错误消息是否使用中文？
- [ ] 是否使用了 JUnit 4 Assert？
- [ ] 是否避免了 JUnit 5 Assertions？
- [ ] 断言的期望值和实际值顺序是否正确？

---

## 相关规范引用

- 单元测试编写规范总索引：`specrules/03_coding/index.md`
- 单元测试技术栈规范：`specrules/03_coding/unit_test_technology_stack.md`
- 测试基类标准规范：`specrules/03_coding/test_base_class_standards.md`
- 编译通过检查规范：`specrules/03_coding/unit_test_compilation_standards.md`

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
