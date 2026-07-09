---
description: "DAO 层接口设计、QueryCondition、软删除与 ValidStatusEnum 使用规范"
alwaysApply: false
globs: ["**/dal/**", "**/dao/**"]
version: "1.0.0"
---

# DAO层设计规范

本规范与 [数据库设计规范](db_table_design.md)、[MyBatis Mapper 规范](db_mybatis_mapper.md)、[分页与测试配置](db_pagination_and_test.md)
共同组成 DB 技术规范体系。

## DAO接口设计约束

### 基础方法约束

1. **标准CRUD方法**：DAO层初始只需要提供基础的`insert`、`update`、`delete`方法
2. **软删除实现**：`delete`方法实际是将有效数据更新为失效状态，而非物理删除
3. **默认查询方法**：提供基础的ID查询和条件查询方法

### QueryCondition设计规范

1. **统一查询条件封装**：DAO层必须提供`QueryCondition`内部类用于封装查询条件
2. **条件复用**：通过`QueryCondition`实现不同查询条件的复用，避免为每种条件组合创建单独方法
3. **默认查询支持**：支持ID查询和通过condition查询两种基本模式

### 标准DAO接口设计模板

```java
public interface CourseDAO {

    /**
     * 根据ID查询
     */
    Course selectById(Long id);

    /**
     * 根据条件查询列表
     */
    List<Course> selectByCondition(QueryCondition condition);

    /**
     * 插入数据
     */
    int insert(Course course);

    /**
     * 更新数据
     */
    int updateById(Course course);

    /**
     * 删除数据（软删除：更新为失效状态）
     */
    int deleteById(Long id);

    /**
     * 查询条件封装类
     */
    @Data
    class QueryCondition {
        private Long id;
        private List<Long> ids;
        private String courseCode;
        private List<String> courseCodeList;
        private Integer status;
        private String region;
        private String language;
        private Integer categoryId;
        private String keyword;
        private Long startTime;
        private Long endTime;
        private Integer isValid = 1; // 默认只查询有效数据
        private String orderBy;
        private String orderDirection = "ASC";
    }
}
```

### DAO实现规范

```java
@Repository
public class CourseDAOImpl implements CourseDAO {

    @Resource
    private CourseMapper courseMapper;

    @Override
    public Course selectById(Long id) {
        Objects.requireNonNull(id, "ID不能为空");
        try {
            return courseMapper.selectByPrimaryKey(id);
        } catch (Exception e) {
            logger.error("根据ID查询失败, id: {}", id, e);
            throw new RuntimeException("根据ID查询失败", e);
        }
    }

    @Override
    public List<Course> selectByCondition(QueryCondition condition) {
        try {
            CourseExample example = buildExample(condition);
            return courseMapper.selectByExample(example);
        } catch (Exception e) {
            logger.error("根据条件查询失败, condition: {}", condition, e);
            throw new RuntimeException("根据条件查询失败", e);
        }
    }

    @Override
    public int insert(Course course) {
        Objects.requireNonNull(course, "课程对象不能为空");
        Objects.requireNonNull(course.getRegion(), "地区不能为空");

        try {
            long currentTime = System.currentTimeMillis();
            course.setCtime(currentTime);
            course.setUtime(currentTime);
            course.setIsValid(ValidStatusEnum.VALID.value());
            return courseMapper.insertSelective(course);
        } catch (Exception e) {
            logger.error("插入课程失败, course: {}", course, e);
            throw new RuntimeException("插入课程失败", e);
        }
    }

    @Override
    public int updateById(Course course) {
        Objects.requireNonNull(course, "课程对象不能为空");
        Objects.requireNonNull(course.getId(), "课程ID不能为空");

        try {
            course.setUtime(System.currentTimeMillis());
            return courseMapper.updateByPrimaryKeySelective(course);
        } catch (Exception e) {
            logger.error("更新课程失败, course: {}", course, e);
            throw new RuntimeException("更新课程失败", e);
        }
    }

    @Override
    public int deleteById(Long id) {
        Objects.requireNonNull(id, "ID不能为空");

        try {
            Course course = new Course();
            course.setId(id);
            course.setIsValid(ValidStatusEnum.IN_VALID.value());
            course.setUtime(System.currentTimeMillis());
            return courseMapper.updateByPrimaryKeySelective(course);
        } catch (Exception e) {
            logger.error("删除课程失败, id: {}", id, e);
            throw new RuntimeException("删除课程失败", e);
        }
    }

    /**
     * 构建查询条件
     */
    private CourseExample buildExample(QueryCondition condition) {
        CourseExample example = new CourseExample();
        CourseExample.Criteria criteria = example.createCriteria();

        if (condition.getId() != null) {
            criteria.andIdEqualTo(condition.getId());
        }
        if (CollectionUtils.isNotEmpty(condition.getIds())) {
            criteria.andIdIn(condition.getIds());
        }
        if (condition.getCourseCode() != null) {
            criteria.andCourseCodeEqualTo(condition.getCourseCode());
        }
        if (CollectionUtils.isNotEmpty(condition.getCourseCodeList())) {
            criteria.andCourseCodeIn(condition.getCourseCodeList());
        }
        if (condition.getStatus() != null) {
            criteria.andStatusEqualTo(condition.getStatus());
        }
        if (condition.getRegion() != null) {
            criteria.andRegionEqualTo(condition.getRegion());
        }
        if (condition.getLanguage() != null) {
            criteria.andLanguageEqualTo(condition.getLanguage());
        }
        if (condition.getCategoryId() != null) {
            criteria.andCategoryIdEqualTo(condition.getCategoryId());
        }
        if (condition.getStartTime() != null) {
            criteria.andCtimeGreaterThanOrEqualTo(condition.getStartTime());
        }
        if (condition.getEndTime() != null) {
            criteria.andCtimeLessThanOrEqualTo(condition.getEndTime());
        }

        // 软删除过滤
        if (condition.getIsValid() != null) {
            criteria.andIsValidEqualTo(condition.getIsValid());
        } else {
            criteria.andIsValidEqualTo(ValidStatusEnum.VALID.value());
        }

        // 排序
        if (condition.getOrderBy() != null) {
            example.setOrderByClause(condition.getOrderBy() + " " + condition.getOrderDirection());
        } else {
            example.setOrderByClause("ctime desc");
        }

        return example;
    }
}
```

### DAO设计约束总结

1. **方法简化**：只提供必要的CRUD方法，避免方法膨胀
2. **条件统一**：通过QueryCondition统一管理查询条件
3. **软删除**：delete操作实现为状态更新，保留数据
4. **参数校验**：所有方法都要进行必要的参数校验
5. **异常处理**：统一的异常处理和日志记录
6. **时间字段**：自动设置ctime和utime字段
7. **有效性标记**：默认只查询有效数据，支持查询所有数据
8. **枚举值使用**：更新isValid字段时必须使用ValidStatusEnum枚举值

## ValidStatusEnum使用规范

### 强制要求

在DAO层更新`is_valid`字段时，必须使用`ValidStatusEnum`枚举值，禁止直接使用数字常量。

### 正确的使用方式

```java
@Repository
public class CourseDAOImpl implements CourseDAO {

    @Override
    public int deleteById(Long id) {
        Objects.requireNonNull(id, "ID不能为空");

        try {
            Course course = new Course();
            course.setId(id);
            // ✅ 正确：使用ValidStatusEnum枚举值
            course.setIsValid(ValidStatusEnum.IN_VALID.value());
            course.setUtime(System.currentTimeMillis());
            return courseMapper.updateByPrimaryKeySelective(course);
        } catch (Exception e) {
            logger.error("删除课程失败, id: {}", id, e);
            throw new RuntimeException("删除课程失败", e);
        }
    }

    @Override
    public int insert(Course course) {
        Objects.requireNonNull(course, "课程对象不能为空");

        try {
            long currentTime = System.currentTimeMillis();
            course.setCtime(currentTime);
            course.setUtime(currentTime);
            // ✅ 正确：使用ValidStatusEnum枚举值
            course.setIsValid(ValidStatusEnum.VALID.value());
            return courseMapper.insertSelective(course);
        } catch (Exception e) {
            logger.error("插入课程失败, course: {}", course, e);
            throw new RuntimeException("插入课程失败", e);
        }
    }

    private CourseExample buildExample(QueryCondition condition) {
        CourseExample example = new CourseExample();
        CourseExample.Criteria criteria = example.createCriteria();

        // 其他条件...

        // 软删除过滤
        if (condition.getIsValid() != null) {
            criteria.andIsValidEqualTo(condition.getIsValid());
        } else {
            // ✅ 正确：使用ValidStatusEnum枚举值
            criteria.andIsValidEqualTo(ValidStatusEnum.VALID.value());
        }

        return example;
    }
}
```

### 禁止的错误做法

```java
// ❌ 错误：直接使用数字常量
course.setIsValid(0);  // 禁止
course.setIsValid(1);  // 禁止

// ❌ 错误：使用魔法数字
criteria.andIsValidEqualTo(1);  // 禁止

// ❌ 错误：自定义常量
private static final int VALID = 1;
private static final int INVALID = 0;
course.setIsValid(INVALID);  // 禁止
```

### 枚举查找规范

在实现DAO时，按以下优先级查找有效性枚举：

1. **优先查找**：`ValidStatusEnum`
2. **备选查找**：`ValidEnum`
3. **如果都不存在**：创建相应的枚举类

### 枚举值获取方法

```java
// 获取有效状态值
Integer validValue = ValidStatusEnum.VALID.value();

// 获取无效状态值
Integer invalidValue = ValidStatusEnum.IN_VALID.value();

// 根据值获取枚举（用于状态判断）
ValidStatusEnum status = ValidStatusEnum.getByValue(1);
```

### QueryCondition中的默认值设置

```java
@Data
class QueryCondition {
    // 其他字段...

    // ✅ 正确：使用枚举值作为默认值
    private Integer isValid = ValidStatusEnum.VALID.value(); // 默认只查询有效数据

    // 或者在构造方法中设置
    public QueryCondition() {
        this.isValid = ValidStatusEnum.VALID.value();
    }
}
```

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
