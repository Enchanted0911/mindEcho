package com.mindecho.module.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.memory.entity.Memory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 记忆 Mapper
 */
@Mapper
public interface MemoryMapper extends BaseMapper<Memory> {

    /**
     * 查询用户所有记忆（按重要度降序）
     */
    @Select("SELECT * FROM memory WHERE user_id = #{userId} AND deleted = 0 ORDER BY importance_score DESC")
    List<Memory> findByUserId(@Param("userId") Long userId);

    /**
     * 按类型查询记忆
     */
    @Select("SELECT * FROM memory WHERE user_id = #{userId} AND memory_type = #{type} AND deleted = 0 ORDER BY importance_score DESC")
    List<Memory> findByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);
}

