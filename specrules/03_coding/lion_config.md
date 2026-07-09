---
description: "ConfigCenter 配置 key 定义与按 region 读取规范：统一配置常量写法、依赖坐标与 RegionConfigUtils 使用方式，避免散落硬编码与跨 region 泄漏。"
alwaysApply: false
globs: ["**/*.java"]
---

# ConfigCenter 配置与按 region 读取规范

本规则约束 **ConfigCenter 配置 key 的定义方式** 与 **按 region 读取配置的统一用法**，防止：

- 配置 key 在各处硬编码、含义不清
- 读取 ConfigCenter 时未按 region 隔离，造成跨地区数据/行为污染
- 自建工具类或直连 ConfigCenter 客户端，导致用法不一致、难以治理

推荐统一依赖与工具类：

- **依赖坐标**：`com.example.business:common-util:1.0.42`
- **工具类**：`com.example.business.utils.RegionConfigUtils`

---

## 1. 适用范围

- 在本项目中 **新增或修改 ConfigCenter 配置** 时：
    - 定义 ConfigCenter 配置 key 常量
    - 读取 ConfigCenter 配置（特别是按地区/region 读取）
- 涉及以下典型场景：
    - 商家配置、活动配置、接入点配置等 **按地区隔离** 的参数
    - 需要在多模块/多服务间复用的配置 key

---

## 2. ConfigCenter 配置 key 定义规范

### 2.1 统一在常量类中定义 key

- **必须**在专门的配置常量类中集中定义 ConfigCenter key，例如：
    - 业务聚合类：`ConfigConstant`、`GrowthConfigConstant` 等
    - 或按子域拆分：`ExampleConfigConstant`、`XXXBizConfigConstant`
- **禁止**在业务代码中直接出现字符串字面量形式的 ConfigCenter key（如 `"example.service.endpoint"` 散落多处）。

示例：

```java
public class ConfigConstant {

    /**
     * Example接入点ID配置key
     * 香港环境：100001
     * 欧洲环境：100002
     */
    public static final String EXAMPLE_ACCESS_POINT_ID = "example.service.endpoint";

    // 其他配置 key ...
}
```

### 2.2 命名与注释要求

- **字段命名**：使用全大写 + 下划线，语义清晰，例如：
    - `EXAMPLE_ACCESS_POINT_ID`
    - `GROWTH_TASK_SWITCH`
    - `GROWTH_EXPERIMENT_BUCKET`
- **字符串值**：使用 `.` 分隔的业务前缀 + 具体含义，保持稳定，避免随意改动：
    - `"example.service.endpoint"`
    - `"growth.task.switch"`
- **Javadoc 注释必须包含**：
    - 配置含义与作用范围
    - 如为 **按 region 切分** 的配置，说明各 region 对应的典型值或取值约束
    - 若为开关类配置，约定取值（如 `0/1`、`true/false` 等）

---

## 3. ConfigCenter 读取规范（按 region 隔离）

### 3.1 Region 参数校验

- 按 region 读取 ConfigCenter 配置时，**必须先校验 region 非空**：
    - 推荐使用项目内断言工具，如：
        - `MAssert.notBlank(region, "地区不能为空");`
- **禁止**绕过 region 校验直接读取，或将 `null` / 空字符串传入工具类。

### 3.2 统一使用 RegionConfigUtils

根据配置值的**目标类型**选择不同的方法，避免类型不匹配导致运行时报错：

#### 3.2.1 非 String 类型（Integer / Long / Boolean 等）

使用 `getBeanByRegion`，传入目标类型的 `.class`：

```java
RegionConfigUtils.getBeanByRegion(region, key, 类型.class, 默认值);
```

示例：

```java
/**
 * 获取接入点ID
 *
 * @param region 地区
 * @return 接入点ID
 */
public Integer getAccessPointId(String region) {
    MAssert.notBlank(region, "地区不能为空");
    // 非 String 类型：使用 getBeanByRegion
    return RegionConfigUtils.getBeanByRegion(
            region,
            ConfigConstant.EXAMPLE_ACCESS_POINT_ID,
            Integer.class,
            20000
    );
}
```

#### 3.2.2 String 类型（⚠️ 必须使用 getByRegion）

**当配置值为 `String` 类型时，必须使用 `getByRegion`，禁止使用 `getBeanByRegion(region, key, String.class, defaultVal)`。**

> **原因**：`getBeanByRegion` 在处理 `String.class` 时会尝试进行反序列化，导致运行时报错。`String` 类型配置直接使用
`getByRegion` 获取原始字符串值，行为稳定且正确。

```java
RegionConfigUtils.getByRegion(region, key, 默认值);
```

示例：

```java
/**
 * 获取服务等级任务组编码
 *
 * @param region 地区
 * @return 任务组编码
 */
public String getServiceLevelTaskGroupCode(String region) {
    MAssert.notBlank(region, "地区不能为空");
    // String 类型：必须使用 getByRegion，禁止使用 getBeanByRegion
    return RegionConfigUtils.getByRegion(region, GrowthConfigConstant.SERVICE_LEVEL_TASK_GROUP_CODE,
            "SERVICE_LEVEL");
}
```

**反模式（禁止）**：

```java
// ❌ 错误：String 类型不可用 getBeanByRegion，会导致运行时报错
public String getServiceLevelTaskGroupCode(String region) {
    return RegionConfigUtils.getBeanByRegion(region, GrowthConfigConstant.SERVICE_LEVEL_TASK_GROUP_CODE,
            String.class, "SERVICE_LEVEL");
}
```

- 禁止以下做法：
    - 直接调用 ConfigCenter 客户端（如 `ConfigCenter.get(...)` 等）绕过 `RegionConfigUtils`
    - 自行在 key 上拼接 region（如 `"key." + region`），破坏统一管理
    - 在不同模块各自实现一套「region + ConfigCenter」封装
    - **对 String 类型配置使用 `getBeanByRegion(region, key, String.class, defaultVal)`**（运行时报错）

### 3.3 默认值选择

- 默认值必须满足：
    - **安全兜底**：不会导致严重错误或跨 region 泄漏（如默认回退到某个特定地区的配置）
    - 便于观测：出现默认值时能够通过日志/监控发现（视业务需求增加打点）
- 若配置为 **强依赖**（没有合适默认值），应：
    - 在调用前通过初始化流程或运维侧保障 ConfigCenter 中已配置
    - 读取失败时明确记录异常/日志，避免静默失败

---

## 4. 依赖与工具类约定

### 4.1 统一依赖

- 所有需要使用 `RegionConfigUtils` 的模块，统一引入依赖：

```xml
<dependency>
    <groupId>com.example.business</groupId>
    <artifactId>common-util</artifactId>
    <version>1.0.42</version>
</dependency>
```

- 若项目已有 BOM 或父 POM 管理版本，请优先通过 BOM 管理版本号，保持依赖一致。

### 4.2 工具类使用规范

- **使用统一工具类**：`com.example.business.utils.RegionConfigUtils`
- 禁止：
    - 在本项目内再定义一套同名或功能相近的 Region 配置工具类
    - 通过反射、内省等方式绕过工具类直接操作底层 ConfigCenter 客户端

---

## 5. 反模式（禁止项）

- **禁止**在业务代码中硬编码 ConfigCenter key 字符串，而不通过配置常量类：
    - 例如：`ConfigCenter.get("example.service.endpoint")` 散落在多处。
- **禁止**跨 region 泄漏的读取方式：
    - 使用固定的「默认 region」去读所有地区配置，而非按传入的业务 region。
    - 在 region 为空时静默回退到某个默认地区而不告警。
- **禁止**在一个系统内存在多种配置读取风格：
    - 一部分使用 `RegionConfigUtils`，另一部分直接 ConfigCenter 客户端或自定义工具。
- **禁止**为同一业务含义在多个常量类中定义不同的 ConfigCenter key：
    - 必须通过统一的配置常量类复用相同 key，避免不可预期的配置差异。

---

## 6. 落地建议

- 在新增 ConfigCenter 配置时：
    - 先在统一配置常量类中定义 key + 完整注释；
    - 根据配置类型选择正确的读取方式：
        - **String 类型** → `RegionConfigUtils.getByRegion(region, key, defaultVal)`
        - **非 String 类型**（Integer/Long/Boolean 等）→
          `RegionConfigUtils.getBeanByRegion(region, key, Type.class, defaultVal)`
- 在改造存量代码时：
    - 优先收敛重复 ConfigCenter key 字符串到配置常量类；
    - 将直连 ConfigCenter 读取替换为 `RegionConfigUtils`，并补充 region 校验与默认值策略；
    - 检查所有 `getBeanByRegion(region, key, String.class, ...)` 调用，替换为 `getByRegion`。
