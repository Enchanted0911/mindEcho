---
description: "基础设施层标准规范：数据访问（MyBatis-Plus）、外部服务调用（OpenFeign / RestClient + Resilience4j）、数据库迁移（Flyway/Liquibase）、缓存（Spring Cache + Redis）、包结构、命名约定与可观测性"
alwaysApply: false
globs: ["**/infrastructure/**/*.java"]
version: "2.0.0"
---

# 基础设施层标准规范

基础设施层负责**数据持久化**、**外部服务调用**和**技术基础设施**的适配，是领域层与外部技术系统的桥梁。

---

## 1. 核心原则（NON-NEGOTIABLE）

1. **为领域层提供接口**：Infra 定义接口（DAO、Proxy、Cache）供 Domain 层依赖；Domain 不关注 Infra 内部的具体实现类。
2. **不依赖本工程业务模块**：Infra 模块只依赖外部/第三方库，不能依赖 domain、application、api 等本工程业务模块。
3. **接口与实现分离**：所有外部依赖（DB、RPC、HTTP、MQ、Cache）必须通过接口访问，方便测试与替换。
4. **所有外部调用必须有熔断降级**：使用 Resilience4j CircuitBreaker + TimeLimiter 或 @Retry，不得裸调。
5. **数据库变更必须版本化**：所有 DDL 变更通过 Flyway 或 Liquibase 管理，禁止手动执行 SQL。

---

## 2. 包结构规范

```
infrastructure/
├── persistence/                  # 数据持久化
│   ├── entity/                   # MyBatis-Plus Entity
│   │   ├── OrderEntity.java
│   │   └── ProductEntity.java
│   ├── dao/                      # DAO 接口
│   │   ├── OrderDAO.java
│   │   └── ProductDAO.java
│   ├── dao/impl/                 # DAO 实现
│   │   ├── OrderDAOImpl.java
│   │   └── ProductDAOImpl.java
│   └── mapper/                   # MyBatis Mapper（若使用 MyBatis）
│       ├── OrderMapper.java
│       └── ProductMapper.java
├── proxy/                        # 外部服务代理
│   ├── OrderServiceProxy.java    # Proxy 接口
│   ├── PaymentServiceProxy.java
│   └── impl/                     # Proxy 实现
│       ├── OrderServiceProxyImpl.java
│       └── PaymentServiceProxyImpl.java
├── cache/                        # 缓存适配
│   ├── CacheProxy.java
│   └── impl/
│       └── RedisCacheProxyImpl.java
├── mq/                           # 消息队列适配（若未放在 infra 之外）
│   ├── producer/
│   └── consumer/
├── config/                       # Infra 层配置（数据源、连接池、Redis、Feign 等）
│   ├── DataSourceConfig.java
│   ├── RedisConfig.java
│   └── Resilience4jConfig.java
└── db/
    └── migration/                # Flyway/Liquibase 迁移脚本
        ├── V1__init_schema.sql
        └── V2__add_order_table.sql
```

---

## 3. 数据访问层（DAO）

### 3.1 技术选型

| 技术 | 适用场景 | 推荐度 |
|------|----------|:---:|
| **MyBatis-Plus** | 常规 CRUD、复杂 SQL、动态条件查询 | ✅ 首选 |
| **裸 MyBatis Mapper XML** | 遗留兼容 | ❌ 避免（升级到 MyBatis-Plus） |
| **JdbcTemplate** | 极简单表操作、批量 SQL | ⚠️ 仅配合 MyBatis-Plus 使用 |
| **Spring Data JPA** | 不推荐 | ❌ 本项目统一使用 MyBatis-Plus |
| **jOOQ** | 不推荐 | ❌ 与 MyBatis-Plus 定位重叠 |

### 3.2 DAO 接口与实现分离（REQUIRED）

所有 DAO 必须定义接口和实现。接口在 Domain 层可引用，实现在 Infra 层内部。

```java
// ========== 接口（Infra 层） ==========
public interface OrderDAO {

    /**
     * 插入订单（主键自增回填）
     */
    Long insert(OrderEntity entity);

    /**
     * 根据 ID 查询（有效数据）
     */
    Optional<OrderEntity> selectById(Long id);

    /**
     * 根据条件分页查询
     */
    List<OrderEntity> selectByCondition(QueryCondition condition);

    /**
     * 根据 ID 逻辑删除
     */
    int softDeleteById(Long id);

    /**
     * 查询条件封装（DAO 层唯一允许的内部类）
     */
    @Data
    @Builder
    class QueryCondition {
        private Long id;
        private List<Long> ids;
        private String orderNo;
        private Integer status;
        private String region;
        private Long startTime;
        private Long endTime;
        @Builder.Default
        private Integer isValid = 1;  // 默认只查有效数据
        private String orderBy;
        @Builder.Default
        private String orderDirection = "DESC";
    }
}
```

### 3.3 DAO 实现（MyBatis-Plus）

```java
@Repository
@Slf4j
public class OrderDAOImpl implements OrderDAO {

    private final OrderMapper orderMapper;

    public OrderDAOImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public Long insert(OrderEntity entity) {
        long now = System.currentTimeMillis();
        entity.setCtime(now);
        entity.setUtime(now);
        entity.setIsValid(1);
        orderMapper.insert(entity);
        return entity.getId();  // MyBatis-Plus 自动回填主键
    }

    @Override
    public Optional<OrderEntity> selectById(Long id) {
        return Optional.ofNullable(
            orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getIsValid, 1)));
    }

    @Override
    public List<OrderEntity> selectByCondition(QueryCondition condition) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper
            .eq(OrderEntity::getIsValid, condition.getIsValid())
            .eq(StringUtils.hasText(condition.getRegion()),
                OrderEntity::getRegion, condition.getRegion())
            .eq(condition.getStatus() != null,
                OrderEntity::getStatus, condition.getStatus())
            .in(CollUtil.isNotEmpty(condition.getIds()),
                OrderEntity::getId, condition.getIds())
            .ge(condition.getStartTime() != null,
                OrderEntity::getCtime, condition.getStartTime())
            .le(condition.getEndTime() != null,
                OrderEntity::getCtime, condition.getEndTime());

        // 排序
        if (condition.getOrderBy() != null) {
            boolean isAsc = "ASC".equalsIgnoreCase(condition.getOrderDirection());
            wrapper.orderBy(true, isAsc,
                EntityUtil.fieldToProperty(OrderEntity.class, condition.getOrderBy()));
        } else {
            wrapper.orderByDesc(OrderEntity::getCtime);
        }

        return orderMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public int softDeleteById(Long id) {
        OrderEntity entity = new OrderEntity();
        entity.setId(id);
        entity.setIsValid(0);
        entity.setUtime(System.currentTimeMillis());
        return orderMapper.updateById(entity);
    }
}
```

### 3.4 MyBatis Mapper 接口

```java
/** MyBatis-Plus 通过 BaseMapper 自动提供 CRUD，无需写 XML */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    /**
     * 仅在此定义 BaseMapper 无法覆盖的复杂查询
     * 优先使用 LambdaQueryWrapper 动态条件，避免写固定 SQL
     */
    @Select("""
        SELECT * FROM order_task
        WHERE region = #{region}
          AND is_valid = 1
        ORDER BY ctime DESC
        LIMIT #{limit}
        """)
    List<OrderEntity> selectRecentByRegion(String region, int limit);
}
```

### 3.5 分页查询

MyBatis-Plus 内置分页插件，无需手写 LIMIT：

```java
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

```java
// DAO 层分页查询
public IPage<OrderEntity> selectPage(QueryCondition condition, int page, int size) {
    LambdaQueryWrapper<OrderEntity> wrapper = buildWrapper(condition);
    Page<OrderEntity> pageParam = new Page<>(page, size);
    return orderMapper.selectPage(pageParam, wrapper);
}
```

### 3.6 命名规范

| 类型 | 后缀 | 示例 |
|------|------|------|
| DAO 接口 | `DAO` | `OrderDAO`、`ProductDAO` |
| DAO 实现 | `DAOImpl` | `OrderDAOImpl`、`ProductDAOImpl` |
| MyBatis Mapper | `Mapper` | `OrderMapper`、`ProductMapper` |
| Entity 类 | `Entity` | `OrderEntity`、`ProductEntity` |
| QueryCondition | `DAO` 内部类 `.QueryCondition` | `OrderDAO.QueryCondition` |

---

## 4. Database Entity 规范

### 4.1 通用字段（所有表必须包含）

```java
/** 基础 Entity，所有 Entity 继承此类，复用通用字段 */
@Data
public abstract class BaseEntity {

    /** 主键（BIGINT AUTO_INCREMENT，MyBatis-Plus 自动回填） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 地区（必须，varchar(32)） */
    private String region;

    /** 创建人 */
    private String createBy;

    /** 更新人 */
    private String updateBy;

    /** 创建时间（毫秒时间戳） */
    private Long ctime;

    /** 更新时间（毫秒时间戳） */
    private Long utime;

    /** 有效性：0-删除 1-有效（默认 1） */
    private Integer isValid;
}
```

> **通用字段默认值在 DAO 层 `insert` 方法中统一设置**（ctime、utime、isValid），Entity 本身为纯数据载体，不包含行为逻辑。

### 4.2 业务实体示例

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_task")
public class OrderTaskEntity extends BaseEntity {

    /** 任务编码 */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 状态 */
    private Integer status;

    /** 业务 ID */
    private String bizId;
}
```

### 4.3 数据库命名约定

| 层级 | 约定 | 示例 |
|------|------|------|
| 表名 | `snake_case` | `order_task`、`product_sku` |
| 列名 | `snake_case` | `task_code`、`create_by` |
| 主键 | `id`，BIGINT AUTO_INCREMENT | `id` |
| 时间字段 | `BIGINT` 存储毫秒时间戳 | `ctime`、`utime` |
| 唯一约束 | `uk_` 前缀，必须含 `region` | `uk_code_region` |
| 索引 | `idx_` 前缀 | `idx_status_region` |

---

## 5. Repository 与 DAO 的关系

### 5.1 分层职责

```
┌─────────────────────────────────────────────┐
│  Domain Layer                                │
│  TaskRepository (接口)  →  TaskRepositoryImpl│
│       │                      (聚合 DAO 调用)  │
│       ↓                                      │
│  TaskDAO (接口，Infra 提供)                    │
└─────────────────────────────────────────────┘
          │  依赖（接口依赖，非实现依赖）
          ↓
┌─────────────────────────────────────────────┐
│  Infrastructure Layer                        │
│  TaskDAO (接口)  →  TaskDAOImpl (实现)        │
│       │                    │                 │
│       │               OrderMapper            │
│       ↓                    ↓                 │
│  Database (MySQL)                            │
└─────────────────────────────────────────────┘
```

**关键规则**：
- Repository 接口+实现在 **Domain 层**，负责聚合多个 DAO/Proxy/Cache 调用
- DAO 接口在 **Infra 层**，Domain 只依赖其接口
- Infra 不依赖 Domain，保持依赖单向

### 5.2 Repository 实现示例（Domain 层）

```java
// domain/repository/TaskRepositoryImpl.java
@Repository
public class TaskRepositoryImpl implements TaskRepository {

    private final TaskDAO taskDAO;
    private final TaskCacheProxy cacheProxy;

    @Override
    @Transactional
    public TaskDO save(TaskDO task) {
        TaskEntity entity = TaskConverter.toEntity(task);
        Long id = taskDAO.insert(entity);
        task = task.withId(id);
        cacheProxy.evictByRegion(task.getRegion());  // 主动清理缓存
        return task;
    }

    @Override
    public Optional<TaskDO> findById(Long id) {
        return taskDAO.selectById(id)
            .map(TaskConverter::toDomain);
    }
}
```

---

## 6. 外部服务调用（Proxy 模式）

### 6.1 技术选型

| 技术 | 适用场景 | 推荐度 |
|------|----------|:---:|
| **Spring Cloud OpenFeign** | RESTful 微服务间调用、声明式接口 | ✅ 首选 |
| **RestClient / WebClient** | 轻量 HTTP 调用、响应式场景 | ✅ 备选 |
| **gRPC** | 高性能、强类型、流式 RPC | ⚠️ 特定场景 |
| **RSocket** | 双向流、低延迟 | ⚠️ 特定场景 |

### 6.2 OpenFeign Proxy 示例

```java
// ========== 接口（Infra 层） ==========
public interface PaymentServiceProxy {

    /**
     * 发起支付
     */
    PaymentResult pay(PaymentRequest request);

    /**
     * 查询支付状态
     */
    PaymentStatus queryStatus(String paymentId);
}

// ========== 实现 ==========
@Component
@Slf4j
public class PaymentServiceProxyImpl implements PaymentServiceProxy {

    private final PaymentFeignClient feignClient;

    public PaymentServiceProxyImpl(PaymentFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public PaymentResult pay(PaymentRequest request) {
        log.info("发起支付: orderId={}, amount={}", request.getOrderId(), request.getAmount());

        ApiResponse<PaymentResult> response = feignClient.pay(request);
        if (!response.isSuccess()) {
            log.error("支付失败: code={}, msg={}", response.getCode(), response.getMessage());
            throw new ExternalServiceException("支付失败: " + response.getMessage());
        }

        log.info("支付成功: paymentId={}", response.getData().getPaymentId());
        return response.getData();
    }

    @Override
    public PaymentStatus queryStatus(String paymentId) {
        ApiResponse<PaymentStatus> response = feignClient.queryStatus(paymentId);
        if (!response.isSuccess()) {
            throw new ExternalServiceException("查询支付状态失败");
        }
        return response.getData();
    }
}

// ========== Feign Client 定义 ==========
@FeignClient(name = "payment-service", url = "${payment.service.url}")
interface PaymentFeignClient {
    @PostMapping("/api/v1/payments")
    ApiResponse<PaymentResult> pay(@RequestBody PaymentRequest request);

    @GetMapping("/api/v1/payments/{paymentId}/status")
    ApiResponse<PaymentStatus> queryStatus(@PathVariable String paymentId);
}
```

### 6.3 Proxy 命名规范

| 类型 | 后缀 | 示例 |
|------|------|------|
| Proxy 接口 | `Proxy` | `PaymentServiceProxy`、`ShopServiceProxy` |
| Proxy 实现 | `ProxyImpl` | `PaymentServiceProxyImpl` |
| Feign Client | `FeignClient`（包级私有） | `PaymentFeignClient` |
| 禁止 | `Gateway`、`Client`、`Service` | — |

### 6.4 外部调用结果处理规范

```java
@Override
public List<Long> getMerchantIdsByRegion(String region, int page, int size) {
    try {
        ApiResponse<ShopPage> response = shopFeignClient.listShops(region, page, size);

        if (response == null || !response.isSuccess()) {
            log.warn("商家查询返回空: region={}, code={}", region,
                response != null ? response.getCode() : "null");
            return Collections.emptyList();
        }

        return response.getData().getItems().stream()
            .map(ShopInfo::getMerchantId)
            .toList();

    } catch (FeignException.ServiceUnavailable e) {
        log.error("商家服务不可用: region={}", region, e);
        // 熔断器会处理重试/降级；此处仅记录
        throw e;
    } catch (FeignException e) {
        log.error("商家查询异常: region={}", region, e);
        throw new ExternalServiceException("商家查询失败", e);
    }
}
```

---

## 7. 熔断与降级（Resilience4j）

### 7.1 配置（application.yml）

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 50
        failure-rate-threshold: 50        # 失败率 > 50% 触发熔断
        wait-duration-in-open-state: 30s  # 熔断后 30s 试探恢复
        permitted-number-of-calls-in-half-open-state: 5
        slow-call-duration-threshold: 5s
        slow-call-rate-threshold: 50
    instances:
      paymentService:
        base-config: default
        sliding-window-size: 20
        wait-duration-in-open-state: 15s
  timelimiter:
    configs:
      default:
        timeout-duration: 10s             # 默认 10s 超时
    instances:
      paymentService:
        timeout-duration: 5s
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - java.net.SocketTimeoutException
          - feign.FeignException.ServiceUnavailable
```

### 7.2 服务调用 + 熔断降级

```java
@Service
public class PaymentAppService {

    private final PaymentServiceProxy paymentProxy;
    private final CircuitBreaker paymentCircuitBreaker;

    public PaymentAppService(PaymentServiceProxy paymentProxy,
                             CircuitBreakerRegistry registry) {
        this.paymentProxy = paymentProxy;
        this.paymentCircuitBreaker = registry.circuitBreaker("paymentService");
    }

    public PaymentResult payWithFallback(PaymentRequest request) {
        Supplier<PaymentResult> supplier = () -> paymentProxy.pay(request);

        Supplier<PaymentResult> decorated = Decorators.ofSupplier(supplier)
            .withCircuitBreaker(paymentCircuitBreaker)
            .withFallback(Arrays.asList(
                CallNotPermittedException.class,
                TimeoutException.class
            ), this::payFallback)
            .decorate();

        return decorated.get();
    }

    private PaymentResult payFallback(Throwable t) {
        log.error("支付服务降级: {}", t.getMessage());
        return PaymentResult.fallback();
    }
}
```

### 7.3 注解方式（AOP）

```java
@Component
public class PaymentServiceProxyImpl implements PaymentServiceProxy {

    private final PaymentFeignClient feignClient;

    @CircuitBreaker(name = "paymentService", fallbackMethod = "payFallback")
    @TimeLimiter(name = "paymentService")
    @Override
    public PaymentResult pay(PaymentRequest request) {
        return feignClient.pay(request).getData();
    }

    // fallback 方法：签名必须与主方法一致 + Throwable
    public PaymentResult payFallback(PaymentRequest request, Throwable t) {
        log.error("支付服务降级: orderId={}, error={}", request.getOrderId(), t.getMessage());
        return PaymentResult.fallback();
    }
}
```

### 7.4 熔断约束

1. **必须通过代理对象调用**——类内部 `this.method()` 自调用熔断不生效。
2. **主方法禁止 catch 业务异常**——Resilience4j 通过异常判定调用结果，catch 后统计失效。
3. **降级方法必须 public**，参数与主方法一致，末尾可追加 `Throwable`。
4. **平台配置优先**：阈值参数（超时、失败率、统计窗口）优先使用配置中心动态值，代码中的值仅为默认。

---

## 8. 缓存规范

### 8.1 统一使用 Spring Cache + Redis

```java
// 接口
public interface CacheProxy {
    <T> Optional<T> get(String key, Class<T> type);
    void set(String key, Object value, Duration ttl);
    void evict(String key);
    void evictByPattern(String pattern);
}

// 实现
@Component
@Slf4j
public class RedisCacheProxyImpl implements CacheProxy {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("缓存读取失败: key={}", key, e);
            return Optional.empty();  // 降级：缓存失败不阻塞业务
        }
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("缓存写入失败: key={}", key, e);
        }
    }
}
```

### 8.2 缓存原则

- **禁止本地缓存**（JVM 堆内/静态变量），统一使用分布式缓存（Redis）
- **必须设置 TTL**，避免缓存雪崩
- **数据更新时主动清理缓存**，保持一致性
- **缓存失败降级到 DB**，不抛异常

---

## 9. 数据库迁移（Flyway）

### 9.1 强制版本化管理

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration/{vendor}
    baseline-on-migrate: true
    validate-on-migrate: true
```

### 9.2 迁移脚本命名

```
db/migration/
├── V1__init_schema.sql
├── V2__add_order_table.sql
├── V3__add_product_index.sql
└── V4__alter_order_add_region.sql
```

### 9.3 迁移脚本规范

```sql
-- V2__add_order_table.sql
CREATE TABLE order_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    region VARCHAR(32) NOT NULL,
    task_code VARCHAR(64) NOT NULL,
    task_name VARCHAR(200) DEFAULT NULL,
    status INT NOT NULL DEFAULT 0,
    biz_id VARCHAR(64) DEFAULT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    ctime BIGINT NOT NULL,
    utime BIGINT NOT NULL,
    is_valid INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_region (task_code, region),
    KEY idx_status_region (status, region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 10. 可观测性

### 10.1 关键日志

| 场景 | 日志级别 | 内容 |
|------|:------:|------|
| DAO 插入/更新 | DEBUG | 操作类型 + entity ID + 耗时 |
| DAO 异常 | ERROR | 操作 + 参数 + 异常信息 |
| 外部调用开始 | INFO | 服务名 + 方法 + 关键参数 |
| 外部调用成功 | INFO | 服务名 + 结果摘要 + 耗时 |
| 外部调用异常 | ERROR | 服务名 + 异常类型 + 消息 |
| 熔断触发 | WARN | 断路器名 + 状态转移 |
| 缓存命中/未命中 | DEBUG | key + 命中状态 |

### 10.2 Micrometer 指标

```java
@Configuration
public class InfraMetricsConfig {

    private final MeterRegistry meterRegistry;

    // DAO 操作计时
    @Bean
    public MeterBinder daoMetrics() {
        return r -> {
            Timer.builder("dao.operation.time")
                .description("DAO operations duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(r);
        };
    }
}
```

---

## 11. 检查清单

### 数据访问
- [ ] DAO 有接口与实现分离
- [ ] Entity 继承 BaseEntity 包含所有通用字段
- [ ] `region` 字段已包含在查询条件和唯一约束中
- [ ] 主键使用 `BIGINT AUTO_INCREMENT`
- [ ] 逻辑删除使用 `is_valid` 字段
- [ ] 数据库变更通过 Flyway/Liquibase 管理

### 外部调用
- [ ] Proxy 以接口+实现分离，命名以 `Proxy` / `ProxyImpl` 结尾
- [ ] 所有外部调用有熔断降级（Resilience4j）
- [ ] 主方法不 catch 业务异常（交由熔断器统计）
- [ ] 降级方法 public、同参、可追加 Throwable
- [ ] 异常日志记录关键参数和错误详情

### 缓存
- [ ] 使用 Redis 分布式缓存，禁止本地缓存
- [ ] 所有缓存 key 设置了合理 TTL
- [ ] 数据更新时主动清理缓存

---

## 版本与变更

- **2.0.0** (2026-07-09): 全面重写。数据访问层统一使用 MyBatis-Plus（LambdaQueryWrapper 动态条件、BaseMapper 自动 CRUD、分页插件）；外部服务调用采用 OpenFeign + Resilience4j；新增 Flyway 数据库迁移规范、Redis 缓存适配、可观测性章节；Entity 使用 @TableName/@TableId 纯数据载体。
- 1.1.0 (2026-06-23): 熔断降级改为 Resilience。
- 1.0.0 (2025-02-06): 初始化版本。
