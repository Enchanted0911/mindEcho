package com.mindecho.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.module.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询会话最近 N 条消息（按时间倒序取，返回正序）
     * 用于构建 AI 上下文（内部调用）
     */
    @Select("SELECT * FROM (SELECT * FROM chat_message WHERE session_id = #{sessionId} AND deleted = 0 " +
            "ORDER BY created_time DESC LIMIT #{limit}) t ORDER BY t.created_time ASC")
    List<ChatMessage> findRecentMessages(@Param("sessionId") Long sessionId, @Param("limit") Integer limit);

    /**
     * 分页查询会话消息（按时间倒序，用于前端懒加载历史消息）
     * 前端第 1 页拿最新消息，向上滚动时加载第 2、3... 页
     */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY created_time DESC")
    IPage<ChatMessage> pageBySessionId(Page<ChatMessage> page, @Param("sessionId") Long sessionId);

    /**
     * 统计用户今日消息数
     */
    @Select("SELECT COUNT(*) FROM chat_message WHERE user_id = #{userId} AND role = 'user' " +
            "AND DATE(created_time) = CURDATE() AND deleted = 0")
    Long countTodayMessages(@Param("userId") Long userId);

    /**
     * 逻辑删除指定会话下的所有消息（级联删除）
     */
    @Update("UPDATE chat_message SET deleted = 1 WHERE session_id = #{sessionId} AND deleted = 0")
    int deleteBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 逻辑删除指定消息之后（创建时间更晚）的所有消息，用于编辑消息时清除后续上下文
     */
    @Update("UPDATE chat_message SET deleted = 1 WHERE session_id = #{sessionId} " +
            "AND created_time > #{afterTime} AND deleted = 0")
    int deleteAfterTime(@Param("sessionId") Long sessionId, @Param("afterTime") java.time.LocalDateTime afterTime);

    /**
     * 逻辑删除指定消息本身及之后（创建时间 >= fromTime）的所有消息
     * 用于编辑消息时：连同被编辑的消息一起清除，再由 sendMessage 重新写入
     */
    @Update("UPDATE chat_message SET deleted = 1 WHERE session_id = #{sessionId} " +
            "AND created_time >= #{fromTime} AND deleted = 0")
    int deleteFromTime(@Param("sessionId") Long sessionId, @Param("fromTime") java.time.LocalDateTime fromTime);
}

