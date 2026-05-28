package com.mindecho.module.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.module.diary.entity.DiaryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 情绪日记 Mapper
 */
@Mapper
public interface DiaryEntryMapper extends BaseMapper<DiaryEntry> {

    /**
     * 分页查询用户日记列表
     */
    @Select("SELECT * FROM diary_entry WHERE user_id = #{userId} AND deleted = 0 ORDER BY diary_date DESC")
    IPage<DiaryEntry> pageByUserId(Page<DiaryEntry> page, @Param("userId") Long userId);

    /**
     * 查询某日日记
     */
    @Select("SELECT * FROM diary_entry WHERE user_id = #{userId} AND diary_date = #{date} AND deleted = 0")
    DiaryEntry findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}

