package com.mindecho.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.module.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 聊天会话 Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 分页查询用户会话列表（按最新更新时间倒序）
     */
    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND deleted = 0 ORDER BY updated_time DESC")
    IPage<ChatSession> pageByUserId(Page<ChatSession> page, @Param("userId") Long userId);
}

