---
name: object-conversion-checker
description: Checks DTO/BO/DO/Entity object conversion and data-class style per project standards. Validates BO→DO/DO→Entity placement (BO/DO methods vs Converter), Converter static/no-injection, and recommends Lombok @Data to replace hand-written getters/setters. Use when reviewing or writing Converter code, BO/DO/Entity classes, or when the user asks to check object conversion or remove get/set methods.
version: "1.1.0"
---

# 对象转换与数据类规范检查

按项目 [对象转换规范](specrules/03_coding/object_conversion_standards.md) 与 [数据对象命名](specrules/00_general/naming/data_object_naming.md) 检查：转换职责归属、Converter 用法、以及数据类是否使用 Lombok 去除手写 getter/setter。

## 1. 何时使用本 Skill

- 审查或编写 **Converter**、**BO**、**DO**、**Entity**、DTO/VO、**Kafka 消息体** 时
- 用户要求「检查对象转化」「BO 转 DO」「DO 转 Entity」「Kafka 消息转换」「用 @Data 去掉 get/set」时
- 提交前做转换与数据类风格一致性检查时

## 2. 转换职责检查清单

### 2.1 分层与职责

| 转换方向 | 简单转换（单对象、字段一一对应） | 复杂转换（多参数/多对象拼装） | Converter 位置 |
|----------|----------------------------------|-------------------------------|----------------|
| DTO/VO ↔ BO | 一律不用内部方法，**一律用 Converter** | 一律用 Converter | `starter/.../converter/` |
| BO → DO | **封装在 BO**：`toXxxDO()`、`static fromXxxDO(XxxDO do)` | 单独 **Converter** | `application/.../converter/` |
| DO → Entity / Cache / 外部 DTO | **封装在 DO**：`toXxxEntity()`、`static fromXxxEntity(XxxEntity e)` 等 | 单独 **Converter** | `domain/.../converter/` |

- [ ] **Starter 层**：DTO↔BO 仅通过 Converter，不在 DTO/BO 内写转换方法
- [ ] **应用层**：BO→DO 简单转换在 BO 内（`toXxxDO`/`fromXxxDO`），复杂用 application Converter
- [ ] **领域层**：DO→Entity/Cache/外部 DTO 简单转换在 DO 内（`toXxxEntity` 等），复杂用 domain Converter

### 2.2 Converter 约束 (NON-NEGOTIABLE)

- [ ] **禁止** `@Component`、`@Service`、`@Bean` 将 Converter 注册为 Bean
- [ ] **禁止** `@Resource`、`@Autowired` 注入 Converter；调用方**仅通过静态方法**调用
- [ ] Converter 类：`public final` + 仅静态方法 + `private` 无参构造
- [ ] 命名：Starter 可用 `xxDTO2xxBO`、`xxBO2xxVO`；应用/领域层用 `toXxxDO`、`fromXxxDO`、`toXxxEntity`、`toXxxCacheDTO` 等

### 2.3 禁止的写法

- [ ] **禁止** 在业务代码或 stream 中直接调用 **服务实现类**的静态/实例方法做转换（如 `XxxServiceImpl::toTaskDO`）
- [ ] 正确：在 **BO/DO** 上提供 `toXxxDO()`、`fromXxxDO()` 或 `XxxDO.fromXxx(xxx)`，或在 **Converter** 中提供静态方法后由业务代码调用

## 3. 数据类风格：用 @Data 去除 get/set

### 3.1 适用对象

纯数据载体、无业务行为时，推荐使用 Lombok 减少样板代码：

- **DTO / VO / Request / Response**（API、Starter 层）
- **BO**（仅作数据传递、无复杂校验逻辑时）
- **DO**（仅作领域数据载体、转换逻辑在 `toXxxEntity()` 等内时）
- **Entity**（与表结构一一对应）
- **消息队列/外部消息体**（如事件 DTO）

### 3.2 检查项

- [ ] 若类为纯数据载体（仅字段 + 可选构造器/Builder），且无「必须手写 getter/setter」的框架约束，则应使用 **`@Data`**（或按需 `@Getter`/`@Setter`），**删除手写 getXxx/setXxx**
- [ ] 若需不可变或 Builder，可配合 `@Builder`、`@AllArgsConstructor`、`@NoArgsConstructor`（与项目现有 DTO、Request 风格一致）
- [ ] 保留 **转换方法**（如 `toXxxDO()`、`fromXxxDO()`、`toXxxEntity()`）在 BO/DO 内，不因使用 @Data 而删除

### 3.3 示例（符合规范）

```java
// 数据类：@Data 替代手写 getter/setter，保留转换方法在 DO 内
@Data
public class TaskDO {
    private Long id;
    private String taskCode;
    private String region;

    public TaskEntity toTaskEntity() {
        TaskEntity entity = new TaskEntity();
        entity.setId(this.id);
        entity.setTaskCode(this.taskCode);
        entity.setRegion(this.region);
        return entity;
    }

    public static TaskDO fromTaskEntity(TaskEntity entity) {
        if (entity == null) return null;
        TaskDO do_ = new TaskDO();
        do_.setId(entity.getId());
        do_.setTaskCode(entity.getTaskCode());
        do_.setRegion(entity.getRegion());
        return do_;
    }
}
```

## 4. 检查流程（推荐顺序）

1. **定位转换**：找出所有 BO→DO、DO→Entity、DTO↔BO、**Kafka 消息 ↔ DO** 的调用与定义
2. **对照 §2**：确认简单/复杂归属、Converter 所在层、是否静态/无注入、是否调用了服务实现类做转换
3. **对照 §3**：对纯数据类建议加 @Data 并删除手写 getter/setter，保留 BO/DO 上的 toXxx/fromXxx 方法
4. **对照 §5**：检查 Kafka 消息体的序列化/反序列化转换是否符合规范
5. **输出**：列出违规项（含文件与行号/片段）与修改建议；对可改为 @Data 的类列出清单

## 5. Kafka 消息体转换规范

### 5.1 转换职责

| 转换方向 | 位置 | 说明 |
|----------|------|------|
| DO → Kafka 消息体 | `domain/.../converter/` 或 DO 的 `toMessage()` | 领域对象转换为 Kafka 消息 |
| Kafka 消息体 → DO | 消费者入口处，在消费者类内部直接完成 | 消息反序列化后立即转换 |

### 5.2 消息体独立定义

Kafka 消息体应与 DO/Entity 独立定义，不共用同一类：

```java
// ✅ 正确：消息体独立定义
public record OrderPlacedMessage(
    String eventId,
    String orderId,
    String customerId,
    BigDecimal amount,
    String region,
    Long timestamp,          // 消息产生时间戳
    String eventVersion      // Schema 版本号
) {}

// 转换在 DO 或 Converter 中
public class OrderDO {
    public OrderPlacedMessage toPlacedMessage() {
        return new OrderPlacedMessage(
            UUID.randomUUID().toString(),
            this.orderId,
            this.customerId,
            this.amount,
            this.region,
            System.currentTimeMillis(),
            "1.0"
        );
    }
}
```

### 5.3 反序列化 → 领域对象

```java
@Component
public class OrderEventConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = "orders.order.created", groupId = "order-service")
    public void consume(OrderPlacedMessage message) {
        OrderDO order = OrderDO.fromPlacedMessage(message);
        orderService.process(order);
    }
}
```

### 5.4 Avro / Protobuf 转换（高级场景）

使用 Schema Registry 或多语言互操作时：

```java
// 生产者：DO → Avro SpecificRecord
public class OrderDO {
    public OrderPlacedAvro toAvro() {
        return OrderPlacedAvro.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOrderId(this.orderId)
            .setAmount(this.amount.doubleValue())
            .setRegion(this.region)
            .setTimestamp(System.currentTimeMillis())
            .build();
    }
}

// 消费者：Avro → DO
@Component
public class OrderAvroConsumer {

    @KafkaListener(topics = "orders.order.created", groupId = "order-service")
    public void consume(OrderPlacedAvro record) {
        OrderDO order = OrderDO.fromAvro(record);
        orderService.process(order);
    }
}
```

### 5.5 消息体检查清单

- [ ] Kafka 消息体是否与 DO/Entity 独立定义（不共用类）？
- [ ] 消息体是否包含 `eventId`（幂等去重）、`region`（数据隔离）、`timestamp`、`eventVersion`？
- [ ] 生产者端转换是否在 DO 方法或 domain Converter 中（不在消费者/应用层）？
- [ ] 反序列化后的转换是否在消费者入口方法内直接完成？
- [ ] 若使用 Avro/Protobuf，Schema 文件是否纳入版本控制？

## 6. 规范来源

- 转换职责与 Converter 约定：[对象转换规范](specrules/03_coding/object_conversion_standards.md)
- 数据对象命名与层级：[数据对象命名](specrules/00_general/naming/data_object_naming.md)
- 更多细节与示例见 [reference.md](reference.md)。

---

## 版本与变更

- 1.1.0 (2025-07-09): 新增 §5 Kafka 消息体转换规范（消息体独立定义、DO↔消息转换、Avro/Protobuf 高级场景、消息体检查清单）。
- 1.0.0 (2025-02-06): 初始版本；对象转换职责与 Converter 检查清单、@Data 数据类风格建议。
