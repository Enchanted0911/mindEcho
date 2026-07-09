---
description: "线程池与并发规范：基于 Java 21+ Virtual Threads 与 Structured Concurrency，覆盖 Spring Boot 3.2+ 虚拟线程配置、信号量限流、ScopedValue 上下文传播、有界线程池退避策略及可观测性"
alwaysApply: false
globs: ["**/*.java"]
version: "2.0.0"
---

# 线程池与并发规范（Java 21+ / Project Loom）

本规范面向 **Java 21+** 与 **Spring Boot 3.2+**，以 **Virtual Threads（虚拟线程）** 为核心并发模型，辅以 **Structured Concurrency** 和 **Scoped Values**，替换传统的平台线程池范式。

---

## 1. 核心原则（NON-NEGOTIABLE）

1. **I/O 密集型任务默认使用虚拟线程**：不再为每个 I/O 操作创建固定大小的线程池。
2. **禁止使用 JDK 原生线程池**：`Executors.newFixedThreadPool` / `newCachedThreadPool` / `new ThreadPoolExecutor(...)` 在 I/O 场景下属于反模式；统一使用虚拟线程或 `TaskExecutor` 抽象。
3. **信号量替代线程池限流**：虚拟线程本身无限——需要限制的是**资源许可**，而非线程数。
4. **禁止嵌套阻塞**：禁止在一个线程池任务中向另一个线程池提交任务并 `get()` 阻塞等待，存在死锁风险。
5. **上下文传播必须显式**：MDC/TraceId/租户信息使用 `ScopedValue` 或 `TaskDecorator` 传播，不能依赖 `ThreadLocal` 自动传递。
6. **CPU 密集型任务仍使用有界平台线程池**：虚拟线程不增加 CPU 核心，哈希、编码、图像处理等运算密集任务仍需有界控制。

---

## 2. 虚拟线程（Virtual Threads）

### 2.1 决策框架

| 工作负载 | 使用虚拟线程？ | 原因 |
|----------|:----------:|------|
| HTTP 请求处理（Tomcat/Jetty） | ✅ 是 | 大部分时间阻塞在数据库/下游 I/O |
| 数据库查询 | ✅ 是 | JDBC 调用阻塞时虚拟线程自动卸载载体 |
| MQ 消费者 | ✅ 是 | 消息处理以 I/O 为主 |
| `@Async` 异步方法 | ✅ 是 | 适合调用外部服务/发送通知 |
| 图像处理 / 哈希计算 | ❌ 否 | CPU 密集型，虚拟线程不增加算力 |
| JNI / 原生代码调用 | ❌ 否 | 原生帧在返回前固定载体线程 |

### 2.2 Spring Boot 3.2+ 一键开启

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true   # 虚拟线程是守护线程，需此配置防止 JVM 提前退出
```

这条配置自动将 Tomcat 请求线程、`@Async` 线程池、`TaskExecutor` / `TaskScheduler` 全部切换到虚拟线程模式。

> **警告**：开启后，`spring.task.execution.pool.*` 等线程池大小配置**不再生效**。虚拟线程由 JVM 载体线程池统一调度（默认大小 = CPU 核数，最大 256）。

### 2.3 手动创建虚拟线程

```java
// 方式一：静态工厂
Thread vt = Thread.startVirtualThread(() -> doWork());

// 方式二：Builder
Thread vt = Thread.ofVirtual()
    .name("worker-", 0)
    .unstarted(() -> doWork());
vt.start();

// 方式三：ExecutorService
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> doWork());
    executor.submit(() -> doWork());
} // close() 等待所有任务完成
```

### 2.4 自定义虚拟线程执行器（Spring Bean）

```java
@Bean(name = "virtualThreadExecutor", destroyMethod = "close")
public ExecutorService virtualThreadExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}

@Bean
public SimpleAsyncTaskExecutor appTaskExecutor() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
    executor.setVirtualThreads(true);
    executor.setConcurrencyLimit(1000);     // 可选：并发上限
    executor.setTaskTerminationTimeout(Duration.ofSeconds(30));
    return executor;
}
```

### 2.5 Java 21-23 固定（Pinning）警告

在 Java 21-23 中，虚拟线程在 `synchronized` 块/方法内执行阻塞 I/O 时会被**固定**到载体线程，消除并发优势。

**检测**：启动参数加 `-Djdk.tracePinnedThreads=short`

**修复**：将 `synchronized` 替换为 `ReentrantLock`

```java
// 之前：固定载体
synchronized (lock) {
    socket.read();  // 虚拟线程被固定
}

// 之后：不固定
lock.lock();
try {
    socket.read();  // 虚拟线程正常卸载
} finally {
    lock.unlock();
}
```

> **Java 24+**（JEP 491）已消除 `synchronized` 固定问题。升级 JDK 是最彻底的解决方案。

---

## 3. Structured Concurrency（结构化并发）

`StructuredTaskScope` 将并发子任务的生命周期绑定到词法作用域，作用域退出时**所有**子任务保证已完成，杜绝线程泄漏。

### 3.1 ShutdownOnFailure——全部结果必需（Fan-out）

```java
record OrderSummary(User user, List<Order> orders, Payment payment) {}

OrderSummary handle(String userId) throws Exception {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

        var userTask   = scope.fork(() -> fetchUser(userId));
        var ordersTask = scope.fork(() -> fetchOrders(userId));
        var payTask    = scope.fork(() -> fetchPayment(userId));

        scope.join();             // 等待全部完成
        scope.throwIfFailed();    // 任一失败 → 取消其他 → 传播异常

        return new OrderSummary(
            userTask.get(), ordersTask.get(), payTask.get()
        );
    } // 退出时：全部子任务保证已完成，无泄漏
}
```

### 3.2 ShutdownOnSuccess——任一结果即可（对冲请求）

```java
UserProfile fetchUser(String userId) throws Exception {
    try (var scope = new StructuredTaskScope.ShutdownOnSuccess<UserProfile>()) {
        scope.fork(() -> primaryDb.find(userId));
        scope.fork(() -> replicaDb.find(userId));
        scope.join();
        return scope.result(); // 返回最先成功的那个
    }
}
```

### 3.3 自定义策略——部分失败容忍

```java
class CollectResults<T> extends StructuredTaskScope<T> {
    private final ConcurrentLinkedQueue<T> results = new ConcurrentLinkedQueue<>();

    @Override
    protected void handleComplete(Subtask<? extends T> subtask) {
        if (subtask.state() == Subtask.State.SUCCESS) {
            results.add(subtask.get());
        }
        // 忽略失败——仅收集成功结果
    }

    public List<T> results() { return List.copyOf(results); }
}
```

### 3.4 超时控制

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var user   = scope.fork(() -> fetchUser(userId));
    var orders = scope.fork(() -> fetchOrders(userId));

    scope.joinUntil(Instant.now().plusSeconds(5));
    scope.throwIfFailed();
    // joinUntil 超时 → TimeoutException → 作用域取消所有剩余子任务
}
```

### 3.5 StructuredTaskScope vs CompletableFuture

| 方面 | `CompletableFuture` | `StructuredTaskScope` |
|------|---------------------|-----------------------|
| 取消传播 | 手动 `future.cancel(true)` | 自动（失败时 shutdown 全部） |
| 线程泄漏 | 你的责任 | 内建（`close()` 等待所有） |
| 错误处理 | 复杂链式 `.exceptionally()` | `throwIfFailed()` |
| 超时 | 每个 future 单独 `.orTimeout()` | `joinUntil(deadline)` 全部 |
| 上下文继承 | 手动传播 | `ScopedValue` 自动继承 |
| 适用场景 | 独立 fire-and-forget 管道 | 有协调关系的并发请求 |

> **指南**：需要协调多个并发子任务 → `StructuredTaskScope`。独立的异步管道/重试包装 → `CompletableFuture` 仍适用。

---

## 4. Scoped Values（替代 ThreadLocal）

### 4.1 为什么替代 ThreadLocal

| ThreadLocal 问题 | ScopedValue 解决 |
|------------------|------------------|
| 可变（任意 `set()`） | 不可变，仅绑定时可写 |
| 必须手动 `remove()` | 作用域结束自动解绑 |
| 虚拟线程百万级 → 内存压力 | 无累积开销 |
| `InheritableThreadLocal` 全量复制 | `StructuredTaskScope` 自动继承 |

### 4.2 基本用法

```java
// 声明（通常为 public static final）
public static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();
public static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();

// 绑定
ScopedValue.where(REQUEST_ID, "req-123")
           .where(CURRENT_USER, adminUser)
           .run(() -> processRequest());

// 在调用栈任意深度读取
void auditService() {
    String reqId = REQUEST_ID.get();  // "req-123"
    User user = CURRENT_USER.get();   // adminUser
    // 无 set() 方法 —— 不可变且安全
}
```

### 4.3 与 StructuredTaskScope 协同

```java
ScopedValue.where(REQUEST_ID, "req-456").run(() -> {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        // 子任务自动继承 REQUEST_ID，无需显式传递
        var user   = scope.fork(() -> fetchUser());
        var orders = scope.fork(() -> fetchOrders());
        scope.join().throwIfFailed();
    }
});
```

---

## 5. 信号量限流（替代有界线程池）

虚拟线程本身不稀缺——稀缺的是**资源许可**（数据库连接、下游限速配额）。使用 `Semaphore` 替代有界线程池来保护这些资源。

### 5.1 数据库连接限流

```java
@Service
public class OrderService {
    // 限制并发数据库操作为 50（匹配 HikariCP 连接池大小）
    private final Semaphore dbPermits = new Semaphore(50);

    public Order getOrder(Long id) throws InterruptedException {
        dbPermits.acquire();  // 阻塞虚拟线程——不阻塞 OS 线程
        try {
            return orderRepository.findById(id).orElseThrow();
        } finally {
            dbPermits.release();
        }
    }
}
```

### 5.2 下游 API 限流

```java
@Service
public class ExternalApiClient {
    private final Semaphore apiPermits = new Semaphore(10); // 下游限速 10 QPS 并发

    public ApiResponse call(Request req) throws InterruptedException {
        if (!apiPermits.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            throw new RejectedExecutionException("API 限流，请求被拒绝");
        }
        try {
            return restClient.post(req);
        } finally {
            apiPermits.release();
        }
    }
}
```

### 5.3 通用限流器封装

```java
public class ConcurrencyLimiter implements AutoCloseable {
    private final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore permits;

    public ConcurrencyLimiter(int maxConcurrent) {
        this.permits = new Semaphore(maxConcurrent);
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            permits.acquire();
            try { return task.call(); }
            catch (Exception e) { throw new CompletionException(e); }
            finally { permits.release(); }
        }, exec);
    }

    @Override
    public void close() { exec.close(); }
}
```

---

## 6. 有界平台线程池的退避场景

以下场景仍需使用有界平台线程池：

| 场景 | 原因 | 配置建议 |
|------|------|----------|
| **CPU 密集型计算** | 虚拟线程不增加核心 | `newFixedThreadPool(Runtime.getRuntime().availableProcessors())` |
| **`@Scheduled` 定时任务** | 守护线程可能导致 JVM 退出 | 专用 `ThreadPoolTaskScheduler(2)` |
| **Java 21-23 的 synchronized 密集代码** | pinning 风险 | 有界线程池 = synchronized 块数 |

### 6.1 `@Scheduled` 专用调度器

```java
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(
            Executors.newScheduledThreadPool(2, Thread.ofPlatform()
                .name("scheduler-", 0)
                .factory())
        );
    }
}
```

### 6.2 Spring Boot 平台线程池配置（仅在必要时使用）

```yaml
spring:
  threads:
    virtual:
      enabled: false   # 明确关闭虚拟线程

  task:
    execution:
      thread-name-prefix: "async-"
      pool:
        core-size: 8
        max-size: 16
        queue-capacity: 100
        keep-alive: "60s"
      shutdown:
        await-termination: true
        await-termination-period: "30s"
```

> **队列容量（queue-capacity）比最大线程数更关键**：有界队列提供自然反压；无界队列在生产环境存在无界内存增长风险。

---

## 7. 上下文传播（MDC / TraceId）

### 7.1 TaskDecorator（Spring 标准方式）

```java
@Component
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }
}

// 应用到 TaskExecutor
@Bean
public ThreadPoolTaskExecutor taskExecutor(MdcTaskDecorator decorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(decorator);
    return executor;
}
```

### 7.2 ScopedValue MDC（Logback 前沿方案）

```java
// 在 try-with-resources 内自动传播
ScopedMDC.put("requestId", requestId);

try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    // 子任务自动看到父级的 ScopedMDC 值
    scope.fork(() -> { log.info("subtask"); return 1; });
    scope.join().throwIfFailed();
}
```

---

## 8. 优雅关闭

### 8.1 Spring Boot 配置

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

### 8.2 自定义执行器关闭

```java
@Bean(name = "asyncExecutor", destroyMethod = "close")
public ExecutorService asyncExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

### 8.3 关闭顺序

1. 负载均衡器停止路由新请求
2. 等待进行中请求完成（`timeout-per-shutdown-phase`）
3. `@PreDestroy` 方法，按依赖逆序调用
4. Bean destroy 方法（`close()` / `shutdown()`）
5. JVM 退出

---

## 9. 可观测性

### 9.1 Micrometer 指标

Spring Boot 自动为 `ThreadPoolTaskExecutor` / `ThreadPoolTaskScheduler` 注册指标：

| 指标 | 类型 | 含义 |
|------|------|------|
| `executor.active` | Gauge | 活跃执行线程数 |
| `executor.queued` | Gauge | 队列中等待的任务数 |
| `executor.queue.remaining` | Gauge | 队列剩余容量 |
| `executor.pool.size` | Gauge | 当前池大小 |
| `executor.completed` | Counter | 已完成任务总数 |

### 9.2 虚拟线程监控

```java
// 需要 micrometer-java21 依赖
new VirtualThreadMetrics().bindTo(meterRegistry);
// 提供：虚拟线程固定持续时间、固定事件计数
```

### 9.3 推荐告警阈值

| 指标 | 告警条件 | 行动 |
|------|----------|------|
| `executor.queue.remaining` | = 0 | 池饱和；扩大或检查泄漏 |
| `hikaricp.connections.pending` | > 0 持续 30 秒 | 连接池过小 |
| 虚拟线程固定持续时间 | > 20ms | 替换 `synchronized` 为 `ReentrantLock` |
| 请求 P99 延迟 | > SLO 的 2× | 调查线程争用或下游瓶颈 |
| `Semaphore.availablePermits` | = 0 持续 > 10 秒 | 下游限速或资源耗尽 |

---

## 10. 检查清单

### 迁移到虚拟线程

- [ ] Java 版本 >= 21（推荐 >= 24）
- [ ] `spring.threads.virtual.enabled: true` 已启用
- [ ] `spring.main.keep-alive: true` 已配置
- [ ] 已排查 `synchronized` 密集代码并替换为 `ReentrantLock`
- [ ] `@Scheduled` 使用平台线程调度器（非守护）
- [ ] 自定义线程池已改用虚拟线程或信号量

### 资源保护

- [ ] 数据库连接池（HikariCP `maximum-pool-size`）按数据库容量合理设置
- [ ] 下游 API 调用使用 `Semaphore` 限流
- [ ] CPU 密集型任务使用有界平台线程池
- [ ] 无嵌套阻塞等待

### 上下文与关闭

- [ ] MDC/TraceId 使用 `TaskDecorator` 或 `ScopedValue` 传播
- [ ] 自定义 `ExecutorService` 配置了 `destroyMethod="close"`
- [ ] 优雅关闭超时已配置

### 可观测性

- [ ] 暴露了线程池 Micrometer 指标
- [ ] 配置了队列耗尽/拒绝告警
- [ ] 分布式追踪自动传播（OpenTelemetry）

---

## 版本与变更

- **2.0.0** (2026-07-09): 全面重写。基于 Java 21+ Virtual Threads 作为主并发模型；新增 Structured Concurrency / Scoped Values 规范；信号量限流替代有界线程池；Java 21-23 pinning 警告与修复；优雅关闭与可观测性章节。
- 1.0.0 (2026-06-23): 初始化版本。
