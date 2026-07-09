---
description: "MyBatis Mapper 主键回填、字段完整性与 Region 数据隔离规范"
alwaysApply: false
globs: ["**/mapper/**", "**/*Mapper.java", "**/*SqlProvider.java"]
version: "1.0.0"
---

# MyBatis Mapper规范

本规范与 [数据库设计规范](db_table_design.md)、[DAO 层设计规范](db_dao.md)、[分页与测试配置](db_pagination_and_test.md)
共同组成 DB 技术规范体系。

## 生成代码约束（REQUIRED）

**生成代码时不要生成 Mapper 接口（`*Mapper.java`）、对应 XML 映射文件及 SqlProvider 类。** 本规范仅用于人工实现时的参考，或用于评审已有
Mapper/XML 是否符合规范；若需新增 Mapper 或 XML，由开发人员按本规范手写。

## 主键回填配置

所有insert和insertSelective方法必须配置主键回填，确保插入后能够获取自动生成的主键值。

### 配置方法

```java
@Mapper
public interface CourseInfoMapper {

    /**
     * 插入课程信息（主键回填）
     */
    @Insert("INSERT INTO course_info (course_code, course_name, status, region, ctime, utime) " +
            "VALUES (#{courseCode}, #{courseName}, #{status}, #{region}, #{ctime}, #{utime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CourseInfo courseInfo);

    /**
     * 选择性插入课程信息（主键回填）
     */
    @InsertProvider(type = CourseInfoSqlProvider.class, method = "insertSelective")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(CourseInfo courseInfo);
}
```

### 关键配置说明

- **useGeneratedKeys = true**：启用主键回填
- **keyProperty = "id"**：指定主键字段名
- 插入后实体对象的id字段会自动填充生成的主键值

## 字段完整性检查

### SqlProvider字段处理

所有SqlProvider中的方法必须包含实体类的所有字段处理，确保字段映射的完整性。

```java
public class CourseInfoSqlProvider {

    public String insertSelective(CourseInfo courseInfo) {
        SQL sql = new SQL();
        sql.INSERT_INTO("course_info");

        // 必须包含所有字段的条件判断
        if (courseInfo.getCourseCode() != null) {
            sql.VALUES("course_code", "#{courseCode}");
        }
        if (courseInfo.getCourseName() != null) {
            sql.VALUES("course_name", "#{courseName}");
        }
        if (courseInfo.getStatus() != null) {
            sql.VALUES("status", "#{status}");
        }
        // region字段必须处理
        if (courseInfo.getRegion() != null) {
            sql.VALUES("region", "#{region}");
        }
        if (courseInfo.getCtime() != null) {
            sql.VALUES("ctime", "#{ctime}");
        }
        if (courseInfo.getUtime() != null) {
            sql.VALUES("utime", "#{utime}");
        }

        return sql.toString();
    }

    public String updateByExampleSelective(Map<String, Object> parameter) {
        CourseInfo record = (CourseInfo) parameter.get("record");
        CourseInfoExample example = (CourseInfoExample) parameter.get("example");

        SQL sql = new SQL();
        sql.UPDATE("course_info");

        // 必须包含所有字段的条件判断
        if (record.getCourseCode() != null) {
            sql.SET("course_code = #{record.courseCode}");
        }
        if (record.getCourseName() != null) {
            sql.SET("course_name = #{record.courseName}");
        }
        if (record.getStatus() != null) {
            sql.SET("status = #{record.status}");
        }
        // region字段通常不允许更新
        if (record.getUtime() != null) {
            sql.SET("utime = #{record.utime}");
        }

        applyWhere(sql, example, false);
        return sql.toString();
    }
}
```

### 查询字段一致性

查询方法的字段列表必须与实体类字段保持一致，避免字段遗漏。

```java
@Select("SELECT id, course_code, course_name, status, region, ctime, utime " +
        "FROM course_info WHERE region = #{region} AND status = #{status}")
List<CourseInfo> selectByRegionAndStatus(@Param("region") String region,
                                         @Param("status") Integer status);
```

## Region数据隔离规范

### 查询方法Region参数

所有查询方法都必须包含region参数，实现数据按地区隔离。

```java
@Mapper
public interface CourseInfoMapper {

    /**
     * 根据ID和region查询课程信息
     */
    @Select("SELECT * FROM course_info WHERE id = #{id} AND region = #{region}")
    CourseInfo selectByIdAndRegion(@Param("id") Long id, @Param("region") String region);

    /**
     * 根据课程编码和region查询
     */
    @Select("SELECT * FROM course_info WHERE course_code = #{courseCode} AND region = #{region}")
    CourseInfo selectByCourseCodeAndRegion(@Param("courseCode") String courseCode,
                                           @Param("region") String region);

    /**
     * 分页查询（必须包含region条件）
     */
    @Select("SELECT * FROM course_info WHERE region = #{region} AND status = #{status} " +
            "ORDER BY ctime DESC")
    List<CourseInfo> selectByRegionAndStatus(@Param("region") String region,
                                             @Param("status") Integer status);
}
```

### Region字段处理规范

1. **查询条件**：所有查询都必须包含region条件
2. **插入操作**：创建数据时必须设置正确的region字段
3. **更新操作**：不允许修改region字段
4. **删除操作**：必须验证region权限

---

## 版本与变更

- 1.0.0 (2025-02-06): 初始化版本与变更记录
