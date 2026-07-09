---
description: "单元测试技术栈规范 - 定义单元测试所使用的技术框架、依赖版本及禁用项"
alwaysApply: false
globs: ["**/*.java"]
version: "1.0.0"
---

# 单元测试技术栈规范

## 概述

本规范定义了项目统一使用的单元测试技术栈，确保所有模块的测试代码风格一致、依赖版本统一。

---

## 技术框架选型

### JDK 版本

- **版本要求**：**JDK 17**
- **说明**：确保与项目主体 JDK 版本一致
- **禁用项**：无

### 测试框架

| 框架          | 版本                     | 用途      | 禁用项                 |
|:------------|:-----------------------|:--------|:--------------------|
| **JUnit**   | 4（4.13.2+）             | 单元测试框架  | ❌ 禁止使用 JUnit 5      |
| **Mockito** | 4.5.1+（mockito-inline） | Mock 框架 | ❌ 禁止使用 PowerMockito |

### 断言库

- **使用**：JUnit 4 Assert（`import static org.junit.Assert.*`）
- **禁用**：❌ JUnit 5 Assertions（`org.junit.jupiter.api.Assertions`）

---

## Maven 依赖配置

### 标准依赖声明

```xml
<!-- JUnit 4 -->
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.13.2</version>
    <scope>test</scope>
</dependency>

<!-- Mockito Inline (支持静态Mock) -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-inline</artifactId>
    <version>4.5.1</version>
    <scope>test</scope>
</dependency>
```

### 可选但推荐的依赖

```xml
<!-- Google Guava (用于 Lists.newArrayList() 等工具) -->
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 技术栈对标

| 技术维度       | 选择             | 原因                  |
|:-----------|:---------------|:--------------------|
| 测试框架       | JUnit 4        | 轻量级、广泛使用、与项目生态兼容    |
| Mock 框架    | Mockito        | 功能完善、支持静态 Mock、社区活跃 |
| 静态 Mock 支持 | mockito-inline | 内嵌式 Mock，无需外部库冲突    |
| 性能断言       | JUnit 4 Assert | 原生支持，无额外依赖          |

---

## 禁用项清单

### ❌ 绝对禁用

| 禁用技术                            | 理由             | 替代方案                        |
|:--------------------------------|:---------------|:----------------------------|
| **JUnit 5**                     | 版本不统一，与现有代码不兼容 | 使用 JUnit 4                  |
| **PowerMockito**                | 功能复杂，易引入测试脆弱性  | 使用 Mockito 的 `mockStatic()` |
| **Hamcrest 断言**                 | 不是项目标准         | 使用 `org.junit.Assert`       |
| **TestNG**                      | 项目不使用          | 使用 JUnit 4                  |
| **AssertJ / Fluent-Assertions** | 与标准断言库不统一      | 使用 JUnit 4 Assert           |

---

## 注解和导入规范

### 强制使用的注解

```java
// ✅ 必须使用的注解
import org.junit.Test;           // 标记测试方法
import org.junit.Before;         // setUp
import org.junit.After;          // tearDown
import org.junit.BeforeClass;    // 类级别初始化
import org.junit.AfterClass;     // 类级别清理
import org.mockito.Mock;         // Mock 声明
import org.mockito.InjectMocks;  // 依赖注入
import org.junit.runner.RunWith; // 指定 Runner
```

### 强制使用的断言导入

```java
// ✅ 必须使用
import static org.junit.Assert.*;

// ❌ 禁止使用
import static org.junit.jupiter.api.Assertions.*;  // JUnit 5 风格
import static org.assertj.core.api.Assertions.*;    // AssertJ 风格
```

---

## 版本维护政策

### 版本升级流程

1. **评估阶段**：检查新版本是否与 JDK 17 兼容
2. **试验阶段**：在一个试点模块中测试
3. **通知阶段**：在团队范围内公告新版本
4. **全面升级**：统一升级所有模块的依赖版本
5. **文档更新**：更新本规范文件中的版本号

### 当前版本固定

- JUnit 4: **4.13.2** （LTS 版本）
- Mockito: **4.x** 或 **5.x**（与 JDK 17 兼容）

未经评估和批准，**禁止随意升级依赖版本**。

---

## 兼容性检查清单

- [ ] 所有 import 语句使用 JUnit 4 而非 JUnit 5
- [ ] 依赖配置文件中的版本号与本规范一致
- [ ] 没有使用 PowerMockito 相关依赖
- [ ] Mock 框架使用 mockito-inline 版本
- [ ] 断言方法使用 `org.junit.Assert`
- [ ] 被测试类和测试类都使用 JDK 17 特性

---

## 相关规范引用

- 单元测试编写规范总索引：`specrules/03_coding/index.md`
- 测试基类标准：`specrules/03_coding/test_base_class_standards.md`
- Mock 和断言规范：`specrules/03_coding/mock_and_assertion_standards.md`

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
