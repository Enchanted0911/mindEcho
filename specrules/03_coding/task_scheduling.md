---
description: "定时任务调度规范：Spring @Scheduled + Virtual Threads、Quartz 分布式调度、Kubernetes CronJob、任务幂等性、可观测性与优雅关闭"
alwaysApply: false
globs: ["**/scheduler/**/*.java", "**/job/**/*.java", "**/*Task*.java", "**/*Job*.java"]
version: "1.0.0"
---

# 定时任务调度规范

本规范基于 **Spring Boot 3.x** 与 **Java 21+ Virtual Threads**，覆盖 `@Scheduled` 定时任务、Quartz 分布式调度、Kubernetes CronJob 的基础设施级任务，以及任务幂等性、并发控制与可观测性。

---

## 1. 核心原则

1. **默认使用 Virtual Threads**：定时任务与 I/O 密集型任务使用虚拟线程，CPU 密集型保留有界平台线程池。
2. **任务幂等**：所有定时任务必须支持安全重复执行（通过业务唯一键去重）。
3. **任务隔离**：独立定时任务使用专用线程池，避免相互阻塞。
4. **优雅关闭**：任务线程池必须配置 `awaitTermination` + 合理的关闭超时。
5. **必选可观测性**：执行耗时、成功/失败计数、最后执行时间必须暴露为 Micrometer 指标。
6. **禁止无界队列**：任务队列必须有容量限制，提供天然反压。

---

## 2. 选型决策

| 场景 | 推荐方案 | 理由 |
|------|----------|------|
| 简单定时间隔 (< 数分钟) | `@Scheduled` + Virtual Threads | 零额外依赖，虚拟线程天然适合 I/O 等待 |
| 复杂 Cron 表达式 / 任务编排 | Quartz Scheduler | 支持持久化、集群、失火处理 |
| 运维级定时任务 / 跨服务编排 | Kubernetes CronJob | 与 Pod 生命周期解耦，支持资源隔离 |
| 长时间工作流 / Saga 补偿 | Temporal | 持久化执行历史、自动重试、可观测性 |

---

## 3. @Scheduled 定时任务

### 3.1 基础配置

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean(name = "taskScheduler", destroyMethod = "close")
    public ExecutorService taskScheduler() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

### 3.2 任务类约定

```java
@Component
@Slf4j
public class DailyReportTask {

    private final ReportService reportService;
    private final IdempotencyService idempotencyService;

    @Scheduled(cron = "0 0 6 * * ?") // 每天 06:00
    public void generateDailyReport() {
        String jobId = JobIdBuilder.forDay("daily-report");
        if (!idempotencyService.tryAcquire(jobId)) {
            log.info("Job {} already executed today, skipping", jobId);
            return;
        }
        try {
            log.info("Starting daily report generation: {}", jobId);
            reportService.generate();
            log.info("Daily report completed: {}", jobId);
        } catch (Exception e) {
            log.error("Daily report failed: {}", jobId, e);
            idempotencyService.release(jobId); // 失败释放锁，允许重试
        }
    }
}
```

### 3.3 任务幂等性

```java
@Service
public class IdempotencyService {

    private final JdbcTemplate jdbc;

    @Transactional
    public boolean tryAcquire(String jobId) {
        try {
            jdbc.update("""
                INSERT INTO scheduled_job_lock (job_id, locked_at)
                VALUES (?, ?)
                """, jobId, System.currentTimeMillis());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public void release(String jobId) {
        jdbc.update("DELETE FROM scheduled_job_lock WHERE job_id = ?", jobId);
    }
}
```

```sql
CREATE TABLE scheduled_job_lock (
    job_id VARCHAR(128) NOT NULL PRIMARY KEY,
    locked_at BIGINT NOT NULL,
    INDEX idx_locked_at (locked_at)
);
```

---

## 4. @Scheduled 配置参数

### 4.1 Cron 表达式最佳实践

| 模式 | 表达式 | 说明 |
|------|--------|------|
| 每小时 | `0 0 * * * ?` | 整点执行 |
| 每 30 分钟 | `0 */30 * * * ?` | 每半小时 |
| 每天凌晨 2 点 | `0 0 2 * * ?` | 避开业务高峰 |
| 工作日早 9 点 | `0 0 9 * * MON-FRI` | 仅工作日 |
| 每月 1 号 | `0 0 0 1 * ?` | 月度任务 |

### 4.2 fixedRate vs fixedDelay vs cron

```java
// fixedRate：固定频率（从上一次开始计时）
@Scheduled(fixedRate = 30_000)  // 每 30 秒触发，不管上次是否完成

// fixedDelay：固定延迟（从上一次完成后计时）
@Scheduled(fixedDelay = 30_000) // 上次完成后等 30 秒再触发

// cron：精确时间点
@Scheduled(cron = "0 0 6 * * ?") // 每天 06:00:00
```

> **推荐 `fixedDelay`** 用于需要防止任务重叠的场景；若任务耗时可能超过间隔，`fixedRate` 会导致任务堆积。

---

## 5. Quartz 分布式调度

### 5.1 适用场景

- 集群环境下同一任务只需一个节点执行
- 需要动态增删任务（运行时注册/取消）
- 需要任务持久化与失火处理（misfire handling）

### 5.2 基本配置

```java
@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail reportJobDetail() {
        return JobBuilder.newJob(DailyReportJob.class)
            .withIdentity("dailyReportJob")
            .storeDurably()
            .build();
    }

    @Bean
    public Trigger reportTrigger() {
        return TriggerBuilder.newTrigger()
            .forJob(reportJobDetail())
            .withIdentity("dailyReportTrigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 6 * * ?")
                .withMisfireHandlingInstructionFireAndProceed())
            .build();
    }
}
```

### 5.3 Quartz Job 实现

```java
@Slf4j
public class DailyReportJob implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap data = context.getJobDetail().getJobDataMap();
        String region = data.getString("region");

        log.info("Quartz job executing: region={}, fireTime={}", region, context.getFireTime());

        try {
            // 业务逻辑
        } catch (Exception e) {
            log.error("Quartz job failed", e);
            throw new JobExecutionException(e); // Quartz 处理重试
        }
    }
}
```

---

## 6. Kubernetes CronJob

### 6.1 适用场景

- 任务与主应用解耦（独立镜像、独立资源配额）
- 需要独立扩缩容的任务
- 夸服务编排的一次性或周期性任务

### 6.2 CronJob 定义

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: daily-report
spec:
  schedule: "0 6 * * *"
  concurrencyPolicy: Forbid       # 禁止并发
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 7
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: Never
          containers:
            - name: report
              image: myapp/report-runner:latest
              env:
                - name: REGION
                  valueFrom:
                    configMapKeyRef:
                      name: app-config
                      key: region
```

### 6.3 CronJob vs @Scheduled

| 维度 | @Scheduled | K8s CronJob |
|------|------------|-------------|
| 资源隔离 | 共享应用 JVM | 独立 Pod，资源配额隔离 |
| 部署 | 随应用部署 | 独立部署，独立回滚 |
| 监控 | 应用内 Micrometer | Pod 级别 + 应用内双指标 |
| 故障隔离 | 任务 OOM 影响主应用 | 任务 OOM 仅影响该 Pod |
| 复杂度 | 低 | 中（需要 CI/CD 流水线 + 镜像构建） |

---

## 7. 并发控制

### 7.1 单实例：禁止并发执行

```java
@Component
public class ExclusiveTask {

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 60_000)
    public void execute() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Previous execution still running, skipping");
            return;
        }
        try {
            doWork();
        } finally {
            running.set(false);
        }
    }
}
```

### 7.2 多实例：分布式锁

```java
@Component
public class ClusteredTask {

    private final StringRedisTemplate redis;

    @Scheduled(cron = "0 0 * * * ?")
    public void execute() {
        String lockKey = "lock:daily-report";
        String lockValue = hostName + ":" + Thread.currentThread().threadId();

        // Redis 分布式锁（SET NX PX）
        Boolean acquired = redis.opsForValue()
            .setIfAbsent(lockKey, lockValue, Duration.ofMinutes(10));

        if (Boolean.TRUE.equals(acquired)) {
            try {
                doWork();
            } finally {
                // 仅释放自己的锁（Lua 脚本保证原子性）
                redis.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
            }
        } else {
            log.debug("Lock held by another instance, skipping");
        }
    }

    private static final RedisScript<Boolean> UNLOCK_SCRIPT =
        new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else return 0 end
            """, Boolean.class);
}
```

---

## 8. 可观测性

### 8.1 Micrometer 指标

```java
@Component
public class MonitoredTask {

    private final MeterRegistry registry;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer executionTimer;

    public MonitoredTask(MeterRegistry registry) {
        this.registry = registry;
        this.successCounter = Counter.builder("scheduled.task.success")
            .tag("task", "daily-report")
            .register(registry);
        this.failureCounter = Counter.builder("scheduled.task.failure")
            .tag("task", "daily-report")
            .register(registry);
        this.executionTimer = Timer.builder("scheduled.task.duration")
            .tag("task", "daily-report")
            .register(registry);
    }

    @Scheduled(cron = "0 0 6 * * ?")
    public void execute() {
        Timer.Sample sample = Timer.start(registry);
        try {
            doWork();
            successCounter.increment();
        } catch (Exception e) {
            failureCounter.increment();
            throw e;
        } finally {
            sample.stop(executionTimer);
        }
    }
}
```

### 8.2 关键指标

| 指标 | 类型 | 含义 |
|------|------|------|
| `scheduled.task.duration` | Timer | 任务执行耗时分布（p50/p95/p99） |
| `scheduled.task.success` | Counter | 成功次数 |
| `scheduled.task.failure` | Counter | 失败次数 |
| `executor.queued` | Gauge | 任务队列积压量 |

### 8.3 告警规则

| 条件 | 动作 |
|------|------|
| 任务连续失败 >= 3 次 | 立即告警 |
| `scheduled.task.duration` p99 > 预期 2× | 排查慢查询 / 下游瓶颈 |
| `executor.queued` > 0 持续 5 分钟 | 任务堆积 |
| 任务最后执行时间超过预期间隔 2× | 任务卡死或调度器故障 |

---

## 9. 优雅关闭

```java
@Bean(name = "taskScheduler", destroyMethod = "close")
public ExecutorService taskScheduler() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

```yaml
spring:
  task:
    scheduling:
      shutdown:
        await-termination: true
        await-termination-period: 60s   # 等待进行中任务完成
  lifecycle:
    timeout-per-shutdown-phase: 60s
```

---

## 10. 检查清单

- [ ] 定时任务是否支持幂等（安全重复执行）？
- [ ] 是否使用虚拟线程 `newVirtualThreadPerTaskExecutor()`（I/O 密集型）？
- [ ] `fixedDelay` vs `fixedRate` 选择正确？
- [ ] 多实例场景是否使用分布式锁？
- [ ] 是否暴露 Micrometer 指标（duration / success / failure）？
- [ ] 是否配置优雅关闭（`await-termination`）？
- [ ] `@Scheduled` 方法是否 `try-catch` 防止异常吞没？
- [ ] Kubernetes CronJob 是否设置了 `concurrencyPolicy: Forbid`？

---

## 版本与变更

- 1.0.0 (2025-07-09): 初始版本；基于 Spring Boot 3.x + Java 21 Virtual Threads + Quartz + K8s CronJob 全覆盖调度规范。

