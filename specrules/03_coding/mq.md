---
description: "消息队列规范：基于 Apache Kafka + Spring Kafka 3.x，覆盖生产者/消费者配置、Schema Registry、幂等性、死信队列、DefaultErrorHandler 错误处理、CloudEvents 信封、Topic 命名与拓扑设计、可观测性及测试策略"
alwaysApply: false
globs: ["**/*.java", "**/*.yml", "**/*.yaml"]
version: "2.0.0"
---

# 消息队列规范（Apache Kafka / Spring Kafka 3.x）

本规范基于 **Apache Kafka 3.7+** 与 **Spring for Apache Kafka 3.x（Spring Boot 3.x）**，覆盖生产者/消费者配置、Schema Registry、幂等性、死信队列、CloudEvents 信封标准、Topic 命名与拓扑设计、可观测性及测试策略。

---

## 1. 核心原则（NON-NEGOTIABLE）

1. **生产者 acks=all**：消息必须等待所有 ISR 副本确认，确保零数据丢失。
2. **消费者禁用自动提交**：`enable-auto-commit: false`，偏移量在业务处理成功后手动提交。
3. **必须使用 DLT**：每条消费者 Topic 必须有对应的 `.DLT` 死信队列，失败的记录不得丢失。
4. **幂等消费**：所有消费者必须实现幂等性（基于 Event ID 去重），容忍重复投递。
5. **Key 决定顺序**：需要顺序保证的消息 Key = 业务实体 ID；无顺序要求则 Key 可以为 null 以最大化并行度。
6. **禁止在生产代码中使用 `auto-offset-reset: latest`** 配合 `enable-auto-commit: true`——此模式下消费者启动后偏移量已被提交，消息可能丢失；统一使用 `earliest` + 手动提交。
7. **Topic 命名遵循 `{domain}.{entity}.{event}` 约定**，点号分隔，小写，过去时态动词。

---

## 2. 生产级配置

### 2.1 基础连接与安全

```yaml
spring:
  kafka:
    bootstrap-servers: broker1:9092,broker2:9092,broker3:9092
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: PLAIN
      sasl.jaas.config: >
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_USERNAME}"
        password="${KAFKA_PASSWORD}";
```

### 2.2 生产者配置

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      properties:
        enable.idempotence: true             # 防止重试导致重复
        delivery.timeout.ms: 120000
        request.timeout.ms: 30000
        linger.ms: 10                        # 微批处理，提高吞吐
        compression.type: lz4                # 减少网络带宽与磁盘消耗
        max.in.flight.requests.per.connection: 5
```

| 配置项 | 推荐值 | 原因 |
|--------|--------|------|
| `acks` | `all` | 所有 ISR 确认后才视为成功，零数据丢失 |
| `enable.idempotence` | `true` | 防止网络重试导致分区内重复 |
| `linger.ms` | `10` | 微批处理窗口，以极低延迟换取显著吞吐提升 |
| `compression.type` | `lz4` | 平衡压缩率与 CPU 消耗；`zstd` 适合追求极致压缩 |
| `delivery.timeout.ms` | `120000` | 2 分钟总投递超时，包含重试 |

### 2.3 消费者配置

```yaml
spring:
  kafka:
    consumer:
      group-id: ${spring.application.name}
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      isolation.level: read_committed
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: com.myapp.events
        spring.json.type.mapping: >
          orderPlaced:com.myapp.events.OrderPlacedEvent
        max.poll.interval.ms: 300000        # 必须超过最坏情况处理时长
        max.poll.records: 500
        heartbeat.interval.ms: 3000
        session.timeout.ms: 45000
        partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor
    listener:
      ack-mode: BATCH
      concurrency: 3
```

| 配置项 | 推荐值 | 原因 |
|--------|--------|------|
| `ErrorHandlingDeserializer` | 包装反序列化器 | 单条损坏消息不影响整个消费者，由错误处理器接管 |
| `spring.json.type.mapping` | 逻辑名 → 类映射 | 不发送全限定类名，安全且解耦 |
| `CooperativeStickyAssignor` | 增量重均衡 | 避免"停全组"重均衡，仅受影响消费者暂停 |
| `isolation.level` | `read_committed` | 消费事务性 Topic 时只读已提交消息 |

---

## 3. 生产者实现

### 3.1 Reliable Send（带回调）

```java
@Component
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishOrderCreated(OrderEvent event) {
        kafkaTemplate.send("orders.order.created", event.orderId(), event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Sent order {} to partition {} offset {}",
                        event.orderId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send order {}", event.orderId(), ex);
                    // 可选：写入 Outbox 表作为补偿
                }
            });
    }
}
```

### 3.2 Transactional Outbox（DB + Kafka 原子性）

**永不单独使用 DB 提交后发 Kafka，存在数据丢失风险。**

```java
@Component
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;

    @Transactional
    public void createOrder(CreateOrderCommand cmd) {
        // 1. 业务操作：保存订单
        Order order = orderRepository.save(cmd.toOrder());

        // 2. 同一事务中插入 Outbox 事件
        outboxRepository.save(new OutboxEvent(
            order.id(), "orders.order.created", serializeEvent(order)));
    }
}

// 独立组件：轮询 Outbox 并投递 Kafka（或使用 Debezium CDC 实现实时读取）
@Component
public class OutboxRelayer {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relayOutboxEvents() {
        List<OutboxEvent> pending = outboxRepository.findAllPending();
        for (OutboxEvent event : pending) {
            kafkaTemplate.send(event.topic(), event.key(), event.payload())
                .whenComplete((result, ex) -> {
                    if (ex == null) { event.markSent(); outboxRepository.save(event); }
                });
        }
    }
}
```

> **生产建议**：以 Debezium（CDC）替代轮询调度器，实现毫秒级延迟的 Outbox 中继。

---

## 4. 消费者实现

### 4.1 标准消费者（幂等 + 异常分类 + DLT）

```java
@Component
public class OrderCreatedConsumer {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = "orders.order.created", groupId = "orders-service")
    public void consume(OrderEvent event) {
        // 1. 幂等性检查
        if (!idempotencyService.tryMarkProcessed(event.eventId())) {
            log.info("Duplicate event {} skipped", event.eventId());
            return;
        }

        // 2. 参数校验
        validate(event);

        // 3. 业务处理
        orderService.process(event.toOrder());

        log.info("Processed order {} successfully", event.orderId());
    }

    private void validate(OrderEvent event) {
        if (event.orderId() == null || event.customerId() == null) {
            throw new ValidationException("Missing required fields");
        }
    }
}
```

### 4.2 幂等性实现

```sql
CREATE TABLE processed_events (
    event_id VARCHAR(64) NOT NULL,
    processed_at BIGINT NOT NULL,
    PRIMARY KEY (event_id)
);
```

```java
@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;

    @Transactional
    public boolean tryMarkProcessed(String eventId) {
        try {
            jdbc.update("INSERT INTO processed_events(event_id, processed_at) VALUES (?, ?)",
                eventId, System.currentTimeMillis());
            return true;
        } catch (DuplicateKeyException e) {
            return false;  // 已处理
        }
    }
}
```

### 4.3 消息体结构规范

```java
public record OrderEvent(
    /** 事件唯一 ID（用于幂等性） */
    String eventId,

    /** 事件类型 */
    String eventType,

    /** 事件发生时间戳 */
    Long timestamp,

    /** 业务实体 ID（作为 Kafka Key） */
    String orderId,

    /** Region 隔离 */
    String region,

    /** 事件版本 */
    String eventVersion,

    /** 业务载荷 */
    OrderPayload data
) {}

public record OrderPayload(
    String customerId,
    BigDecimal amount,
    List<OrderItem> items,
    String status
) {}
```

**消息体必须包含：**
- `eventId`：唯一事件 ID，用于幂等性去重
- `region`：地区字段，满足多地区数据隔离
- `timestamp`：事件时间戳
- `eventVersion`：事件结构版本（用于向前兼容的 Schema 演进）

---

## 5. 错误处理与死信队列

### 5.1 DefaultErrorHandler 配置

```java
@Bean
public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    // DLT = 原 Topic + ".DLT" 后缀
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

    // 重试策略：1s → 2s → 4s → 8s → 16s → 32s → DLT
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(6);
    backOff.setInitialInterval(1_000L);
    backOff.setMultiplier(2.0);
    backOff.setMaxInterval(60_000L);

    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

    // 不可重试异常 → 直接进入 DLT
    handler.addNotRetryableExceptions(
        DeserializationException.class,
        ValidationException.class,
        MethodArgumentNotValidException.class
    );

    return handler;
}
```

### 5.2 异常分类策略

| 异常类型 | 是否重试 | 处理方式 |
|----------|:----:|----------|
| `DeserializationException` | ❌ 否 | 消息格式永久错误 → 直接 DLT |
| `ValidationException` | ❌ 否 | 业务校验失败 → 直接 DLT |
| `DuplicateKeyException` | ❌ 否 | 幂等性冲突 → 跳过 |
| `TimeoutException` | ✅ 是 | 临时网络/服务超时 → 指数退避重试 |
| `DatabaseException` (连接) | ✅ 是 | 数据库临时不可用 → 重试 |
| `HttpServerErrorException` (5xx) | ✅ 是 | 下游服务临时故障 → 重试 |

### 5.3 DLT 消息头（自动注入）

```
kafka_dlt-original-topic          — 原始 Topic
kafka_dlt-original-partition      — 原始分区
kafka_dlt-original-offset         — 原始偏移量
kafka_dlt-original-consumer-group — 消费者组
kafka_dlt-exception-fqcn          — 异常类全限定名
kafka_dlt-exception-message       — 异常消息
kafka_dlt-exception-stacktrace    — 完整堆栈
```

### 5.4 DLT 消费者（检查与告警）

```java
@Component
public class DeadLetterConsumer {

    @KafkaListener(topics = "orders.order.created.DLT", groupId = "orders-dlt-consumer")
    public void processDeadLetters(ConsumerRecord<String, String> record) {
        String originalTopic = new String(record.headers()
            .lastHeader("kafka_dlt-original-topic").value());
        String exceptionMsg = new String(record.headers()
            .lastHeader("kafka_dlt-exception-message").value());

        log.error("=== DLT RECEIVED === originalTopic={}, exception={}",
            originalTopic, exceptionMsg);

        // 告警：PagerDuty / Slack / 钉钉
        alertService.sendDltAlert(originalTopic, exceptionMsg);

        // 持久化用于后续手动重放
        deadLetterRepository.save(DeadLetterRecord.from(record));
    }
}
```

### 5.5 DLT Topic 配置

```yaml
# DLT Topic 保留期 30 天（原 Topic 为 7 天），方便排障
topics:
  - name: orders.order.created.DLT
    partitions: 12
    replicas: 3
    config:
      retention.ms: 2592000000       # 30 天
      cleanup.policy: delete
```

---

## 6. Schema Registry

### 6.1 序列化格式选择

| 格式 | 适用场景 | 优点 | 缺点 |
|------|----------|------|------|
| **Avro** | 跨语言、强类型、数据密集型 | 最紧凑、成熟的生态（Confluent） | 需 Schema 文件、学习曲线 |
| **Protobuf** | gRPC 生态、高性能 | 最快序列化、比 Avro 更小 | 需 `.proto` 定义 |
| **JSON Schema** | JSON 优先团队、快速上手 | 人类可读、无代码生成 | 负载更大、序列化较慢 |

**推荐：** 跨团队协作 → Avro + Confluent Schema Registry；内部高吞吐 → JSON + `spring.json.type.mapping`。

### 6.2 兼容性模式

| 模式 | 规则 | 适用场景 |
|------|------|----------|
| `BACKWARD`（默认） | 新 Schema 可读旧数据 | 新增可选字段 |
| `FORWARD` | 旧 Schema 可读新数据 | 删除字段 |
| `FULL` | 同时兼容前后 | 公共 API、共享契约 |
| `NONE` | 无兼容性检查 | 早期开发、内部 Topic |

**推荐：** 默认 `BACKWARD`，跨团队共享领域事件使用 `FULL`。

---

## 7. CloudEvents 信封标准

### 7.1 为什么使用 CloudEvents

- **互操作性**：所有服务使用相同的事件信封结构
- **上下文传播**：`id`、`source`、`type`、`subject`、`time` 随每个事件携带
- **多语言 SDK**：Java、Go、Python、.NET 均有官方 SDK
- **演进性**：`specversion` 允许规范未来升级

### 7.2 事件结构

```json
{
    "specversion": "1.0",
    "id": "a234-1234-1234",
    "source": "/orders/order-service",
    "type": "com.myapp.orders.order.created",
    "subject": "order-12345",
    "datacontenttype": "application/json",
    "time": "2026-07-09T10:30:00Z",
    "data": {
        "orderId": "order-12345",
        "customerId": "cust-789",
        "amount": 99.95
    }
}
```

### 7.3 Java 生产者

```java
CloudEvent event = CloudEventBuilder.v1()
    .withId(UUID.randomUUID().toString())
    .withSource(URI.create("/orders/order-service"))
    .withType("com.myapp.orders.order.created")
    .withSubject(order.id())
    .withDataContentType("application/json")
    .withData(objectMapper.writeValueAsBytes(order))
    .withExtension("region", order.region())
    .build();

kafkaTemplate.send("orders.order.created", order.id(), event);
```

---

## 8. 批处理

### 8.1 配置与监听器

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> batchFactory(
        ConsumerFactory<String, OrderEvent> consumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderEvent>();
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(true);
    return factory;
}

@KafkaListener(topics = "orders.order.created", containerFactory = "batchFactory")
public void listenBatch(List<OrderEvent> events) {
    log.info("Processing batch of {} events", events.size());
    orderRepository.saveAll(events.stream().map(OrderEvent::toOrder).toList());
}
```

### 8.2 批处理参数

```yaml
spring.kafka.consumer.properties:
  fetch.min.bytes: 65536           # 64KB 后才拉取
  fetch.max.wait.ms: 500           # 不足时最多等待 500ms
  max.poll.records: 500
```

---

## 9. Topic 命名与拓扑设计

### 9.1 命名约定

```
{domain}.{entity}.{event}              → orders.order.created
{tenant}.{domain}.{entity}.{event}.v{version} → acme.orders.order.created.v1
```

**规则：** 小写、点号分隔、过去时态动词、≤50 字符、不含 `_`（Kafka 指标名中 `.` 和 `_` 会合并）。

### 9.2 分区规划

| 吞吐量 | 分区数建议 | 理由 |
|--------|-----------|------|
| < 100 msg/s | 3-6 | 支持 3-6 个并发消费者 |
| 100-1K msg/s | 12-24 | 良好扩展余量 |
| > 1K msg/s | 30-60+ | 基于吞吐 ÷ 25 MB/s/分区计算 |

> 分区数只能增不能减。起始推荐 12。

### 9.3 Key 选择

| Key | 顺序保证 | 并行度 | 适用场景 |
|-----|:----:|:----:|----------|
| `orderId` | ✅ 完美 | 受分区数限制 | 业务事务 |
| `customerId` | ✅ 按客户 | 可能存在热点分区 | 客户领域事件 |
| `null` | ❌ 无 | 轮询、最大并行 | 日志、指标 |

---

## 10. 可观测性

### 10.1 Micrometer 指标

```yaml
spring.kafka.producer.properties.micrometer.enabled: true
spring.kafka.consumer.properties.micrometer.enabled: true
```

### 10.2 关键指标

| 指标 | 类型 | 意义 |
|------|------|------|
| `kafka.consumer.fetch.manager.records.lag` | Gauge | 消费者滞后量 |
| `spring.kafka.listener` | Timer | 记录处理耗时 |
| `kafka.producer.node.request.latency.avg` | Gauge | 生产者延迟 |
| `kafka.consumer.coordinator.rebalance.rate` | Rate | 重均衡频率 |

### 10.3 推荐告警阈值

| 指标 | 告警条件 | 行动 |
|------|----------|------|
| Consumer Lag | > 10,000 持续 > 5 分钟 | 扩容消费者或排查慢查询 |
| 重均衡速率 | > 0.01/s | 检查消费者不稳定原因 |
| 记录处理 P99 | > 1,000 ms | 优化业务逻辑或扩容分区 |
| DLT 消息 | 任意一条 > 0 | 立即排查 |

### 10.4 分布式追踪

Spring Kafka 3.x 原生支持 Micrometer Observation，自动在 Kafka Header 中注入 `traceparent` 和 `tracestate`，实现跨服务端到端追踪：

```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0
```

---

## 11. 测试

### 11.1 EmbeddedKafka（单元测试）

```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "orders.order.created", "orders.order.created.DLT" })
class OrderEventConsumerTest {

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Test
    void shouldProcessOrderEvent() throws Exception {
        OrderEvent event = new OrderEvent("event-1", "order-123", /* ... */);

        kafkaTemplate.send("orders.order.created", event.orderId(), event)
            .get(5, TimeUnit.SECONDS);

        await().atMost(5, TimeUnit.SECONDS)
            .until(() -> consumer.getProcessedCount("order-123") == 1);
    }

    @Test
    void shouldSendToDLTOnValidationFailure() throws Exception {
        OrderEvent invalid = new OrderEvent("event-2", null, /* ... */); // 缺少 orderId

        kafkaTemplate.send("orders.order.created", "key", invalid)
            .get(5, TimeUnit.SECONDS);

        // 断言：事件出现在 DLT 上
        ConsumerRecord<String, OrderEvent> dltRecord =
            KafkaTestUtils.getSingleRecord(dltConsumer, "orders.order.created.DLT");
        assertThat(dltRecord.value().eventId()).isEqualTo("event-2");
    }
}
```

### 11.2 Testcontainers（集成测试）

```java
@SpringBootTest
@Testcontainers
class KafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void fullEndToEnd() throws Exception {
        // 真实 Kafka 环境下的端到端测试
        kafkaTemplate.send("orders.order.created", "order-789", event)
            .get(5, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS)
            .until(() -> consumer.getProcessedIds().contains("order-789"));
    }
}
```

---

## 12. 检查清单

### 配置
- [ ] `acks=all` 已配置
- [ ] `enable.idempotence=true` 已配置
- [ ] `enable-auto-commit=false` 已配置
- [ ] `ErrorHandlingDeserializer` 已包装反序列化器
- [ ] `CooperativeStickyAssignor` 已配置
- [ ] `compression.type` 已设置为 `lz4` 或 `zstd`

### 消费者
- [ ] 消费者实现幂等性（Event ID 去重）
- [ ] 区分业务异常（不重试）与系统异常（重试）
- [ ] 每种异常有明确的处理路径
- [ ] DLT 消费者已配置并接通告警

### 消息设计
- [ ] 消息体包含 `eventId`（幂等性）、`region`（隔离）、`timestamp`、`eventVersion`
- [ ] Topic 命名符合 `{domain}.{entity}.{event}` 约定

### 可靠性
- [ ] DB + Kafka 双写使用 Transactional Outbox 模式
- [ ] 每条 Topic 都有对应的 DLT
- [ ] DLT 保留期 ≥ 30 天

### 可观测性
- [ ] Micrometer 指标已启用
- [ ] Consumer Lag 告警已配置
- [ ] 分布式追踪已启用（OpenTelemetry）

---

## 版本与变更

- **2.0.0** (2026-07-09): 全面重写。基于 Spring Kafka 3.x + Kafka 3.7+ 重写全部配置与实践；新增 Schema Registry、CloudEvents 信封标准、Transactional Outbox 模式、DefaultErrorHandler 错误分类策略、批处理、Topology 设计、可观测性与测试章节。
- 1.0.0 (2025-02-06): 初始化版本。
