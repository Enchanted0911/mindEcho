---
description: "API 文档标准规范：基于 OpenAPI 3.1 + SpringDoc 2.x，覆盖注解使用、Schema 文档化、安全方案、版本策略、分组管理、AsyncAPI 事件文档、契约测试与构建时生成"
alwaysApply: false
globs: ["**/api/**/*.java", "**/controller/**/*.java", "**/dto/**/*.java"]
version: "2.0.0"
---

# API 文档标准规范（OpenAPI 3.1 / SpringDoc 2.x）

本规范基于 **OpenAPI 3.1** 规范与 **SpringDoc OpenAPI v2.8+**（Spring Boot 3.x），覆盖 REST API 的文档生成、Schema 文档化、安全方案、异步 API 文档、API 版本策略及契约测试。

---

## 1. 核心原则（NON-NEGOTIABLE）

1. **所有对外 REST API 必须有完整的 OpenAPI 文档**，包括请求/响应 Schema、错误码和示例。
2. **禁止 `BaseResultDTO<Void>`**：无业务数据的接口应返回 `BaseResultDTO<Boolean>` 或使用 `void` + `@ApiResponse` 文档化。
3. **方法参数 > 3 个必须使用 Request 对象封装**，且 Request 对象上必须有 `@Schema` 注解。
4. **契约变更必须同步 API 文档**：任何字段新增/删除/重命名必须在 PR 中包含对应的文档更新。
5. **错误响应统一使用 RFC 9457 Problem Details 格式**（`application/problem+json`）。

---

## 2. 依赖配置

### 2.1 Maven（Spring Boot 3.x / SpringDoc 2.x）

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.3</version>
</dependency>
```

### 2.2 application.yml

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
    version: openapi_3_1
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    try-it-out-enabled: true
    syntax-highlight:
      theme: monokai
  packages-to-scan: com.myapp.api
  cache:
    disabled: true                       # 开发时禁用缓存
  show-actuator: false
```

### 2.3 Spring Security 排除路径

```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
        .permitAll()
        .anyRequest().authenticated());
    return http.build();
}
```

---

## 3. OpenAPI Bean 全局配置

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("订单服务 API")
                .description("提供订单的创建、查询、取消与管理功能")
                .version("2.1.0")
                .contact(new Contact()
                    .name("订单服务团队")
                    .email("orders-team@company.com")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("输入 Bearer JWT Token")));
    }
}
```

---

## 4. 注解使用规范

### 4.1 控制器层注解

```java
@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "组织管理 CRUD API")
public class OrganizationController {

    @Operation(
        summary = "创建组织",
        description = "在系统中注册新组织。需要 admin 权限。",
        operationId = "createOrganization",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "组织创建成功",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = OrganizationResponse.class),
                examples = @ExampleObject(
                    name = "success",
                    summary = "成功响应",
                    value = """
                    {
                        "id": "org-123",
                        "name": "Acme Corp",
                        "status": "ACTIVE",
                        "createdAt": "2026-07-09T10:30:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数无效",
            content = @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetailResponse.class)
            )
        ),
        @ApiResponse(responseCode = "401", description = "未认证"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(
            responseCode = "409",
            description = "组织名称已存在",
            content = @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetailResponse.class)
            )
        )
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse createOrganization(
            @RequestBody @Valid CreateOrganizationRequest request) {
        return service.create(request);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "查询组织", description = "根据 ID 查询组织详情")
    @ApiResponse(
        responseCode = "200",
        description = "查询成功",
        content = @Content(schema = @Schema(implementation = OrganizationResponse.class))
    )
    @ApiResponse(responseCode = "404", description = "组织不存在")
    public OrganizationResponse getOrganization(
            @Parameter(
                description = "组织 ID",
                required = true,
                example = "org-123",
                schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable("id") String id) {
        return service.findById(id);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "分页查询组织列表", description = "支持按名称筛选和排序")
    public Page<OrganizationSummary> listOrganizations(
            @Parameter(description = "页码（从 0 开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "页面大小", example = "20")
            @RequestParam(defaultValue = "20") @Max(100) int size,

            @Parameter(description = "按名称模糊匹配")
            @RequestParam(required = false) String name) {
        return service.list(page, size, name);
    }
}
```

### 4.2 DTO Schema 文档化

```java
@Schema(description = "创建组织请求")
public record CreateOrganizationRequest(

    @Schema(
        description = "组织名称（唯一）",
        example = "Acme Corporation",
        minLength = 2,
        maxLength = 200
    )
    @NotBlank @Size(min = 2, max = 200)
    String name,

    @Schema(description = "联系邮箱", example = "contact@acme.com", format = "email")
    @Email
    String contactEmail,

    @Schema(
        description = "组织类型",
        allowableValues = {"ENTERPRISE", "GOVERNMENT", "NON_PROFIT"},
        example = "ENTERPRISE"
    )
    @NotNull
    OrganizationType type,

    @Schema(description = "合法注册地址", implementation = AddressDto.class)
    @Valid
    AddressDto legalAddress
) {}

@Schema(description = "组织响应")
public record OrganizationResponse(

    @Schema(
        description = "唯一标识符",
        example = "org-a1b2c3d4",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    String id,

    @Schema(description = "组织名称", example = "Acme Corporation")
    String name,

    @Schema(
        description = "当前状态",
        allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"},
        example = "ACTIVE"
    )
    OrganizationStatus status,

    @Schema(
        description = "创建时间",
        example = "2026-07-09T10:30:00Z",
        type = "string",
        format = "date-time"
    )
    Instant createdAt
) {}
```

### 4.3 `@Schema` 关键属性速查

| 属性 | 用途 | 示例 |
|------|------|------|
| `description` | 字段说明 | `@Schema(description = "用户显示名称")` |
| `example` | 示例值 | `@Schema(example = "张三")` |
| `implementation` | 类型引用 | `@Schema(implementation = Address.class)` |
| `allowableValues` | 枚举约束 | `@Schema(allowableValues = {"ACTIVE", "INACTIVE"})` |
| `hidden` | 从文档隐藏 | `@Schema(hidden = true)` |
| `accessMode` | 读写权限 | `@Schema(accessMode = READ_ONLY)` |
| `format` | 子格式 | `@Schema(type = "string", format = "date-time")` |
| `deprecated` | 标记弃用 | `@Schema(deprecated = true)` |
| `minLength` / `maxLength` | 字符串长度限制 | `@Schema(minLength = 1, maxLength = 100)` |
| `minimum` / `maximum` | 数值范围 | `@Schema(minimum = "0", maximum = "9999")` |

### 4.4 Jakarta Validation → OpenAPI 自动映射

SpringDoc 自动将 Bean Validation 注解转换为 OpenAPI Schema 约束：

| Validation 注解 | 生成的 Schema 约束 |
|----------------|-------------------|
| `@NotNull` | `required: true` |
| `@Size(min, max)` | `minLength` / `maxLength` |
| `@Min` / `@Max` | `minimum` / `maximum` |
| `@Email` | `format: email` |
| `@Pattern(regex)` | `pattern` |
| `@NotEmpty` | `minLength: 1` |

---

## 5. API 分组管理

### 5.1 按业务域分组

```java
@Bean
public GroupedOpenApi publicApi() {
    return GroupedOpenApi.builder()
        .group("public-api")
        .displayName("公开 API")
        .pathsToMatch("/api/public/**")
        .build();
}

@Bean
public GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
        .group("admin-api")
        .displayName("管理 API")
        .pathsToMatch("/api/admin/**")
        .pathsToExclude("/api/admin/internal/**")
        .build();
}
```

端点：`/v3/api-docs/public-api`、`/v3/api-docs/admin-api`

### 5.2 按版本分组

```java
@Bean
public GroupedOpenApi v1Api() {
    return GroupedOpenApi.builder()
        .group("v1").displayName("API v1（当前）")
        .pathsToMatch("/api/v1/**").build();
}

@Bean
public GroupedOpenApi v2Api() {
    return GroupedOpenApi.builder()
        .group("v2").displayName("API v2（开发中）")
        .pathsToMatch("/api/v2/**").build();
}
```

---

## 6. 安全方案文档化

### 6.1 JWT Bearer Token

```java
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "在 Authorization 头中输入 Bearer Token"
)
```

### 6.2 OAuth2

```java
@SecurityScheme(
    name = "oauth2",
    type = SecuritySchemeType.OAUTH2,
    flows = @OAuthFlows(
        authorizationCode = @OAuthFlow(
            authorizationUrl = "https://auth.example.com/oauth2/authorize",
            tokenUrl = "https://auth.example.com/oauth2/token",
            scopes = {
                @OAuthScope(name = "read", description = "读取权限"),
                @OAuthScope(name = "write", description = "写入权限")
            }
        )
    )
)
```

### 6.3 API Key

```java
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
        .components(new Components()
            .addSecuritySchemes("apiKey", new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-KEY")
                .description("从管理后台获取的 API Key")));
}
```

### 6.4 按端点指定安全要求

```java
@Operation(
    summary = "获取用户资料",
    security = @SecurityRequirement(name = "bearerAuth")
)
@GetMapping("/me")
public UserProfile me() { ... }

@Operation(
    summary = "创建管理员",
    security = @SecurityRequirement(name = "oauth2", scopes = {"admin"})
)
@PostMapping("/admin/users")
public UserProfile createAdmin(@RequestBody CreateUser req) { ... }
```

---

## 7. API 版本策略

### 7.1 URL 路径版本（推荐）

```java
@RequestMapping("/api/v1/organizations")  // 当前版本
public class OrganizationV1Controller { ... }

@RequestMapping("/api/v2/organizations")  // 新版
public class OrganizationV2Controller { ... }
```

### 7.2 弃用标记

```java
@Operation(summary = "旧版支付端点", deprecated = true)
@Deprecated
@PostMapping("/api/v1/payments")
public PaymentResponse payV1(@RequestBody PaymentRequest request) { ... }
```

在生成的 OpenAPI 规范中，该操作将包含 `deprecated: true`。

### 7.3 全局 OperationCustomizer 添加弃用头

```java
@Bean
public OperationCustomizer deprecatedHeaderCustomizer() {
    return (operation, handlerMethod) -> {
        if (handlerMethod.getMethod().isAnnotationPresent(Deprecated.class)) {
            operation.addExtension("x-deprecated-at", "2026-06-01");
            operation.addExtension("x-sunset-date", "2026-12-31");
        }
        return operation;
    };
}
```

---

## 8. 错误响应标准化（RFC 9457）

### 8.1 ProblemDetail Schema

```java
@Schema(description = "RFC 9457 Problem Details 错误响应")
public record ProblemDetailResponse(

    @Schema(description = "错误类型 URI", example = "https://api.example.com/errors/invalid-input")
    String type,

    @Schema(description = "错误摘要", example = "请求参数无效")
    String title,

    @Schema(description = "HTTP 状态码", example = "400")
    int status,

    @Schema(description = "详细说明", example = "字段 'name' 不能为空")
    String detail,

    @Schema(description = "错误发生路径", example = "/api/v1/organizations")
    String instance,

    @Schema(description = "字段级错误详情")
    List<FieldViolation> errors
) {}

@Schema(description = "字段校验错误")
public record FieldViolation(
    @Schema(description = "字段名", example = "name")
    String field,

    @Schema(description = "错误描述", example = "不能为空")
    String message
) {}
```

### 8.2 全局异常处理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(400);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://api.example.com/errors/validation-error"));
        problem.setDetail(ex.getMessage());
        problem.setProperty("errors", ex.getViolations());
        return problem;
    }
}
```

### 8.3 在所有错误响应上使用标准 Media Type

```java
@ApiResponse(
    responseCode = "400",
    description = "请求参数无效",
    content = @Content(
        mediaType = "application/problem+json",
        schema = @Schema(implementation = ProblemDetailResponse.class)
    )
)
```

---

## 9. AsyncAPI——事件驱动接口文档

### 9.1 为什么需要 AsyncAPI

OpenAPI 描述**请求-响应 API**；AsyncAPI 描述**事件驱动/消息传递 API**（Kafka Topic、MQ Queue）。

### 9.2 AsyncAPI 2.x 规范示例

```yaml
asyncapi: 2.6.0
info:
  title: 订单事件服务
  version: 1.0.0

servers:
  production:
    url: kafka.internal:9092
    protocol: kafka

defaultContentType: application/json

channels:
  order/created:
    description: 订单创建事件
    publish:
      operationId: emitOrderCreated
      summary: 订单已创建
      message:
        name: OrderCreatedEvent
        payload:
          type: object
          properties:
            orderId:
              type: string
              format: uuid
            customerId:
              type: string
            totalAmount:
              type: number
            items:
              type: array
              items:
                $ref: '#/components/schemas/OrderItem'

  order/status-changed:
    description: 订单状态变更事件
    publish:
      operationId: emitStatusChange
      message:
        name: OrderStatusChanged
        payload:
          type: object
          properties:
            orderId: { type: string }
            fromStatus: { type: string }
            toStatus: { type: string }
            timestamp: { type: string, format: date-time }
```

### 9.3 文档产出物

| API 类型 | 文档格式 | 工具 |
|----------|----------|------|
| REST API | OpenAPI 3.1 | SpringDoc |
| Kafka/MQ 事件 | AsyncAPI 2.x | springwolf / 手动编写 |
| WebSocket | AsyncAPI 2.x | 手动编写 |

---

## 10. 构建时生成 OpenAPI 规范

### 10.1 springdoc-openapi-maven-plugin

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <jvmArguments>-Dspring.application.admin.enabled=true</jvmArguments>
    </configuration>
    <executions>
        <execution>
            <id>pre-integration-test</id>
            <goals><goal>start</goal></goals>
        </execution>
        <execution>
            <id>post-integration-test</id>
            <goals><goal>stop</goal></goals>
        </execution>
    </executions>
</plugin>

<plugin>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-maven-plugin</artifactId>
    <version>1.4</version>
    <executions>
        <execution>
            <id>generate-openapi</id>
            <phase>integration-test</phase>
            <goals><goal>generate</goal></goals>
            <configuration>
                <apiDocsUrl>http://localhost:8080/v3/api-docs</apiDocsUrl>
                <outputFileName>openapi.json</outputFileName>
                <outputDir>${project.build.directory}</outputDir>
                <failOnError>true</failOnError>
            </configuration>
        </execution>
    </executions>
</plugin>
```

执行 `mvn verify` 后，`target/openapi.json` 即为完整规范文件，可发布到 API 门户（SwaggerHub、Backstage、Azure API Management）或用于契约测试。

---

## 11. 契约测试

### 11.1 OpenAPI 规范结构校验

```xml
<plugin>
    <groupId>com.github.adrianohowl</groupId>
    <artifactId>openapi-maven-plugin</artifactId>
    <version>1.9</version>
    <executions>
        <execution>
            <goals><goal>validate</goal></goals>
            <configuration>
                <inputSpec>${project.build.directory}/openapi.json</inputSpec>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 11.2 代码断言——操作完整性检查

```java
@Test
void allEndpointsHaveSummariesAndResponses() {
    OpenAPI api = new OpenAPIV3Parser()
        .read("target/openapi.json");

    api.getPaths().forEach((path, pathItem) ->
        pathItem.readOperationsMap().forEach((method, op) -> {
            assertThat(op.getSummary())
                .as("%s %s must have summary", method, path)
                .isNotEmpty();
            assertThat(op.getResponses())
                .as("%s %s must define responses", method, path)
                .isNotEmpty();
        }));
}
```

### 11.3 消费者驱动契约（Spring Cloud Contract）

```groovy
// contracts/shouldReturnOrganization.groovy
Contract.make {
    request {
        method GET()
        url "/api/v1/organizations/123"
        headers { accept(applicationJson()) }
    }
    response {
        status 200
        headers { contentType(applicationJson()) }
        body([ id: "org-123", name: "Acme Corp", status: "ACTIVE" ])
    }
}
```

---

## 12. 请求参数规范

### 12.1 参数 > 3 个必须使用 Request 对象

```java
// ❌ 禁止：参数平铺
public Page<User> search(String name, String email, Integer age,
                          String city, String sortBy, SortDirection sortDir) { ... }

// ✅ 必须：封装为 Request 对象
@ParameterObject
public record UserSearchRequest(
    @Parameter(description = "用户名") String name,
    @Parameter(description = "邮箱") String email,
    @Parameter(description = "年龄") Integer age,
    @Parameter(description = "城市") String city,
    @Parameter(description = "排序字段") String sortBy,
    @Parameter(description = "排序方向") SortDirection sortDir
) {}

public Page<User> search(@ParameterObject @Valid UserSearchRequest request) { ... }
```

### 12.2 分页参数标准化

```java
@ParameterObject
public record PageRequest(
    @Parameter(description = "页码（从 0 开始）", example = "0")
    @Min(0)
    @RequestParam(defaultValue = "0")
    int page,

    @Parameter(description = "页面大小", example = "20")
    @Min(1) @Max(100)
    @RequestParam(defaultValue = "20")
    int size
) {}
```

---

## 13. 检查清单

### 注解完整性
- [ ] 所有 Controller 类有 `@Tag` 注解
- [ ] 所有 REST 方法有 `@Operation` 注解
- [ ] `@Operation` 包含 `summary` 和 `description`
- [ ] 所有响应码都有 `@ApiResponse` 定义（至少 200、400、401、500）
- [ ] 所有路径变量有 `@Parameter` + `description`
- [ ] 所有 `@RequestParam` 有 `@Parameter` + `description`

### Schema 文档化
- [ ] 所有 Request/Response DTO 有 `@Schema(description = "...")`
- [ ] DTO 关键字段有 `@Schema(example = "...")`
- [ ] 成功响应的 `@ApiResponse` 包含 `@ExampleObject`
- [ ] 错误响应使用 `application/problem+json` 媒体类型

### 安全
- [ ] 鉴权方案在 `@SecurityScheme` 中声明
- [ ] 受保护端点使用 `@SecurityRequirement` 标记
- [ ] 公共端点不添加安全要求

### 版本与演进
- [ ] API 版本通过 URL 路径或 Header 体现
- [ ] 已弃用端点标记 `deprecated = true`
- [ ] 弃用端点附带 `x-sunset-date` 扩展

### 构建与测试
- [ ] `springdoc-openapi-maven-plugin` 在 CI 中执行 `generate`
- [ ] 生成的 `openapi.json` 通过结构校验
- [ ] 关键端点的响应 Schema 与代码实现一致

---

## 版本与变更

- **2.0.0** (2026-07-09): 全面重写。基于 OpenAPI 3.1 + SpringDoc 2.x 重写全部规范；新增 @Schema 属性速查、Jakarta Validation 自动映射、API 分组管理、安全方案文档化、RFC 9457 Problem Details、AsyncAPI 事件文档、构建时生成、契约测试与请求参数对象化章节。
- 1.1.0 (2026-04-14): 新增对外 API artifact 版本记录要求。
- 1.0.0 (2025-02-06): 初始化版本。
