---
description: "枚举使用规范 - 定义枚举在领域对象中的集成模式、反模式列表和禁止的做法"
alwaysApply: false
globs: ["**/*DO.java", "**/*Service.java", "**/*ServiceImpl.java", "**/*DAO.java"]
version: "1.0.0"
---

# 枚举使用规范

## 概述

枚举的正确使用是避免魔法数字、提高代码可维护性的关键。本规范定义了枚举在领域对象、Service 和 DAO 中的使用标准和反模式列表。

## 在领域对象中的标准使用

### 标准模式 1: 枚举集成

所有关键的业务状态字段都应该提供与枚举的互操作方法。

```java
public class CourseDO {
    private Long id;
    private Integer status;  // 存储 Integer 值，便于数据库存储和查询
    private String title;
    private Long ctime;
    private Long utime;

    // ========== 枚举互操作方法 ==========

    /**
     * 获取状态枚举
     */
    public CourseStatus getStatusEnum() {
        return CourseStatus.getByValue(status);
    }

    /**
     * 设置状态枚举
     */
    public void setStatusEnum(CourseStatus statusEnum) {
        this.status = statusEnum != null ? statusEnum.getValue() : null;
        this.utime = System.currentTimeMillis();  // 自动更新修改时间
    }

    /**
     * 业务判断方法 1: 判断是否可以编辑
     */
    public boolean canEdit() {
        CourseStatus statusEnum = getStatusEnum();
        return statusEnum != null && statusEnum.canEdit();
    }

    /**
     * 业务判断方法 2: 判断是否可以提交审核
     */
    public boolean canSubmitForAudit() {
        CourseStatus statusEnum = getStatusEnum();
        return statusEnum != null && statusEnum.canSubmitAudit();
    }

    /**
     * 状态流转方法：提交审核
     */
    public void submitForAudit() {
        CourseStatus currentStatus = getStatusEnum();
        if (currentStatus == null || !currentStatus.canSubmitAudit()) {
            throw new BaseBizException("当前状态不允许提交审核，当前状态：" + 
                (currentStatus != null ? currentStatus.getDescription() : "未知"));
        }
        setStatusEnum(CourseStatus.PENDING);
    }

    /**
     * 状态流转方法：审核通过
     */
    public void approve() {
        CourseStatus currentStatus = getStatusEnum();
        if (currentStatus != CourseStatus.PENDING) {
            throw new BaseBizException("只有待审核状态才能审核通过");
        }
        setStatusEnum(CourseStatus.APPROVED);
    }

    /**
     * 状态流转方法：上线
     */
    public void goOnline() {
        CourseStatus currentStatus = getStatusEnum();
        if (currentStatus != CourseStatus.APPROVED) {
            throw new BaseBizException("只有审核通过的课程才能上线");
        }
        setStatusEnum(CourseStatus.ONLINE);
    }
}
```

### 标准模式 2: 状态流转封装

将复杂的状态流转逻辑封装在 DO 中。

```java
public class CourseAuditDO {
    private Long id;
    private Integer auditStatus;
    private String auditReason;
    private Long auditTime;

    /**
     * 获取审核状态枚举
     */
    public AuditStatus getAuditStatusEnum() {
        return AuditStatus.getByValue(auditStatus);
    }

    /**
     * 设置审核状态枚举
     */
    public void setAuditStatusEnum(AuditStatus status) {
        this.auditStatus = status != null ? status.getValue() : null;
        this.auditTime = System.currentTimeMillis();
    }

    /**
     * 审核通过
     */
    public void approve() {
        AuditStatus current = getAuditStatusEnum();
        if (current != AuditStatus.PENDING) {
            throw new BaseBizException("只有待审核状态才能审核通过，当前状态：" +
                (current != null ? current.getDescription() : "未知"));
        }
        setAuditStatusEnum(AuditStatus.APPROVED);
        this.auditReason = null;
    }

    /**
     * 审核拒绝
     */
    public void reject(String reason) {
        AuditStatus current = getAuditStatusEnum();
        if (current != AuditStatus.PENDING) {
            throw new BaseBizException("只有待审核状态才能审核拒绝，当前状态：" +
                (current != null ? current.getDescription() : "未知"));
        }
        setAuditStatusEnum(AuditStatus.REJECTED);
        this.auditReason = reason;
    }

    /**
     * 重新提交（从拒绝状态）
     */
    public void resubmit() {
        AuditStatus current = getAuditStatusEnum();
        if (current != AuditStatus.REJECTED) {
            throw new BaseBizException("只有拒绝状态才能重新提交");
        }
        setAuditStatusEnum(AuditStatus.PENDING);
        this.auditReason = null;
    }
}
```

### 标准模式 3: 组合枚举使用

当一个对象有多个枚举字段时，应该都提供互操作方法。

```java
public class CourseInteractionDO {
    private Long id;
    private Integer interactionType;  // 互动类型
    private Integer status;            // 处理状态

    /**
     * 获取互动类型枚举
     */
    public InteractionType getInteractionTypeEnum() {
        return InteractionType.getByValue(interactionType);
    }

    /**
     * 设置互动类型枚举
     */
    public void setInteractionTypeEnum(InteractionType type) {
        this.interactionType = type != null ? type.getValue() : null;
    }

    /**
     * 获取处理状态枚举
     */
    public InteractionStatus getStatusEnum() {
        return InteractionStatus.getByValue(status);
    }

    /**
     * 设置处理状态枚举
     */
    public void setStatusEnum(InteractionStatus status) {
        this.status = status != null ? status.getValue() : null;
    }

    /**
     * 判断是否可以处理
     */
    public boolean canProcess() {
        InteractionType type = getInteractionTypeEnum();
        InteractionStatus status = getStatusEnum();

        return type != null && status != null &&
               type.isUserInitiated() && status.canProcess();
    }

    /**
     * 处理互动
     */
    public void processInteraction() {
        if (!canProcess()) {
            throw new BaseBizException("当前互动不可处理");
        }

        InteractionType type = getInteractionTypeEnum();
        switch (type) {
            case LIKE:
                processLike();
                break;
            case COMMENT:
                processComment();
                break;
            case SHARE:
                processShare();
                break;
            default:
                throw new BaseBizException("不支持的互动类型: " + type.getDescription());
        }

        setStatusEnum(InteractionStatus.PROCESSED);
    }

    private void processLike() { /* ... */ }
    private void processComment() { /* ... */ }
    private void processShare() { /* ... */ }
}
```

## 避免的反模式

### 反模式 1: 直接数值比较

```java
// ❌ 反模式：直接比较数值，缺乏业务含义
public class CourseService {
    public void editCourse(CourseDO course) {
        if (course.getStatus() == 1) {  // 魔法数字，不清楚 1 代表什么
            // 可编辑逻辑
        }
    }
}

// ✅ 正确方式 1：使用枚举
public class CourseService {
    public void editCourse(CourseDO course) {
        if (course.getStatusEnum() == CourseStatus.DRAFT) {
            // 可编辑逻辑
        }
    }
}

// ✅ 正确方式 2：使用 DO 的业务方法
public class CourseService {
    public void editCourse(CourseDO course) {
        if (course.canEdit()) {
            // 可编辑逻辑
        }
    }
}
```

### 反模式 2: 重复的状态判断逻辑

```java
// ❌ 反模式：同一业务判断在多个地方重复
public class CourseService {
    public boolean canEdit(CourseDO course) {
        Integer status = course.getStatus();
        return Objects.equals(status, 1) || Objects.equals(status, 4);  // 魔法数字
    }

    public void editCourse(Long courseId) {
        CourseDO course = courseDAO.findById(courseId);
        if (!canEdit(course)) {
            throw new BaseBizException("课程不可编辑");
        }
        // 编辑逻辑
    }
}

public class CourseController {
    public void editCourse(Long courseId, CourseEditParam param) {
        CourseDO course = courseDAO.findById(courseId);
        Integer status = course.getStatus();
        if (!Objects.equals(status, 1) && !Objects.equals(status, 4)) {  // 重复逻辑！
            throw new BaseBizException("课程不可编辑");
        }
        // 调用 Service 进行编辑
    }
}

// ✅ 正确方式：封装在 DO 中
public class CourseDO {
    public boolean canEdit() {
        CourseStatus status = getStatusEnum();
        return status != null && status.canEdit();
    }
}

public class CourseService {
    public void editCourse(Long courseId) {
        CourseDO course = courseDAO.findById(courseId);
        if (!course.canEdit()) {
            throw new BaseBizException("课程不可编辑");
        }
        // 编辑逻辑
    }
}

public class CourseController {
    public void editCourse(Long courseId, CourseEditParam param) {
        CourseDO course = courseDAO.findById(courseId);
        if (!course.canEdit()) {  // 使用同一个方法
            throw new BaseBizException("课程不可编辑");
        }
        courseService.editCourse(courseId);
    }
}
```

### 反模式 3: 在 Service 中进行复杂的状态判断

```java
// ❌ 反模式：Service 中充斥着状态判断逻辑
@Service
public class CourseService {
    public void handleCourseLifecycle(CourseDO course, String action) {
        Integer status = course.getStatus();
        
        if ("submit".equals(action)) {
            if (!Objects.equals(status, 1) && !Objects.equals(status, 4)) {
                throw new BaseBizException("不能提交");
            }
        } else if ("approve".equals(action)) {
            if (!Objects.equals(status, 2)) {
                throw new BaseBizException("不能审核通过");
            }
        } else if ("publish".equals(action)) {
            if (!Objects.equals(status, 3)) {
                throw new BaseBizException("不能发布");
            }
        }
        // ... 更多状态判断
    }
}

// ✅ 正确方式：状态判断逻辑封装在 DO 中
@Service
public class CourseService {
    public void submitCourse(CourseDO course) {
        course.submitForAudit();  // DO 自动进行状态验证
        courseDAO.update(course);
    }

    public void approveCourse(CourseDO course) {
        course.approve();  // DO 自动进行状态验证
        courseDAO.update(course);
    }

    public void publishCourse(CourseDO course) {
        course.goOnline();  // DO 自动进行状态验证
        courseDAO.update(course);
    }
}
```

### 反模式 4: 在 DAO 层创建枚举

```java
// ❌ 反模式：DAO 中定义与数据库层相关的枚举
@Mapper
public interface CourseDAO {
    enum QueryStatus {
        DRAFT(1),
        PENDING(2),
        APPROVED(3);
        // ...
    }
}

// ✅ 正确方式：枚举定义在业务层，DAO 使用 Integer 值
public enum CourseStatus implements IntValueEnum {
    DRAFT(1, "草稿"),
    PENDING(2, "待审核"),
    APPROVED(3, "审核通过");
    // ...
}

@Mapper
public interface CourseDAO {
    // 使用 Integer 参数
    List<CourseDO> selectByStatus(Integer status);
    
    // 或者使用 Integer List
    List<CourseDO> selectByStatusList(List<Integer> statusList);
}
```

### 反模式 5: 过度使用 switch-case

```java
// ⚠️ 可以接受但不推荐：大量 switch-case
public class CourseService {
    public String getStatusDescription(CourseDO course) {
        switch (course.getStatus()) {
            case 1: return "草稿";
            case 2: return "待审核";
            case 3: return "审核通过";
            case 4: return "审核拒绝";
            default: return "未知";
        }
    }
}

// ✅ 更好的方式：使用枚举的描述方法
public class CourseService {
    public String getStatusDescription(CourseDO course) {
        CourseStatus status = course.getStatusEnum();
        return status != null ? status.getDescription() : "未知";
    }
}
```

## Service 层的最佳实践

### 示例 1: 业务流程编排

```java
@Service
public class CourseService {
    
    public void submitCourseForAudit(Long courseId, String submitReason) {
        CourseDO course = courseDAO.findById(courseId);
        
        // ✅ 使用 DO 的业务方法进行状态验证
        course.submitForAudit();
        
        // 执行业务逻辑
        course.setSubmitReason(submitReason);
        courseDAO.update(course);
        
        // 发送事件或 MQ
        publishCourseSubmittedEvent(course);
    }

    public void approveCourse(Long courseId, String approvalReason) {
        CourseDO course = courseDAO.findById(courseId);
        
        // ✅ 使用 DO 的业务方法进行状态流转
        course.approve();
        
        course.setApprovalReason(approvalReason);
        courseDAO.update(course);
        
        publishCourseApprovedEvent(course);
    }

    public void publishCourse(Long courseId) {
        CourseDO course = courseDAO.findById(courseId);
        
        // ✅ 使用 DO 的业务方法进行状态流转
        course.goOnline();
        
        courseDAO.update(course);
        
        publishCoursePublishedEvent(course);
    }
}
```

### 示例 2: 查询列表过滤

```java
public class CourseService {
    
    public List<CourseDTO> getEditableCourses(Long merchantId) {
        // ✅ 在 DAO 中使用 Integer 值
        List<Integer> editableStatuses = Arrays.asList(
            CourseStatus.DRAFT.getValue(),
            CourseStatus.REJECTED.getValue()
        );
        
        List<CourseDO> courses = courseDAO.selectByMerchantAndStatuses(
            merchantId, 
            editableStatuses
        );
        
        // ✅ 在返回给前端前再次验证
        return courses.stream()
            .filter(CourseDO::canEdit)
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
}
```

## 检查清单

- [ ] 所有业务状态字段是否都提供了 `getXxxEnum()` 方法？
- [ ] 所有业务状态字段是否都提供了 `setXxxEnum()` 方法？
- [ ] DO 中是否定义了常用的业务判断方法？
- [ ] DO 中是否定义了状态流转方法？
- [ ] Service 中是否避免了直接的 Integer 值比较？
- [ ] Service 中是否使用了 DO 的业务方法而非直接比较？
- [ ] 是否避免了在多个地方重复相同的状态判断逻辑？
- [ ] DAO 查询是否接受 Integer 值而非枚举对象？
- [ ] 是否避免了在 Service 中进行复杂的 switch-case 状态判断？
- [ ] 是否使用了 do.canXxx() 而非 do.getStatus() == value？

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
