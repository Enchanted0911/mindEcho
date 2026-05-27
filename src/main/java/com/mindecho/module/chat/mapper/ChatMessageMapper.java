package com.mindecho.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询会话最近 N 条消息（按时间倒序取，返回正序）
     */
    @Select("SELECT * FROM (SELECT * FROM chat_message WHERE session_id = #{sessionId} AND deleted = 0 " +
            "ORDER BY created_time DESC LIMIT #{limit}) t ORDER BY t.created_time ASC")
    List<ChatMessage> findRecentMessages(@Param("sessionId") Long sessionId, @Param("limit") Integer limit);

    /**
     * 统计用户今日消息数
     */
    @Select("SELECT COUNT(*) FROM chat_message WHERE user_id = #{userId} AND role = 'user' " +
            "AND DATE(created_time) = CURDATE() AND deleted = 0")
    Long countTodayMessages(@Param("userId") Long userId);
}

