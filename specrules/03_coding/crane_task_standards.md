---
description: "TaskScheduler 定时任务执行类编写规范：@ScheduledTaskConfig 与 @ScheduledTask 注解用法及代码示例"
alwaysApply: false
globs: ["**/job/**/*.java", "**/*Task*.java", "**/*Job*.java"]
version: "1.0.0"
---

# TaskScheduler 任务执行类规范

TaskScheduler 通过扫描并执行标注了特定注解的类与方法，完成定时或补偿任务。编写 TaskScheduler 任务执行类时需满足以下约定。

## 1. 类级：@ScheduledTaskConfig

- **用途**：标识该类为 TaskScheduler 的**执行配置类**，TaskScheduler 会扫描并注册该类下标注了 `@ScheduledTask` 的方法为可执行任务。
- **要求**：任务执行类**必须在类上标注 `@ScheduledTaskConfig`**，否则其中的 `@ScheduledTask` 方法不会被 TaskScheduler 识别。

## 2. 方法级：@ScheduledTask("任务名")

- **用途**：将当前方法映射为 TaskScheduler 平台上的**一个具体可执行任务**。
- **要求**：
    - 在具体任务方法上使用 `@ScheduledTask("任务名")`，`"任务名"` 与 TaskScheduler 平台上配置的任务标识一致。
    - 方法签名可按业务需要定义（如无参、或带 String 等参数，由 TaskScheduler 传参）。

## 3. 代码示例

以下示例展示类上 `@ScheduledTaskConfig`、方法上 `@ScheduledTask("unbundleDeviceTask")` 的写法；业务逻辑仅为示意，重点为注解与结构。

```java
@Slf4j
@ScheduledTaskConfig
public class MerchantDeviceUnbundleTask {

    private static final String NO_PUSH_TOKEN_ERROR = "medusaProxy getPushTokenByPoiIds is null";

    @Autowired
    private MerchantDeviceInfoManager merchantDeviceInfoManager;

    @ScheduledTask("unbundleDeviceTask")
    public void unbundleDeviceTask(String deviceSnConfig) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info("MerchantDeviceUnbundleTask.unbundleDeviceTask start");
        log.info("MerchantDeviceUnbundleTask.unbundleDeviceTask deviceSnConfig:{}", deviceSnConfig);
        int unbundleCount = 0;
        try {
            if (StringUtils.isBlank(deviceSnConfig)) {
                return;
            }
            List<String> deviceSnList = Arrays.asList(deviceSnConfig.split(","));

            for (String deviceSn : deviceSnList) {
                // 查询是否有效
                MerchantDeviceInfoDTO deviceInfoDTO = merchantDeviceInfoManager.getOne(Wrappers
                        .lambdaQuery(MerchantDeviceInfoDTO.class).eq(MerchantDeviceInfoDTO::getDeviceSn, deviceSn)
                        .eq(MerchantDeviceInfoDTO::getScene, DeviceReportSceneEnum.HEART_REPORT.getCode())
                        .eq(MerchantDeviceInfoDTO::getValid, ValidEnum.VALID.getStatus()));
                if (Objects.isNull(deviceInfoDTO)) {
                    continue;
                }
                // 门店-设备绑定关系改为无效
                deviceInfoDTO.setValid(ValidEnum.INVALID.getStatus());
                merchantDeviceInfoManager.updateById(deviceInfoDTO);
                log.info("MerchantDeviceUnbundleTask.unbundleDeviceTask, deviceSn:{}", deviceSn);
                unbundleCount++;
            }
        } catch (Exception e) {
            log.error("MerchantDeviceUnbundleTask.unbundleDeviceTask error, ", e);
        }
        stopWatch.stop();
        log.info("MerchantDeviceUnbundleTask.unbundleDeviceTask end, taskRunTime:{}, unbundleCount:{}",
                stopWatch.getTotalTimeMillis(), unbundleCount);
    }
}
```

**要点小结**：

| 位置    | 注解 / 要求                               |
|-------|---------------------------------------|
| 类上    | `@ScheduledTaskConfig`，标注为 TaskScheduler 执行配置类 |
| 任务方法上 | `@ScheduledTask("任务名")`，与 TaskScheduler 平台上的任务标识一致    |

## 4. 与本项目其他 TaskScheduler 用法的关系

- **注解执行**：本文约定的是 TaskScheduler **直接扫描并执行**的 Java 类写法（`@ScheduledTaskConfig` + `@ScheduledTask`），适用于在 TaskScheduler
  平台配置为「执行该类方法」的任务。
- **接口暴露方式**：本项目中对外接口（含服务等级查询等）均通过 **Gateway 下 RPC** 提供，不通过 HTTP Controller 暴露；TaskScheduler
  任务仅通过上述注解执行类触发，无 TaskSchedulerTaskController。

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
