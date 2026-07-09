---
description: "项目模块依赖关系与构建配置规范：Maven 多模块结构、BOM 统一版本管理、Maven Enforcer 约束、分层依赖方向、API Artifact 版本升级规则、现代构建配置"
alwaysApply: false
globs: ["**/pom.xml", "**/build.gradle*"]
version: "2.0.0"
---

# 项目结构规范

本规范定义 Maven 多模块项目的模块组织、依赖管理和版本控制策略，与 DDD 分层架构严格对齐。

---

## 1. 模块划分（DDD 分层 ↔ Maven 模块）

### 1.1 标准模块结构

```
{project-root}/
├── pom.xml                          # 根 POM：BOM + dependencyManagement + 模块聚合
├── {project}-api/                   # API 模块：对外接口契约
│   └── pom.xml
├── {project}-starter/               # 启动器模块：Spring Boot 启动 + 配置装配
│   └── pom.xml
├── {project}-application/           # 应用层模块：用例编排
│   └── pom.xml
├── {project}-domain/                # 领域层模块：核心业务逻辑
│   └── pom.xml
└── {project}-infrastructure/        # 基础设施层模块：技术适配
    └── pom.xml
```

### 1.2 模块职责与依赖方向

```
┌──────────────┐
│   starter    │  Spring Boot 启动、AutoConfiguration、MQ Listener
│  (启动器)     │  依赖 application + api
└──────┬───────┘
       │
       ↓
┌──────────────┐
│     api      │  对外 REST/RPC 接口、Request/Response DTO
│  (接口契约)   │  不依赖本工程任何模块
└──────────────┘

┌──────────────┐
│ application  │  业务用例编排、事务管理、BO↔DO 转换
│  (应用层)     │  依赖 domain + api
└──────┬───────┘
       │
       ↓
┌──────────────┐
│   domain     │  核心业务逻辑、领域服务、Repository 接口+实现
│  (领域层)     │  依赖 infrastructure
└──────┬───────┘
       │
       ↓
┌──────────────┐
│infrastructure│  DAO、Proxy、Cache、MQ Adapter 等接口+实现
│  (基础设施)    │  不依赖本工程业务模块（不依赖 domain/application/api）
└──────────────┘
```

### 1.3 依赖矩阵

| 模块 | 可依赖 | 禁止依赖 |
|------|--------|----------|
| **starter** | application、api | domain（间接通过 application） |
| **api** | — | 本工程任何模块 |
| **application** | domain、api | infrastructure、starter |
| **domain** | infrastructure | application、api、starter |
| **infrastructure** | —（仅第三方库） | domain、application、api、starter |

> **特例**：domain 允许依赖**本工程以外的外部** API 模块（如其他工程的 `*-api` 或 `*-client`），**禁止**依赖本工程的 api 模块（防止循环依赖）。

---

## 2. 根 POM 与依赖管理

### 2.1 根 POM 结构

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>order-server</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Order Server</name>
    <description>订单服务</description>

    <!-- ========== 模块聚合 ========== -->
    <modules>
        <module>order-server-api</module>
        <module>order-server-domain</module>
        <module>order-server-infrastructure</module>
        <module>order-server-application</module>
        <module>order-server-starter</module>
    </modules>

    <!-- ========== 属性集中管理 ========== -->
    <properties>
        <java.version>21</java.version>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- 框架版本 -->
        <spring-boot.version>3.4.0</spring-boot.version>
        <spring-cloud.version>2024.0.0</spring-cloud.version>
        <springdoc.version>2.8.3</springdoc.version>
        <resilience4j.version>2.3.0</resilience4j.version>
        <jackson.version>3.1.4</jackson.version>
        <mybatis-plus.version>3.5.9</mybatis-plus.version>

        <!-- 本工程模块版本（由根 pom 统一控制） -->
        <order-server-api.version>2.1.0-SNAPSHOT</order-server-api.version>

        <!-- 构建插件版本 -->
        <maven-enforcer-plugin.version>3.5.0</maven-enforcer-plugin.version>
        <flyway-maven-plugin.version>10.21.0</flyway-maven-plugin.version>
        <spotless-maven-plugin.version>2.44.0</spotless-maven-plugin.version>
    </properties>

    <!-- ========== dependencyManagement：统一版本 ========== -->
    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Resilience4j BOM -->
            <dependency>
                <groupId>io.github.resilience4j</groupId>
                <artifactId>resilience4j-bom</artifactId>
                <version>${resilience4j.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 本工程内部模块版本 -->
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>order-server-api</artifactId>
                <version>${order-server-api.version}</version>
            </dependency>
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>order-server-domain</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>order-server-infrastructure</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>order-server-application</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <!-- ========== 构建插件管理 ========== -->
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <release>21</release>
                        <parameters>true</parameters>  <!-- Jackson ParameterNamesModule -->
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-enforcer-plugin</artifactId>
                    <version>${maven-enforcer-plugin.version}</version>
                    <executions>
                        <execution>
                            <id>enforce</id>
                            <goals><goal>enforce</goal></goals>
                            <configuration>
                                <rules>
                                    <dependencyConvergence/>
                                    <requireJavaVersion>
                                        <version>[21,)</version>
                                    </requireJavaVersion>
                                    <requireMavenVersion>
                                        <version>[3.9,)</version>
                                    </requireMavenVersion>
                                    <banCircularDependencies/>
                                </rules>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

### 2.2 子模块 POM 示例

```xml
<!-- order-server-domain/pom.xml -->
<project>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>order-server</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>order-server-domain</artifactId>
    <!-- 版本继承自 parent，不写 <version> -->

    <dependencies>
        <!-- 依赖 infrastructure -->
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>order-server-infrastructure</artifactId>
        </dependency>

        <!-- 第三方库（版本由根 dependencyManagement 统一管理） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-circuitbreaker</artifactId>
        </dependency>
    </dependencies>
</project>
```

> **子模块不写 `<version>`**：版本统一由根 POM 的 `<dependencyManagement>` 控制。内部模块之间引用也不写版本（除 api 模块外）。

---

## 3. API Artifact 版本升级（REQUIRED）

### 3.1 适用范围

当以下变更发生时，必须升级 API artifact 的 Maven 版本：

- 新增、删除、重命名或修改对外 API 字段
- 修改字段类型、必填/选填语义、默认值、枚举值含义
- 新增、删除或修改对外接口方法签名
- 修改序列化契约（如 `@JsonProperty` 名称变更、Thrift `@RpcField` 编号变更）

### 3.2 版本处理流程

1. **定位 API artifact 坐标**：从 api 模块 `pom.xml` 确认 `groupId:artifactId:currentVersion`。
2. **确定目标版本**：遵循 [Semantic Versioning](https://semver.org/)——
   - 新增向后兼容字段 → **MINOR** 版本升级（如 `2.1.0` → `2.2.0`）
   - 删除/重命名字段、修改方法签名 → **MAJOR** 版本升级（如 `2.1.0` → `3.0.0`）
   - 开发阶段使用 `-SNAPSHOT` 后缀
3. **更新版本属性**：在根 POM 的 `<properties>` 中修改对应版本属性。
4. **文档记录**：在 API 文档中记录版本变更说明与下游兼容/升级指引。

### 3.3 版本号管理方式

```xml
<!-- 根 pom.xml properties 中集中管理 -->
<properties>
    <!-- API artifact 独立于工程版本 -->
    <order-server-api.version>2.1.0-SNAPSHOT</order-server-api.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>order-server-api</artifactId>
            <version>${order-server-api.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

> **API 模块版本独立于工程版本**：API 契约变更影响下游消费者，应采用语义版本独立演进。内部模块（domain/infrastructure/application/starter）随工程版本即可。

---

## 4. 依赖约束（Maven Enforcer）

### 4.1 必须启用的规则

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <executions>
        <execution>
            <id>enforce</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <!-- 禁止依赖冲突 -->
                    <dependencyConvergence/>

                    <!-- 禁止循环依赖 -->
                    <banCircularDependencies/>

                    <!-- Java 版本约束 -->
                    <requireJavaVersion>
                        <version>[21,)</version>
                    </requireJavaVersion>

                    <!-- Maven 版本约束 -->
                    <requireMavenVersion>
                        <version>[3.9,)</version>
                    </requireMavenVersion>

                    <!-- 禁止使用已废弃的依赖 -->
                    <bannedDependencies>
                        <excludes>
                            <exclude>commons-logging:commons-logging</exclude>
                            <exclude>log4j:log4j</exclude>
                            <exclude>junit:junit</exclude>  <!-- 使用 JUnit 5 -->
                            <exclude>com.alibaba:fastjson</exclude>
                        </excludes>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 5. 构建配置标准化

### 5.1 编译器配置

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <release>21</release>
        <parameters>true</parameters>   <!-- 保留参数名，供 Jackson 反射 -->
        <compilerArgs>
            <arg>-Xlint:all</arg>
            <arg>-Werror</arg>           <!-- 警告即错误 -->
        </compilerArgs>
    </configuration>
</plugin>
```

### 5.2 代码格式化（Spotless）

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>${spotless-maven-plugin.version}</version>
    <configuration>
        <java>
            <googleJavaFormat>
                <version>1.24.0</version>
            </googleJavaFormat>
            <importOrder>
                <order>java,javax,jakarta,org.springframework,com,*,</order>
            </importOrder>
            <removeUnusedImports/>
        </java>
    </configuration>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

### 5.3 测试配置

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/*Tests.java</include>
        </includes>
        <argLine>-XX:+EnableDynamicAgentLoading</argLine>
    </configuration>
</plugin>
```

### 5.4 Spring Boot 打包

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <!-- 仅在 starter 模块启用，其他模块不打包可执行 jar -->
    <configuration>
        <skip>${skip.spring-boot.repackage}</skip>
    </configuration>
</plugin>
```

> **仅在 starter 模块启用 `spring-boot-maven-plugin`**（或通过 `<skip>` 控制），其他模块不需要可执行 jar。

---

## 6. 版本目录（Version Catalog）——可选替代方案

Gradle 用户使用 `libs.versions.toml`；Maven 用户可配合 `maven-version-catalog` 或手动维护 `properties` 实现类似效果：

```toml
# gradle/libs.versions.toml（Gradle 项目参考）
[versions]
spring-boot = "3.4.0"
spring-cloud = "2024.0.0"
resilience4j = "2.3.0"

[libraries]
resilience4j-circuitbreaker = { module = "io.github.resilience4j:resilience4j-circuitbreaker", version.ref = "resilience4j" }
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version.ref = "spring-boot" }

[bundles]
spring-web = ["spring-boot-starter-web", "spring-boot-starter-validation"]

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
```

---

## 7. 对外 API 契约检查

### 7.1 CI 中自动检查

```yaml
# .github/workflows/api-check.yml
name: API Compatibility Check
on: [pull_request]

jobs:
  api-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin

      - name: Build API module
        run: mvn -pl order-server-api verify

      - name: Check API compatibility
        run: |
          # 使用 japicmp 检查向后兼容性
          mvn -pl order-server-api japicmp:cmp \
            -DoldVersion=$(git show origin/main:order-server-api/pom.xml | grep -oP '<version>\K[^<]+') \
            -DnewVersion=HEAD
```

### 7.2 japicmp Maven 插件

```xml
<plugin>
    <groupId>com.github.siom79.japicmp</groupId>
    <artifactId>japicmp-maven-plugin</artifactId>
    <version>0.22.0</version>
    <configuration>
        <parameter>
            <onlyModified>true</onlyModified>
            <breakBuildOnBinaryIncompatibleModifications>true</breakBuildOnBinaryIncompatibleModifications>
        </parameter>
    </configuration>
</plugin>
```

---

## 8. 检查清单

### 根 POM
- [ ] `dependencyManagement` 引入了 Spring Boot BOM 和 Spring Cloud BOM
- [ ] 所有第三方库版本在 `<properties>` 集中管理
- [ ] 内部模块依赖不写 `<version>`（除 api 模块外）
- [ ] API artifact 版本独立于工程版本
- [ ] Maven Enforcer 已配置（`dependencyConvergence` + `banCircularDependencies`）
- [ ] 编译器 `-parameters` 已启用
- [ ] `spring-boot-maven-plugin` 仅在 starter 模块启用

### 模块依赖
- [ ] starter → application + api
- [ ] application → domain + api
- [ ] domain → infrastructure
- [ ] infrastructure → 无本工程业务模块依赖
- [ ] 无循环依赖（domain 不依赖本工程 api）

### API 版本
- [ ] 对外 API 契约变更时同步升级 API artifact 版本
- [ ] 版本语义正确（MINOR/MAJOR 选择）
- [ ] CI 中启用 API 兼容性检查（japicmp）
- [ ] 变更说明记录在接口文档中

---

## 版本与变更

- **2.0.0** (2026-07-09): 全面重写。采用 Spring Boot 3.4+ / Spring Cloud 2024+ BOM 管理；新增 Maven Enforcer 强约束（禁止冲突/循环依赖/废弃依赖）；补充 Semantic Versioning 版本规则、Spotless 代码格式化、japicmp API 兼容性检查；模块依赖矩阵可视化。
- 1.3.0 (2026-04-14): 新增对外 API artifact 版本升级规则。
- 1.2.0 (2026-03-25): 简化为纯规范文件。
- 1.1.0 (2026-03-13): 对齐新规则入口结构。
- 1.0.0 (2025-02-06): 初始化版本。
