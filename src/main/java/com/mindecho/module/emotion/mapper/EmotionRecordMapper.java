package com.mindecho.module.emotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.emotion.entity.EmotionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 情绪记录 Mapper
 */
@Mapper
public interface EmotionRecordMapper extends BaseMapper<EmotionRecord> {

    /**
     * 查询用户最近情绪记录
     */
    @Select("SELECT * FROM emotion_record WHERE user_id = #{userId} AND deleted = 0 ORDER BY created_time DESC LIMIT #{limit}")
    List<EmotionRecord> findRecentByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);
}

