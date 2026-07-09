---
description: "应用配置管理规范：Spring Boot @ConfigurationProperties 外部化配置、Kubernetes ConfigMaps/Secrets、配置分层、Region 隔离读取、配置热刷新与安全最佳实践"
alwaysApply: false
globs: ["**/config/**/*.java", "**/*.yml", "**/*.yaml"]
version: "1.0.0"
---

# 应用配置管理规范

本规范基于 **Spring Boot 3.x** 与 **Kubernetes-native 配置模型**，覆盖配置层次结构、外部化配置、Region 隔离、敏感信息管理与配置热刷新。

---

## 1. 核心原则

1. **配置外部化**：禁止在代码或资源文件中硬编码环境相关的值（URL、密钥、开关等），全部通过 `application.yml` + 环境变量 / ConfigMap 注入。
2. **类型安全配置**：使用 `@ConfigurationProperties` 绑定配置，禁止使用 `@Value` 逐字段注入。
3. **Region 隔离**：多 Region 配置必须通过 `spring.profiles` 或独立 ConfigMap 隔离，禁止交叉泄漏。
4. **敏感信息分离**：密钥、密码、Token 必须使用 Kubernetes Secrets 或 Vault，不得明文出现在 ConfigMap 中。
5. **配置即代码**：配置文件纳入版本控制，非敏感默认值与结构 Schema 在仓库内维护。

---

## 2. 配置层次结构

### 2.1 优先级（从高到低）

```
1. 命令行参数 (--server.port=9090)
2. 操作系统环境变量 (SERVER_PORT=9090)
3. Kubernetes ConfigMap / Secrets
4. application-{profile}.yml（profile 特定）
5. application.yml（默认）
6. @PropertySource（自定义属性文件）
```

### 2.2 推荐目录结构

```
src/main/resources/
├── application.yml                    # 默认公共配置
├── application-dev.yml                # 开发环境
├── application-staging.yml            # 预发环境
├── application-prod.yml               # 生产环境
└── config/
    └── application-region-eu.yml      # Region 特定（通过 spring.profiles.include 激活）
```

### 2.3 多 Region 配置

```yaml
# application.yml — 公共默认
spring:
  profiles:
    group:
      eu: "prod,region-eu"
      hk: "prod,region-hk"

# application-region-eu.yml — Region 特定
app:
  gateway:
    endpoint: "https://api.eu.example.com"
```

---

## 3. @ConfigurationProperties（类型安全配置）

### 3.1 定义配置类

```java
@ConfigurationProperties(prefix = "app.payment")
@Validated
public record PaymentProperties(

    @NotBlank
    String gatewayUrl,

    @Min(1000) @Max(30000)
    int timeoutMillis,

    @Positive
    int maxRetries,

    @NotNull
    RetryStrategy retryStrategy,

    boolean enabled
) {

    public enum RetryStrategy {
        FIXED, EXPONENTIAL
    }
}
```

### 3.2 启用配置类

```java
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class AppConfig {
}
```

### 3.3 对应 YAML

```yaml
app:
  payment:
    gateway-url: "https://pay.example.com/api"
    timeout-millis: 5000
    max-retries: 3
    retry-strategy: EXPONENTIAL
    enabled: true
```

### 3.4 禁止 @Value 注入

```java
// ❌ 禁止：逐字段 @Value
@Service
public class PaymentService {
    @Value("${app.payment.gateway-url}")
    private String gatewayUrl; // 无类型安全、无校验、跨文件散落

    @Value("${app.payment.timeout}")
    private int timeout;
}

// ✅ 正确：注入类型安全的 properties 对象
@Service
public class PaymentService {

    private final PaymentProperties paymentProps;

    public PaymentService(PaymentProperties paymentProps) {
        this.paymentProps = paymentProps;
    }

    public void process() {
        String url = paymentProps.gatewayUrl();
        int timeout = paymentProps.timeoutMillis();
    }
}
```

---

## 4. Kubernetes ConfigMap 集成

### 4.1 ConfigMap 定义

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  application-prod.yml: |
    app:
      payment:
        gateway-url: "https://pay.internal.example.com"
        timeout-millis: 5000
        max-retries: 3
```

### 4.2 Deployment 挂载

```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: app
          volumeMounts:
            - name: config
              mountPath: /app/config
      volumes:
        - name: config
          configMap:
            name: app-config
```

### 4.3 Spring Boot 加载外部配置

```yaml
# 通过 spring.config.import 加载 ConfigMap 挂载的配置
spring:
  config:
    import:
      - "optional:configtree:/app/config/"
```

---

## 5. 敏感信息管理（Secrets / Vault）

### 5.1 Kubernetes Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: app-secrets
type: Opaque
stringData:
  DB_PASSWORD: "s3cr3t!"
  API_KEY: "sk-abc123"

# Deployment 注入为环境变量
spec:
  containers:
    - envFrom:
        - secretRef:
            name: app-secrets
```

### 5.2 HashiCorp Vault（高级场景）

```yaml
spring:
  cloud:
    vault:
      host: vault.internal.example.com
      port: 8200
      scheme: https
      authentication: KUBERNETES
      kubernetes:
        role: my-app
```

### 5.3 禁止明文存储

```java
// ❌ 禁止
public class Secrets {
    public static final String DB_PASSWORD = "admin123";
}

// ❌ 禁止：ConfigMap 中明文写密码
data:
  application.yml: |
    database.password: "my-secret-password"
```

---

## 6. 配置常量（Config Key 集中管理）

### 6.1 统一常量类

```java
public final class ConfigConstants {

    private ConfigConstants() {}

    /** 支付网关 URL — Region 级配置 */
    public static final String PAYMENT_GATEWAY_URL = "app.payment.gateway-url";

    /** 最大重试次数 */
    public static final String PAYMENT_MAX_RETRIES = "app.payment.max-retries";

    /** 熔断器开关 */
    public static final String CIRCUIT_BREAKER_ENABLED = "app.resilience.circuit-breaker.enabled";
}
```

> **禁止**在业务代码中直接出现字符串字面量形式的配置 key（如 `"app.payment.gateway-url"` 散落多处）。

---

## 7. Region 隔离配置读取

### 7.1 Profile 切换

```yaml
# 启动参数指定 Region
-Dspring.profiles.active=prod,region-eu
```

### 7.2 代码中不硬编码 Region

```java
// ❌ 禁止
if ("EU".equals(System.getenv("REGION"))) {
    endpoint = "https://eu.example.com";
}

// ✅ 正确：通过 @ConfigurationProperties 注入
@ConfigurationProperties("app.gateway")
public record GatewayProperties(
    String endpoint,
    String region
) {}
```

### 7.3 配置回退与默认值

```java
@ConfigurationProperties("app.payment")
public record PaymentProperties(

    @Positive
    @DefaultValue("5000")
    int timeoutMillis,  // 安全默认值

    @NotBlank
    String gatewayUrl   // 无默认值 = 启动失败报错（fail-fast）
) {}
```

---

## 8. 配置热刷新

### 8.1 Spring Cloud Refresh

```java
@Configuration
@RefreshScope
@ConfigurationProperties("app.feature-flags")
public record FeatureFlagProperties(
    boolean darkMode,
    boolean newCheckoutFlow
) {}
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "refresh"
```

### 8.2 基于文件的 Watch（Kubernetes 原生）

ConfigMap 更新后 kubelet 自动同步到 Pod 挂载路径（默认 ~60s），配合：

```yaml
spring:
  cloud:
    kubernetes:
      reload:
        enabled: true
        mode: event           # event | polling | restart-context
        strategy: refresh     # refresh | restart-context | shutdown
```

---

## 9. 集群配置一致性

### 9.1 多机房差异化

| 配置项 | 全局默认 | EU 机房 | HK 机房 |
|--------|---------|---------|---------|
| `gateway.timeout` | 5000 | 8000 | 5000 |
| `payment.gateway-url` | — | `eu-api.example.com` | `hk-api.example.com` |
| `feature.dark-mode` | false | false | true |

### 9.2 一致性检查

```yaml
# CI 中校验多 Region 配置一致性
app:
  config-check:
    required-properties:
      - app.payment.gateway-url
      - app.payment.max-retries
    restrict-regions:
      - eu
      - hk
```

---

## 10. 检查清单

- [ ] 是否使用 `@ConfigurationProperties`（禁用 `@Value`）？
- [ ] 敏感信息是否存储在 Secrets / Vault（禁止 ConfigMap 明文）？
- [ ] 多 Region 配置是否通过 profile 隔离？
- [ ] 配置常量是否集中在 `ConfigConstants` 类？
- [ ] 是否提供了合理的默认值（fail-fast vs 安全回退）？
- [ ] 配置文件是否纳入版本控制？
- [ ] Kubernetes ConfigMap 是否正确挂载并在 `spring.config.import` 中声明？

---

## 版本与变更

- 1.0.0 (2025-07-09): 初始版本；基于 Spring Boot 3.x + Kubernetes ConfigMap/Secrets + @ConfigurationProperties 类型安全配置。

