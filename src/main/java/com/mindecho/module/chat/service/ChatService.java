package com.mindecho.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.common.enums.PersonalityEnum;
import com.mindecho.common.enums.RiskLevelEnum;
import com.mindecho.common.enums.RoleEnum;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.auth.entity.User;
import com.mindecho.module.auth.mapper.UserMapper;
import com.mindecho.module.chat.dto.ChatMessageDTO;
import com.mindecho.module.chat.dto.ChatSessionDTO;
import com.mindecho.module.chat.dto.SendMessageRequest;
import com.mindecho.module.chat.entity.ChatMessage;
import com.mindecho.module.chat.entity.ChatSession;
import com.mindecho.module.chat.mapper.ChatMessageMapper;
import com.mindecho.module.chat.mapper.ChatSessionMapper;
import com.mindecho.module.emotion.service.EmotionService;
import com.mindecho.module.memory.service.MemoryService;
import com.mindecho.module.prompt.service.PromptService;
import com.mindecho.module.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天服务（核心模块）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final UserMapper userMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final PromptService promptService;
    private final RiskService riskService;
    private final EmotionService emotionService;
    private final MemoryService memoryService;

    @Value("${mindecho.free-daily-messages:10}")
    private Integer freeDailyMessages;

    /**
     * 发送消息（SSE 流式返回）
     */
    public SseEmitter sendMessage(Long userId, SendMessageRequest request) {
        // 1. 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 检查每日限制
        checkDailyLimit(userId, user);

        // 3. 敏感词和风险检测
        RiskLevelEnum riskLevel = riskService.detectRisk(request.getMessage());
        if (riskLevel.isHigh()) {
            return handleHighRiskMessage(userId, request);
        }

        // 4. 获取或创建会话
        ChatSession session = getOrCreateSession(userId, request.getSessionId(), user.getPersonality());

        // 5. 保存用户消息
        ChatMessage userMsg = saveMessage(session.getId(), userId, RoleEnum.USER.getCode(),
                request.getMessage(), null, riskLevel.getCode());

        // 6. 情绪分析（异步）
        String emotion = emotionService.analyzeEmotion(request.getMessage());
        userMsg.setEmotion(emotion);
        chatMessageMapper.updateById(userMsg);

        // 7. 构建 Prompt 消息列表
        List<Message> messages = buildMessages(userId, session, request.getMessage());

        // 8. SSE 流式输出
        SseEmitter emitter = new SseEmitter(60000L);
        StringBuilder fullResponse = new StringBuilder();

        try {
            chatClient.prompt()
                    .messages(messages)
                    .stream()
                    .chatResponse()
                    .subscribe(
                            chunk -> {
                                String text = chunk.getResult().getOutput().getContent();
                                if (text != null && !text.isEmpty()) {
                                    fullResponse.append(text);
                                    try {
                                        emitter.send(SseEmitter.event().data(text));
                                    } catch (IOException e) {
                                        log.error("SSE send error", e);
                                        emitter.completeWithError(e);
                                    }
                                }
                            },
                            error -> {
                                log.error("AI chat error", error);
                                emitter.completeWithError(error);
                            },
                            () -> {
                                // 保存 AI 回复
                                saveMessage(session.getId(), userId, RoleEnum.ASSISTANT.getCode(),
                                        fullResponse.toString(), null, RiskLevelEnum.LOW.getCode());

                                // 更新会话标题（第一条消息）
                                updateSessionTitle(session, request.getMessage());

                                // 异步提取记忆
                                memoryService.extractAndSaveMemoryAsync(userId, request.getMessage(), fullResponse.toString());

                                try {
                                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                emitter.complete();
                            }
                    );
        } catch (Exception e) {
            log.error("Chat error", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 处理高风险消息（返回安全话术）
     */
    private SseEmitter handleHighRiskMessage(Long userId, SendMessageRequest request) {
        // 保存高风险消息记录
        if (request.getSessionId() != null) {
            saveMessage(request.getSessionId(), userId, RoleEnum.USER.getCode(),
                    request.getMessage(), null, RiskLevelEnum.HIGH.getCode());
        }

        String safetyResponse = riskService.getSafetyResponse();
        SseEmitter emitter = new SseEmitter(5000L);

        try {
            // 逐字符流式输出安全话术
            for (String part : safetyResponse.split("")) {
                emitter.send(SseEmitter.event().data(part));
            }
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 构建消息列表（System Prompt + 记忆 + 历史）
     */
    private List<Message> buildMessages(Long userId, ChatSession session, String userInput) {
        List<Message> messages = new ArrayList<>();

        // 1. System Prompt（人格 + 用户画像 + 记忆）
        String systemPrompt = promptService.buildSystemPrompt(userId, session.getPersonality());
        messages.add(new SystemMessage(systemPrompt));

        // 2. 最近 10 条历史消息
        List<ChatMessage> history = chatMessageMapper.findRecentMessages(session.getId(), 10);
        for (ChatMessage msg : history) {
            if (RoleEnum.USER.getCode().equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (RoleEnum.ASSISTANT.getCode().equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        // 3. 当前用户输入
        messages.add(new UserMessage(userInput));

        return messages;
    }

    /**
     * 获取或创建会话
     */
    private ChatSession getOrCreateSession(Long userId, Long sessionId, String personality) {
        if (sessionId != null) {
            ChatSession session = chatSessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>()
                            .eq(ChatSession::getId, sessionId)
                            .eq(ChatSession::getUserId, userId)
                            .eq(ChatSession::getDeleted, 0)
            );
            if (session == null) {
                throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
            }
            return session;
        }

        // 创建新会话
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle("新对话");
        session.setPersonality(personality != null ? personality : PersonalityEnum.GENTLE_SISTER.getCode());
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 更新会话标题（首次对话时）
     */
    private void updateSessionTitle(ChatSession session, String firstMessage) {
        if ("新对话".equals(session.getTitle()) && firstMessage.length() > 0) {
            String title = firstMessage.length() > 20
                    ? firstMessage.substring(0, 20) + "..."
                    : firstMessage;
            session.setTitle(title);
            chatSessionMapper.updateById(session);
        }
    }

    /**
     * 保存消息
     */
    private ChatMessage saveMessage(Long sessionId, Long userId, String role,
                                     String content, String emotion, String riskLevel) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setEmotion(emotion);
        message.setRiskLevel(riskLevel);
        chatMessageMapper.insert(message);
        return message;
    }

    /**
     * 检查每日免费消息限制
     */
    private void checkDailyLimit(Long userId, User user) {
        if (user.isVip()) {
            return;
        }
        Long todayCount = chatMessageMapper.countTodayMessages(userId);
        if (todayCount >= freeDailyMessages) {
            throw new BusinessException(ResultCode.DAILY_LIMIT_EXCEEDED);
        }
    }

    /**
     * 获取会话列表
     */
    public IPage<ChatSessionDTO> getSessionList(Long userId, Integer page, Integer size) {
        Page<ChatSession> pageParam = new Page<>(page, size);
        IPage<ChatSession> sessionPage = chatSessionMapper.pageByUserId(pageParam, userId);

        return sessionPage.convert(session -> ChatSessionDTO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .personality(session.getPersonality())
                .createdTime(session.getCreatedTime())
                .updatedTime(session.getUpdatedTime())
                .build());
    }

    /**
     * 获取会话消息列表
     */
    public List<ChatMessageDTO> getMessageList(Long userId, Long sessionId) {
        // 校验会话归属
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getDeleted, 0)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }

        List<ChatMessage> messages = chatMessageMapper.findRecentMessages(sessionId, 50);
        return messages.stream()
                .map(msg -> ChatMessageDTO.builder()
                        .id(msg.getId())
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .emotion(msg.getEmotion())
                        .riskLevel(msg.getRiskLevel())
                        .createdTime(msg.getCreatedTime())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 删除会话
     */
    public void deleteSession(Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getDeleted, 0)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        session.setDeleted(1);
        chatSessionMapper.updateById(session);
    }
}

