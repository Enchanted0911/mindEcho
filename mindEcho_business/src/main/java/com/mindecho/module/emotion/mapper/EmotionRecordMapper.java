package com.mindecho.module.emotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.emotion.entity.EmotionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 情绪记录 Mapper
 */
@Mapper
public interface EmotionRecordMapper extends BaseMapper<EmotionRecord> {
}

