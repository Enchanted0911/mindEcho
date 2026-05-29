package com.mindecho.module.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.diary.entity.DiaryEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 情绪日记 Mapper
 */
@Mapper
public interface DiaryEntryMapper extends BaseMapper<DiaryEntry> {
}

