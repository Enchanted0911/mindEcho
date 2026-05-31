package com.mindecho.module.chat.advisor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mindecho.common.enums.RoleEnum;
import com.mindecho.module.chat.entity.ChatMessage;
import com.mindecho.module.chat.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 基于 PostgreSQL 的聊天记忆存储库
 *
 * <p>将 Spring AI 的 {@link ChatMemoryRepository} 接口与现有的 {@code chat_message} 表对接，
 * 使 Spring AI 的 {@code MessageWindowChatMemoryAdvisor} 能够直接读写持久化的对话历史。
 *
 * <p>会话 ID 约定：使用 {@code "session:{sessionId}"} 格式，以便从会话 UUID 中提取。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MindEchoChatMemoryRepository implements ChatMemoryRepository {

    /** 会话 ID 前缀（conversationId = "session:{sessionId}"） */
    public static final String SESSION_PREFIX = "session:";

    /** 短期记忆：最多保留最近 N 轮历史消息 */
    private static final int MAX_HISTORY_MESSAGES = 20;

    private final ChatMessageMapper chatMessageMapper;

    // ─────────────────────── ChatMemoryRepository 接口实现 ────────────────────

    @Override
    public List<String> findConversationIds() {
        // 本实现不需要列出所有会话 ID（仅用于按需查询）
        return Collections.emptyList();
    }

    /**
     * 加载指定会话的历史消息（短期记忆，最近 N 轮）
     *
     * @param conversationId 格式 "session:{sessionUUID}"
     * @return 按时间升序排列的消息列表
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        UUID sessionId = extractSessionId(conversationId);
        if (sessionId == null) {
            log.warn("MindEchoChatMemoryRepository: invalid conversationId={}", conversationId);
            return Collections.emptyList();
        }

        List<ChatMessage> history = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getDeleted, 0)
                        .orderByDesc(ChatMessage::getCreatedTime)
                        .last("LIMIT " + MAX_HISTORY_MESSAGES)
        );

        // 数据库查询是倒序，需要反转为时间升序（符合对话上下文顺序）
        List<ChatMessage> ordered = new java.util.ArrayList<>(history);
        Collections.reverse(ordered);

        return ordered.stream()
                .<Message>map(msg -> {
                    if (RoleEnum.USER.getCode().equals(msg.getRole())) {
                        return new UserMessage(msg.getContent());
                    } else {
                        return new AssistantMessage(msg.getContent());
                    }
                })
                .toList();
    }

    /**
     * 保存新消息到会话历史
     *
     * <p>注意：Spring AI 的 {@code MessageWindowChatMemoryAdvisor} 在每次交互时会调用此方法
     * 保存用户消息和助手回复。但 ChatService 已通过 {@code saveMessage} 方法直接写入，
     * 因此这里做幂等处理：如果内容相同则跳过重复写入。
     *
     * <p>实际上我们让 ChatService 负责写入，这里只做空实现，防止重复写入。
     *
     * @param conversationId 格式 "session:{sessionUUID}"
     * @param messages       要保存的消息列表（新消息追加在末尾）
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // ChatService 已负责消息持久化，此处无需重复写入
        // Spring AI 的 MessageWindowChatMemoryAdvisor 仅利用 findByConversationId 读取历史
        // saveAll 调用在 Advisor 执行链中会被触发，但我们的消息已在 ChatService 中持久化
        log.debug("MindEchoChatMemoryRepository.saveAll: conversationId={}, count={} (skipped, ChatService handles persistence)",
                conversationId, messages.size());
    }

    /**
     * 删除指定会话的所有历史消息（逻辑删除）
     *
     * @param conversationId 格式 "session:{sessionUUID}"
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        UUID sessionId = extractSessionId(conversationId);
        if (sessionId == null) return;

        chatMessageMapper.update(null,
                new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getDeleted, 0)
                        .set(ChatMessage::getDeleted, 1)
        );
        log.debug("MindEchoChatMemoryRepository: deleted messages for conversationId={}", conversationId);
    }

    // ─────────────────────── 工具方法 ─────────────────────────────────────────

    /**
     * 构造 conversationId（供 ChatService 使用）
     */
    public static String buildConversationId(UUID sessionId) {
        return SESSION_PREFIX + sessionId.toString();
    }

    /**
     * 从 conversationId 中提取 sessionId
     */
    private static UUID extractSessionId(String conversationId) {
        if (conversationId == null || !conversationId.startsWith(SESSION_PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(conversationId.substring(SESSION_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            log.warn("MindEchoChatMemoryRepository: cannot parse sessionId from '{}'", conversationId);
            return null;
        }
    }
}

