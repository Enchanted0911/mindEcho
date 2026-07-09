---
description: "JSON 处理规范：统一使用 Jackson 3.x，配置 ObjectMapper 最佳实践，覆盖序列化/反序列化安全、Record 支持、@JsonView 视图隔离、流式大文件处理及反序列化漏洞防范"
alwaysApply: false
globs: ["**/*.java"]
version: "2.0.0"
---

# JSON 处理规范（Jackson 3.x）

本规范基于 **Jackson 3.x** 与 **Spring Boot 3.x 自动配置**，覆盖生产级 JSON 处理的全部关键环节。Jackson 是 Spring Boot 的默认 JSON 框架，也是 Java 生态的**事实标准**。

---

## 1. 核心原则（NON-NEGOTIABLE）

1. **统一使用 Jackson**：禁止在项目中同时引入 Jackson 和 JSON-B/Gson/Fastjson，避免序列化行为不一致和类冲突。
2. **共享单例 `ObjectMapper`**：`ObjectMapper` / `JsonMapper` 是线程安全的，禁止每次请求/每次调用创建新实例。
3. **必须注册 `JavaTimeModule`**：任何使用 `java.time.*` 类型的项目必须注册该模块，否则日期序列化结果错误。
4. **禁止直接使用 `enableDefaultTyping()`**（Jackson 2.16+ 已移除）；多态反序列化统一使用 `@JsonTypeInfo` + `@JsonSubTypes`。
5. **禁止反序列化不信任的 JSON 到 `Object.class` 或 `Serializable.class`**——这是反序列化 RCE 漏洞的首要入口。

---

## 2. 依赖配置

### 2.1 Maven

```xml
<properties>
    <jackson.version>3.1.4</jackson.version>  <!-- 或 2.18.8+ -->
</properties>

<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>${jackson.version}</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <version>${jackson.version}</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.module</groupId>
    <artifactId>jackson-module-parameter-names</artifactId>
    <version>${jackson.version}</version>
</dependency>
```

### 2.2 Gradle（Kotlin DSL）

```kotlin
val jacksonVersion = "3.1.4"
implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:$jacksonVersion")
```

### 2.3 编译器参数

`ParameterNamesModule` 需要编译时保留参数名：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

---

## 3. `ObjectMapper` 配置

### 3.1 Builder 模式（推荐，Jackson 3.x）

```java
ObjectMapper mapper = JsonMapper.builder()
    // === 模块 ===
    .addModule(new JavaTimeModule())
    .addModule(new ParameterNamesModule())

    // === 序列化 ===
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

    // === 反序列化 ===
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)

    // === Mapper ===
    .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)

    .build();
```

### 3.2 Spring Boot `application.yml` 配置

```yaml
spring:
  jackson:
    # 回退到 Jackson 2 风格的合理默认值（生产推荐）
    use-jackson2-defaults: true

    # 命名策略
    property-naming-strategy: SNAKE_CASE
    default-property-inclusion: non_null

    # 日期
    date-format: yyyy-MM-dd'T'HH:mm:ss.SSSZ
    time-zone: UTC

    # 序列化
    serialization:
      indent-output: false
      fail-on-empty-beans: false

    # 反序列化
    deserialization:
      fail-on-unknown-properties: false
      accept-empty-string-as-null-object: true
```

### 3.3 编程式自定义（Jackson2ObjectMapperBuilderCustomizer）

```java
@Component
public class JacksonCustomizer implements Jackson2ObjectMapperBuilderCustomizer {
    @Override
    public void customize(Jackson2ObjectMapperBuilder builder) {
        builder
            .modules(new JavaTimeModule(), new ParameterNamesModule())
            .featuresToDisable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                MapperFeature.DEFAULT_VIEW_INCLUSION
            )
            .featuresToEnable(
                DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
            )
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .defaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }
}
```

### 3.4 每次调用的局部配置（不可变、线程安全）

```java
// 不修改共享 mapper，使用 writer/reader 进行局部覆盖
String json = mapper.writerFor(User.class)
    .with(SerializationFeature.INDENT_OUTPUT)
    .writeValueAsString(user);

User user = mapper.readerFor(User.class)
    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .readValue(json);
```

---

## 4. 常用 API

### 4.1 对象 ↔ JSON 字符串

```java
// 序列化：对象 → JSON
String json = mapper.writeValueAsString(object);

// 反序列化：JSON → 对象
User user = mapper.readValue(jsonString, User.class);
```

### 4.2 List 反序列化

```java
// 方式一：TypeReference（泛型安全）
List<User> users = mapper.readValue(jsonArray,
    new TypeReference<List<User>>() {});

// 方式二：JavaType
List<User> users = mapper.readValue(jsonArray,
    mapper.getTypeFactory().constructCollectionType(List.class, User.class));
```

### 4.3 Map 反序列化

```java
// JSON → Map
Map<String, Object> map = mapper.readValue(jsonString,
    new TypeReference<Map<String, Object>>() {});

// 从透传字段中提取信息
Map<String, Object> bizData = mapper.readValue(bizDataJson,
    new TypeReference<Map<String, Object>>() {});
String auditId = (String) bizData.get("auditId");
```

### 4.4 带默认值的反序列化

```java
public <T> List<T> parseArrayWithDefault(String json, Class<T> clazz) {
    if (json == null || json.isBlank()) {
        return List.of();
    }
    try {
        return mapper.readValue(json,
            mapper.getTypeFactory().constructCollectionType(List.class, clazz));
    } catch (Exception e) {
        log.warn("JSON 解析失败，返回空列表: {}", json, e);
        return List.of();
    }
}
```

---

## 5. Java Record 支持（Java 16+）

Jackson 2.12+ 原生支持 Record，无需额外配置：

```java
public record CreateUserRequest(
    @NotBlank String email,
    @Min(18) int age,
    @JsonProperty("full_name") String name
) {
    // 紧凑构造器：反序列化时自动运行
    public CreateUserRequest {
        if (name != null && name.isBlank()) {
            name = null;
        }
    }
}

// 自动正确序列化和反序列化
CreateUserRequest req = mapper.readValue(json, CreateUserRequest.class);
```

---

## 6. 多态反序列化（安全）

### 6.1 推荐方式：`@JsonTypeInfo` + `@JsonSubTypes`（编译期白名单）

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Circle.class, name = "circle"),
    @JsonSubTypes.Type(value = Rectangle.class, name = "rectangle")
})
public sealed interface Shape permits Circle, Rectangle {}

public record Circle(double radius) implements Shape {}
public record Rectangle(double width, double height) implements Shape {}
```

### 6.2 若必须动态类型加载

```java
// 仅当确实无法在编译期确定子类型时使用
PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
    .allowIfBaseType("com.myapp.model.")
    .allowIfSubType("com.myapp.model.")
    .build();

ObjectMapper mapper = JsonMapper.builder()
    .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL)
    .build();
```

---

## 7. `@JsonView` 视图隔离

### 7.1 定义视图层次

```java
public class Views {
    public static class Public {}
    public static class Internal extends Public {}
    public static class Admin extends Internal {}
}
```

### 7.2 在模型和 Controller 使用

```java
public class User {
    @JsonView(Views.Public.class)  private String name;
    @JsonView(Views.Public.class)  private String email;
    @JsonView(Views.Internal.class) private String department;
    @JsonView(Views.Admin.class)    private BigDecimal salary;
}

@RestController
public class UserController {
    @GetMapping("/api/users/{id}")
    @JsonView(Views.Public.class)
    public User getPublic(@PathVariable Long id) { ... }

    @GetMapping("/admin/users/{id}")
    @JsonView(Views.Admin.class)
    public User getAdmin(@PathVariable Long id) { ... }
}
```

> **Jackson 3.x** 默认禁用 `DEFAULT_VIEW_INCLUSION`：未标记 `@JsonView` 的字段在视图激活时**不序列化**。如需 Jackson 2 风格（无注解 = 全视图可见），需手动启用。

---

## 8. 自定义序列化器/反序列化器

### 8.1 标准基类

```java
public class MoneySerializer extends StdSerializer<Money> {
    public MoneySerializer() { super(Money.class); }

    @Override
    public void serialize(Money value, JsonGenerator gen,
                          SerializerProvider provider) throws IOException {
        gen.writeString(value.amount() + " " + value.currency());
    }
}

public class MoneyDeserializer extends StdDeserializer<Money> {
    public MoneyDeserializer() { super(Money.class); }

    @Override
    public Money deserialize(JsonParser p, DeserializationContext ctx)
            throws IOException {
        String[] parts = p.getValueAsString().split(" ");
        return new Money(new BigDecimal(parts[0]), parts[1]);
    }
}
```

### 8.2 注册

```java
// 全局
ObjectMapper mapper = JsonMapper.builder()
    .addModule(new SimpleModule("MoneyModule")
        .addSerializer(Money.class, new MoneySerializer())
        .addDeserializer(Money.class, new MoneyDeserializer()))
    .build();

// 或按字段
public class Transaction {
    @JsonSerialize(using = MoneySerializer.class)
    @JsonDeserialize(using = MoneyDeserializer.class)
    private Money amount;
}
```

---

## 9. 流式处理（大文件 > 100MB）

避免将整个 JSON 文档加载到内存：

```java
JsonFactory factory = JsonFactory.builder()
    .streamReadConstraints(StreamReadConstraints.builder()
        .maxDocumentLength(100_000_000L)
        .maxNestingDepth(500)
        .build())
    .build();

try (JsonParser parser = factory.createParser(new File("large.json"))) {
    while (parser.nextToken() != null) {
        if (parser.currentToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            if ("records".equals(field) && parser.currentToken() == JsonToken.START_ARRAY) {
                while (parser.nextToken() == JsonToken.START_OBJECT) {
                    Record r = parser.readValueAs(Record.class);
                    process(r);
                }
            }
        }
    }
}
```

---

## 10. 安全规范（NON-NEGOTIABLE）

### 10.1 反序列化安全三原则

| 规则 | 说明 |
|------|------|
| **禁止 `Object.class` 目标** | 不信任的 JSON 必须反序列化到具体 DTO，不能到 `Object` 或 `Serializable` |
| **禁止裸 `enableDefaultTyping()`** | Jackson 2.16+ 已移除；替代方案为 `@JsonTypeInfo` + `@JsonSubTypes` |
| **设置 StreamReadConstraints** | 防止大文档/深层嵌套 DoS 攻击 |

### 10.2 版本安全基线

```xml
<jackson.version>2.18.8</jackson.version>  <!-- Jackson 2.x 最低安全版本 -->
<!-- 或 -->
<jackson.version>3.1.4</jackson.version>   <!-- Jackson 3.x 最低安全版本 -->
```

### 10.3 DoS 防护

```java
JsonFactory factory = JsonFactory.builder()
    .streamReadConstraints(StreamReadConstraints.builder()
        .maxStringLength(10_000_000)    // 单字符串最大长度
        .maxNestingDepth(500)           // 最大嵌套深度
        .maxDocumentLength(50_000_000)  // 最大文档大小
        .build())
    .build();
```

---

## 11. 枚举处理

在 JSON 中使用枚举的 int 值而非字符串名（减少耦合，向下兼容）：

```java
public enum OrderStatus implements IntValueEnum {
    PENDING(0), PROCESSING(1), COMPLETED(2), CANCELLED(3);

    private final int value;

    @JsonValue
    public int getValue() { return value; }

    @JsonCreator
    public static OrderStatus fromValue(int value) {
        return IntValueEnum.fromValue(OrderStatus.class, value);
    }
}

// DO 中存储 int，提供枚举访问器
public class OrderDO {
    private Integer status;

    @JsonIgnore
    public OrderStatus getStatusEnum() {
        return OrderStatus.fromValue(status);
    }
}
```

---

## 12. 常见反模式（禁止）

| 反模式 | 问题 | 正确做法 |
|--------|------|----------|
| `toString()` 代替序列化 | 不可控格式，与 JSON 规范不兼容 | `mapper.writeValueAsString(obj)` |
| 每次请求新建 `ObjectMapper` | 极昂贵，每个 ~50-100ms | 使用单例或 Spring 自动注入 |
| 未注册 `JavaTimeModule` | `LocalDate` 序列化为 `{year:2026,month:7,...}` | 注册 `JavaTimeModule` |
| 运行时修改共享 `ObjectMapper` | 非线程安全 | 使用 `writer()`/`reader()` 局部配置 |
| `enableDefaultTyping()` | RCE 漏洞入口 | `@JsonTypeInfo` + `@JsonSubTypes` |
| 绕过 `@JsonIgnore` 手动拼字段 | 维护噩梦 | 使用 `@JsonView` 或 DTO 投影 |
| 循环引用未处理 | `StackOverflowError` | 使用 `@JsonIgnore` 打断循环 |
| 在构造器参数中省略 `@JsonProperty` | 反序列化失败 | 启用 `-parameters` 标志或加注解 |

---

## 13. 测试

### 13.1 `@JsonTest` 切片测试

```java
@JsonTest
class UserSerializationTest {

    @Autowired
    private JacksonTester<User> json;

    @Test
    void shouldSerializeUser() throws IOException {
        User user = new User("Alice", "alice@example.com");

        assertThat(json.write(user))
            .hasJsonPathStringValue("@.name", "Alice")
            .hasJsonPathStringValue("@.email", "alice@example.com");
    }

    @Test
    void shouldDeserializeUser() throws IOException {
        String payload = """
            {"name": "Bob", "email": "bob@example.com"}
            """;

        User user = json.parseObject(payload);
        assertThat(user.name()).isEqualTo("Bob");
    }
}
```

### 13.2 使用 fixture 文件的严格匹配

```java
@Test
void shouldMatchExpectedSchema() throws IOException {
    User user = new User("Alice", "alice@example.com", "Engineering");
    assertThat(json.write(user))
        .isEqualToJson("expected-user.json");  // src/test/resources/expected-user.json
}
```

---

## 14. 检查清单

- [ ] 是否仅使用 Jackson（未混用其他 JSON 库）？
- [ ] 是否注册了 `JavaTimeModule` + `ParameterNamesModule`？
- [ ] `ObjectMapper` 是否为共享单例（未每次请求新建）？
- [ ] 是否禁用了 `FAIL_ON_UNKNOWN_PROPERTIES`（向前兼容）？
- [ ] 是否禁用了 `WRITE_DATES_AS_TIMESTAMPS`（人类可读的 ISO-8601）？
- [ ] 反序列化不信任输入时是否使用具体 DTO 而非 `Object.class`？
- [ ] 多态反序列化是否使用 `@JsonTypeInfo` + `@JsonSubTypes`（而非 `enableDefaultTyping()`）？
- [ ] 是否设置了 `StreamReadConstraints` 限制大文档/深层嵌套？
- [ ] Jackson 版本是否 >= 2.18.8 或 >= 3.1.4？
- [ ] 是否使用 `writer()`/`reader()` 进行局部配置（而非运行时修改共享 mapper）？
- [ ] 是否有 `@JsonTest` 测试覆盖关键序列化/反序列化逻辑？

---

## 版本与变更

- **2.0.0** (2026-07-09): 全面重写。采用 Jackson 3.x Builder 模式配置；新增 Java Record 原生支持、sealed 类多态、@JsonView 视图隔离、流式处理、反序列化安全（CVE-2026-54512/54513 防御）、@JsonTest 测试与反模式清单。
- 1.0.0 (2025-02-06): 初始化版本。
