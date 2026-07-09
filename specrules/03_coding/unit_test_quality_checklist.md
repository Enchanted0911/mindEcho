---
description: "单元测试质量检查清单 - 汇总单元测试开发全过程中的检查项和验收标准"
alwaysApply: false
globs: ["**/*Test.java"]
version: "1.0.0"
---

# 单元测试质量检查清单

## 概述

本规范汇总了单元测试开发的全生命周期中需要检查的项目。按照开发阶段分为：**开发前**、**开发中**、**编译检查**、**测试数据**、*
*代码审查** 五个阶段。

---

## 阶段 1：开发前强制检查

在开始编写任何测试代码前，必须完成以下检查。

### 1.1 代码结构分析

- [ ] 被测试类的源代码是否已打开并阅读？
- [ ] 所有 `@Autowired` 或构造器注入的依赖是否已识别？
- [ ] 每个依赖的接口定义是否已查看？
- [ ] 被测试方法的实际调用链是否已跟踪？

**执行方法**：

```
1. 打开 IDE 中的被测试类
2. 按 F12 或 Ctrl+Click 跳转到依赖接口
3. 查看接口的所有方法定义
4. 记录被测试方法调用的实际依赖方法
```

### 1.2 基类检查

- [ ] 当前模块的 `src/test/java/{package}/` 下是否存在 `UnitTestBase`？
- [ ] 如果不存在，是否已按照 [@ref:test_base_class_standards.md] 创建？
- [ ] 基类是否使用了正确的 `@RunWith(MockitoJUnitRunner.class)` 注解？
- [ ] 基类是否包含了静态 Mock 管理代码？

**执行方法**：

```bash
# 检查基类是否存在
find {module}/src/test/java -name "UnitTestBase.java"

# 如果不存在，在对应包下创建
```

### 1.3 技术栈确认

- [ ] 项目的 pom.xml 中是否包含 JUnit 4（4.13.2+）依赖？
- [ ] 是否包含 mockito-inline（4.5.1+）依赖？
- [ ] 是否避免了 JUnit 5 依赖？
- [ ] 是否避免了 PowerMockito 依赖？

**参考**：[@ref:unit_test_technology_stack.md]

---

## 阶段 2：开发前依赖验证

严格执行依赖验证流程，确保所有 Mock 都基于真实存在的依赖。

### 2.1 依赖真实性检查

- [ ] 是否执行了"打开被测试类 → 确认依赖 → 检查接口" 的完整流程？
- [ ] Mock 的每个接口是否都出现在被测试类的字段中？
- [ ] 每个接口的来源（@Autowired 或构造器）是否已确认？
- [ ] 是否避免了 Mock 不存在的服务？

**检查示例**：

```java
// 被测试类：CourseAppServiceImpl
@Service
public class CourseAppServiceImpl {
    @Autowired
    private CourseDomainService courseDomainService;     // ✅ 真实依赖
    
    @Autowired
    private CategoryDomainService categoryDomainService; // ✅ 真实依赖
}

// 测试类中的 Mock：应该与上面完全一致
@Mock
private CourseDomainService courseDomainService;         // ✅ 正确

@Mock
private NonExistentService service;                      // ❌ 禁止！
```

### 2.2 方法存在性检查

- [ ] 被测试方法是否真实存在于被测试类中？
- [ ] 方法的参数和返回值是否已确认？
- [ ] Mock 的方法是否都存在于对应的接口中？
- [ ] 方法签名（参数类型、顺序）是否完全一致？

**检查示例**：

```java
// 接口定义
interface CourseDomainService {
    CourseDO createCourse(CourseDO courseDO);  // 实际方法签名
    CourseDO getCourseById(Long id, String region);  // 需要两个参数
}

// Mock 配置应该完全匹配
when(courseDomainService.createCourse(any(CourseDO.class))).thenReturn(mockDO);  // ✅
when(courseDomainService.getCourseById(1L, "CN")).thenReturn(mockDO);            // ✅
when(courseDomainService.nonExistentMethod()).thenReturn(mockDO);                // ❌
```

---

## 阶段 3：编译检查强制清单

所有单元测试必须能够完全编译通过。编译错误是第一优先级问题。

### 3.1 编译错误零容忍

- [ ] 项目是否能够执行 `mvn compile` 完全通过？
- [ ] 是否存在任何 `[ERROR]` 标记在输出中？
- [ ] 所有代码是否都没有语法错误？
- [ ] 所有声明是否都完整（没有缺少分号等）？

**执行命令**：

```bash
mvn compile
# 或编译测试代码
mvn test-compile
```

### 3.2 导入和类检查

- [ ] 所有 import 语句是否指向真实存在的类？
- [ ] 是否避免了 import 不存在的包？
- [ ] 所有使用的类是否都能通过编译解析？
- [ ] 没有循环依赖导致的编译错误？

**常见问题**：

```java
// ❌ 错误：import 不存在的类
import com.example.nonexistent.NonExistentClass;

// ✅ 正确：import 真实存在的类
import com.example.application.course.domain.model.CourseDO;
```

### 3.3 字段存在性检查

- [ ] 所有使用的字段是否都存在于实体类中？
- [ ] 字段名拼写是否完全正确？
- [ ] 所有 getter/setter 方法是否都真实存在？
- [ ] 是否避免了访问不存在的字段？

参考：[@ref:unit_test_compilation_standards.md#优先级-2字段存在性检查]

### 3.4 方法存在性检查

- [ ] 所有调用的方法是否都存在？
- [ ] Mock 的方法是否都真实存在于接口中？
- [ ] verify 的方法是否都真实存在？
- [ ] 方法参数数量是否正确？

参考：[@ref:unit_test_compilation_standards.md#优先级-3方法存在性检查]

### 3.5 类型匹配检查

- [ ] 所有字段类型是否与实际定义完全匹配？
- [ ] 所有方法参数类型是否正确？
- [ ] 所有返回值类型是否一致？
- [ ] 泛型类型是否完全匹配（如 `List<Long>` vs `List<String>`）？

参考：[@ref:unit_test_compilation_standards.md#优先级-4类型匹配检查]

---

## 阶段 4：测试数据强制检查

确保测试数据的真实性和完整性。

### 4.1 对象字段一致性

- [ ] 测试对象的字段是否与实际 DO/Entity 类完全一致？
- [ ] 是否避免了设置不存在的字段？
- [ ] 是否避免了字段类型不匹配？
- [ ] 是否包含了所有必要的字段（region、ctime、utime 等）？

**示例**：

```java
// ✅ 正确：只设置存在的字段，类型正确
CourseDO courseDO = new CourseDO();
courseDO.setId(1L);
courseDO.setCode("COURSE001");
courseDO.setTitle("测试课程");
courseDO.setStatus(1);          // 使用真实存在的状态值
courseDO.setRegion("CN");
courseDO.setCtime(System.currentTimeMillis());

// ❌ 错误：设置不存在的字段或类型错误
courseDO.setId("1");            // ID 是 Long 类型
courseDO.setNonExistentField("value");  // 字段不存在
courseDO.setStatus(999);        // 不存在的状态值
```

### 4.2 枚举值检查

- [ ] 使用的枚举值是否真实存在？
- [ ] 枚举值的类型是否正确？
- [ ] 是否避免了虚构的枚举值？

**示例**：

```java
// ✅ 正确：使用真实存在的枚举值
courseDO.setStatus(CourseStatus.DRAFT.getValue());

// ❌ 错误：使用不存在的枚举值
courseDO.setStatus(999);
courseDO.setStatus(CourseStatus.NONEXISTENT.getValue());
```

### 4.3 必要字段完整性

- [ ] region 字段是否已设置？
- [ ] ctime 字段是否已设置？
- [ ] utime 字段是否已设置？
- [ ] 其他业务必要字段是否都已设置？

### 4.4 字段差异处理

- [ ] DO 对象与 Entity 对象的字段是否有差异？
- [ ] 字段差异是否已正确处理（在转换方法中）？
- [ ] 转换逻辑是否已在测试中验证？

参考：[@ref:test_base_class_standards.md]

---

## 阶段 5：Mock 强制检查

确保所有 Mock 的使用都符合规范。

### 5.1 依赖注入检查

- [ ] 是否执行了依赖验证步骤？
- [ ] Mock 的接口是否对应被测试类的真实依赖？
- [ ] 是否避免了 Mock 不存在的接口？

参考：[@ref:mock_and_assertion_standards.md#原则-3依赖验证强制执行]

### 5.2 Mock 方法配置检查

- [ ] Mock 的方法签名是否与实际接口完全一致？
- [ ] Mock 的返回值类型是否正确？
- [ ] 参数匹配器的使用是否正确（不混合具体值和匹配器）？

### 5.3 静态 Mock 检查

- [ ] 静态 Mock 是否使用 try-with-resources 方式管理？
- [ ] 是否避免了在 setUp 方法中创建静态 Mock？
- [ ] 是否避免了全局静态 Mock 变量？
- [ ] 静态 Mock 的作用域是否限制在单个测试方法内？

参考：[@ref:mock_and_assertion_standards.md#static-mock-管理规范]

### 5.4 Mock 调用验证检查

- [ ] verify 的方法是否真实存在？
- [ ] verify 的参数类型是否与实际调用一致？
- [ ] 是否正确使用了 `times()`、`atLeastOnce()` 等次数指定？
- [ ] 是否避免了验证不存在的方法？

---

## 阶段 6：代码审查强制检查

检查测试代码的质量和可读性。

### 6.1 测试逻辑清晰性

- [ ] 测试逻辑是否清晰易懂？
- [ ] 是否遵循了 Given-When-Then 结构？
- [ ] 是否有多余的复杂逻辑？

### 6.2 命名规范

- [ ] 测试类名是否以 Test 后缀结尾？
- [ ] 测试方法名是否遵循 `test{Function}_{Scenario}` 格式？
- [ ] 是否避免了无意义的名称（test1、testXxx 等）？

参考：[@ref:unit_test_naming_and_structure.md]

### 6.3 断言规范

- [ ] 所有断言是否都包含清晰的错误消息？
- [ ] 错误消息是否使用中文？
- [ ] 是否使用了 JUnit 4 Assert？
- [ ] 是否避免了 JUnit 5 Assertions？

### 6.4 测试数据独立性

- [ ] 测试数据是否独立，不相互影响？
- [ ] 是否通过 setUp 方法进行必要的初始化？
- [ ] 是否在 tearDown 方法中进行必要的清理？

### 6.5 代码重复性

- [ ] 是否存在测试代码重复？
- [ ] 是否应该提取公共方法（如 createTestXxx）？
- [ ] 是否避免了复制粘贴而来的测试？

### 6.6 测试基类继承

- [ ] 是否正确继承了 UnitTestBase（单元测试）或 TransactionTestBase（集成测试）？
- [ ] 是否避免了跨层继承错误？
- [ ] 是否避免了直接继承 MockitoJUnitRunner？

参考：[@ref:test_base_class_standards.md#基类继承规则]

---

## 阶段 7：测试运行验证

确保测试能够正确运行和通过。

### 7.1 编译验证

- [ ] 是否执行了 `mvn compile` 并成功？
- [ ] 是否执行了 `mvn test-compile` 并成功？
- [ ] 所有编译错误是否都已修复？

### 7.2 单个测试类运行

- [ ] 单个测试类是否能够独立运行？
- [ ] 测试输出是否显示所有测试都通过？

**执行命令**：

```bash
mvn test -Dtest=YourTestClass -DfailIfNoTests=false
```

### 7.3 单个测试方法运行

- [ ] 单个测试方法是否能够独立运行？
- [ ] 是否通过了该特定方法的测试？

**执行命令**：

```bash
mvn test -Dtest=YourTestClass#testMethod -DfailIfNoTests=false
```

### 7.4 覆盖率验证

- [ ] 是否达到了 80% 的覆盖率（最低要求）？
- [ ] 核心业务逻辑的覆盖率是否达到 90%+？
- [ ] 是否包含了异常场景的测试？
- [ ] 是否包含了边界值测试？
- [ ] 是否包含了空值处理测试？

---

## 综合检查矩阵

### 快速检查表

| 检查项      | 检查方法          | 通过标准                                  |
|:---------|:--------------|:--------------------------------------|
| 编译通过     | `mvn compile` | 0 errors                              |
| 测试类命名    | 查看文件名         | `{ClassName}Test.java`                |
| 包结构一致    | 比对源代码路径       | 完全相同                                  |
| 基类继承     | 查看类声明         | 继承 UnitTestBase 或 TransactionTestBase |
| 字段存在性    | 对比源代码         | 所有字段都存在                               |
| 方法存在性    | 查看接口定义        | 所有方法都存在                               |
| 类型一致性    | 检查参数类型        | 所有类型都正确                               |
| Mock 真实性 | 验证依赖注入        | 所有 Mock 都对应真实依赖                       |
| 静态 Mock  | 查看代码结构        | 使用 try-with-resources                 |
| 断言完整     | 查看 Then 部分    | 都有错误消息                                |

---

## 常见问题排查矩阵

| 问题             | 症状                                | 排查步骤         | 解决方案                                           |
|:---------------|:----------------------------------|:-------------|:-----------------------------------------------|
| **编译错误**       | [ERROR] 标记                        | 查看错误信息       | 按 [@ref:unit_test_compilation_standards.md] 处理 |
| **字段错误**       | cannot find symbol: method setXxx | 打开源类查看字段     | 使用实际存在的字段                                      |
| **Mock 失败**    | Mock 对象为 null                     | 检查 @Mock 注解  | 确认基类是否正确                                       |
| **断言失败**       | expected vs actual 不匹配            | 检查期望值        | 验证测试数据或修正断言                                    |
| **依赖错误**       | 无法注入依赖                            | 检查依赖定义       | 验证接口是否真实存在                                     |
| **静态 Mock 冲突** | static mocking already registered | 查看 Mock 创建位置 | 使用 try-with-resources                          |

---

## 检查清单使用指南

### 开发流程

```
1. 开始新测试 → 阶段 1（开发前检查）
2. 设计测试 → 阶段 2（依赖验证）
3. 编写代码 → 阶段 3-6（循环检查）
4. 编译验证 → 阶段 3（编译检查）
5. 运行测试 → 阶段 7（测试运行验证）
6. 完成 → 所有检查项都勾选 ✅
```

### 出现问题时

```
发现问题
  ↓
查看"常见问题排查矩阵"
  ↓
找到对应的排查步骤
  ↓
按照排查步骤执行
  ↓
修复并重新测试
```

---

## 相关规范引用

- 单元测试编写规范总索引：`specrules/03_coding/index.md`
- 单元测试技术栈规范：`specrules/03_coding/unit_test_technology_stack.md`
- 测试基类标准规范：`specrules/03_coding/test_base_class_standards.md`
- Mock 和断言标准规范：`specrules/03_coding/mock_and_assertion_standards.md`
- 编译通过检查规范：`specrules/03_coding/unit_test_compilation_standards.md`
- 命名和结构规范：`specrules/03_coding/unit_test_naming_and_structure.md`

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
