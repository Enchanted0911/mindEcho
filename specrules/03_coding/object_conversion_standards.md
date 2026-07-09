---
description: "DTO/BO/DO/Entity 对象转换规范：分层转换职责、Converter 位置与命名、静态工具类、禁止调用服务实现类方法"
alwaysApply: false
globs: ["**/*Converter*.java", "**/converter/**/*.java", "**/model/*DO.java", "**/bo/*BO.java"]
version: "1.0.0"
---

# 对象转换规范

本文档统一约定 DTO、VO、BO、DO、Entity
等对象之间的转换职责、转换器位置与实现方式。与 [数据对象命名规范](specrules/00_general/naming/data_object_naming.md) 配合使用。

## 1. 分层转换职责总览

| 转换方向                             | 职责层            | 简单转换                        | 复杂转换（多参数拼装）    | Converter 目录                 |
|----------------------------------|----------------|-----------------------------|----------------|------------------------------|
| DTO/VO ↔ BO                      | 用户接口层（starter） | —                           | 一律使用 Converter | `starter/.../converter/`     |
| BO → DO                          | 应用层            | 封装在 BO（toXxxDO / fromXxxDO） | 单独 Converter   | `application/.../converter/` |
| DO → Entity / Cache / 外部 API DTO | 领域层 / 基础设施     | 封装在 DO（toXxxEntity 等）       | 单独 Converter   | `domain/.../converter/`      |

## 2. 用户接口层（Starter）转换

- **一律使用 Converter** 进行转换，不放在 DTO/VO/BO 内部。
- **命名**：`xxDTO2xxBO`、`xxDTOList2BOList`、`xxBO2xxVO` 等，语义清晰、成对可读。
- **位置**：`starter` 模块下 `converter` 包（或与现有结构一致的 converter 目录）。
- **示例**：

```java
// starter 层 Converter：静态工具类，禁止 @Component / @Resource
public final class TaskDTOConverter {

    private TaskDTOConverter() {}

    public static TaskBO dto2BO(TaskDTO dto) {
        if (dto == null) return null;
        TaskBO bo = new TaskBO();
        bo.setTaskCode(dto.getTaskCode());
        bo.setTaskName(dto.getTaskName());
        bo.setRegion(dto.getRegion());
        return bo;
    }

    public static List<TaskBO> dtoList2BOList(List<TaskDTO> dtoList) {
        if (dtoList == null) return Collections.emptyList();
        return dtoList.stream().map(TaskDTOConverter::dto2BO).collect(Collectors.toList());
    }

    public static TaskDTO bo2DTO(TaskBO bo) {
        if (bo == null) return null;
        TaskDTO dto = new TaskDTO();
        dto.setTaskCode(bo.getTaskCode());
        dto.setTaskName(bo.getTaskName());
        dto.setRegion(bo.getRegion());
        return dto;
    }
}
```

## 3. BO → DO 转换（应用层）

- **简单转换**（单对象、字段一一对应）：封装在 **BO** 中，方法名如 `toXxxDO()`、`static fromXxxDO(XxxDO do)`。
- **复杂转换**（多参数拼装、多对象组合、列表+额外上下文）：使用 **单独 Converter**，放在 `application/.../converter/` 下，方法为
  **静态方法**。
- **禁止**：在应用层或领域层通过「直接调用某 Service 实现类的静态/实例转换方法」完成 BO→DO 或 DO→其他对象的转换（见 §6）。

**BO 内简单转换示例**：

```java
// application/bo/TaskBO.java
public class TaskBO {
    private String taskCode;
    private String taskName;
    private String region;

    public TaskDO toTaskDO() {
        TaskDO do_ = new TaskDO();
        do_.setTaskCode(this.taskCode);
        do_.setTaskName(this.taskName);
        do_.setRegion(this.region);
        return do_;
    }

    public static TaskBO fromTaskDO(TaskDO do_) {
        if (do_ == null) return null;
        TaskBO bo = new TaskBO();
        bo.setTaskCode(do_.getTaskCode());
        bo.setTaskName(do_.getTaskName());
        bo.setRegion(do_.getRegion());
        return bo;
    }
}
```

**应用层复杂转换使用 Converter 示例**：

```java
// application/converter/TaskGroupConfigConverter.java（静态类，无 @Component）
public final class TaskGroupConfigConverter {

    private TaskGroupConfigConverter() {}

    /**
     * 多参数、多对象拼装时使用 Converter，而非 BO 内方法
     */
    public static TaskGroupConfigDO fromExternalDTO(String region, TaskGroupDTO dto, List<TaskConfigItemDO> taskList) {
        if (dto == null) return null;
        TaskGroupConfigDO do_ = new TaskGroupConfigDO();
        do_.setRegion(region);
        do_.setGroupCode(dto.getGroupCode());
        do_.setTaskList(taskList);
        // ... 其他拼装
        return do_;
    }
}
```

## 4. DO → Entity / Cache / 外部 API DTO（领域层）

- **简单转换**（单 DO 与单 Entity/Cache/DTO 一一对应）：封装在 **DO** 中，方法名如 `toXxxEntity()`、
  `static fromXxxEntity(XxxEntity entity)`；若为 Cache 或外部 DTO，可用 `toXxxCacheDTO()`、`toXxxApiDTO()` 等。
- **复杂转换**（多参数拼装、多对象组合）：使用 **单独 Converter**，放在 `domain/.../converter/` 下，方法为 **静态方法**。

**DO 内简单转换示例**：

```java
// domain/model/TaskDO.java
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

**领域层复杂转换使用 Converter 示例**：

```java
// domain/converter/TaskGroupConfigDomainConverter.java（静态类，无 @Component）
public final class TaskGroupConfigDomainConverter {

    private TaskGroupConfigDomainConverter() {}

    public static TaskGroupCacheDTO toCacheDTO(TaskGroupConfigDO do_, String version) {
        if (do_ == null) return null;
        TaskGroupCacheDTO dto = new TaskGroupCacheDTO();
        dto.setGroupCode(do_.getGroupCode());
        dto.setVersion(version);
        dto.setTaskList(do_.getTaskList().stream()
                .map(TaskConfigItemDO::toCacheItem)
                .collect(Collectors.toList()));
        return dto;
    }
}
```

## 5. Converter 统一约束 (NON-NEGOTIABLE)

- **禁止** 使用 Spring 的 `@Component`（以及 `@Service`、`@Bean` 等）将 Converter 注册为 Bean。
- **禁止** 通过 `@Resource`、`@Autowired` 等注入 Converter；调用方应直接使用 **静态方法** 调用。
- Converter 类应为 **仅含静态方法的工具类**：
    - 建议使用 `public static Xxx toYyy(...)` 形式。
    - 建议类加 `final`、提供 `private` 无参构造函数以避免被实例化。
- **命名**：Starter 层可用 `xxDTO2xxBO`、`xxDTOList2BOList`；应用层/领域层 Converter 方法名应表达语义，如 `toXxxDO`、
  `fromXxxDO`、`toXxxEntity`、`toXxxCacheDTO` 等。

## 6. 禁止的写法 (NON-NEGOTIABLE)

**禁止** 在流式调用或任意业务代码中，直接引用 **服务实现类** 的静态或实例方法做对象转换。例如：

```java
// ❌ 禁止：直接调用领域服务实现类的静态方法做转换
taskGroupConfigDO.setTaskList(dto.getTaskList().stream()
        .map(GrowthConfigDomainServiceImpl::toTaskConfigItemDO)
        .collect(Collectors.toList()));
```

**正确做法**：

- 若为简单转换：在 **DO/BO** 上提供 `toXxxDO()`、`fromXxxDO()` 或 `TaskConfigItemDO.fromDTO(xxx)` 等，在 stream 中调用该
  DO/BO 的静态方法或实例方法。
- 若为复杂转换：在 **application 或 domain 的 Converter** 中提供静态方法，如
  `TaskGroupConfigConverter.dtoList2TaskConfigItemDOList(dto.getTaskList())`，业务代码调用 Converter 静态方法。

```java
// ✅ 正确：使用 DO 的静态方法或专用 Converter
taskGroupConfigDO.setTaskList(TaskConfigItemDO.fromDTOList(dto.getTaskList()));
// 或
taskGroupConfigDO.setTaskList(TaskGroupConfigConverter.dtoList2TaskConfigItemDOList(dto.getTaskList()));
```

## 7. 与现有规范的关系

- [数据对象命名规范](specrules/00_general/naming/data_object_naming.md)：约定 DTO/VO/BO/DO/Entity
  的命名与所属层级；其中「数据转换规则」以本规范为准，本规范未提及的仍按该文档层级约定执行。
- [应用层标准](specrules/00_general/architecture/app_layer_standards.md)：应用层 BO↔DO 转换方式以本规范 §3、§5
  为准（Converter 为静态、不注入）。
- [用户接口层标准](specrules/00_general/architecture/ui_layer_standards.md)：Starter 层 DTO↔BO 以本规范 §2、§5 为准（一律
  Converter、静态、不注入）。
- [领域层标准](specrules/00_general/architecture/domain_layer_standards.md)：DO↔Entity 等以本规范 §4、§5 为准。

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始版本；统一 Starter/Application/Domain 转换职责、Converter 静态化与禁止注入、禁止调用服务实现类转换方法。
