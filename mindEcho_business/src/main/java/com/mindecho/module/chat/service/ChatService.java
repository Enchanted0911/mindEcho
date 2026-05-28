package com.mindecho.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.common.enums.RiskLevelEnum;
import com.mindecho.common.enums.RoleEnum;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.auth.entity.User;
import com.mindecho.module.auth.mapper.UserMapper;
import com.mindecho.module.chat.dto.ChatMessageDTO;
import com.mindecho.module.chat.dto.ChatSessionDTO;
import com.mindecho.module.chat.dto.EditMessageRequest;
import com.mindecho.module.chat.dto.SendMessageRequest;
import com.mindecho.module.chat.entity.ChatMessage;
import com.mindecho.module.chat.entity.ChatSession;
import com.mindecho.module.chat.mapper.ChatMessageMapper;
import com.mindecho.module.chat.mapper.ChatSessionMapper;
import com.mindecho.module.emotion.service.EmotionService;
import com.mindecho.module.memory.service.MemoryService;
import com.mindecho.module.personality.service.PersonalityService;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final PersonalityService personalityService;

    @Value("${mindecho.free-daily-messages:10}")
    private Integer freeDailyMessages;

    /**
     * 用户当前活跃的 SSE 连接（userId -> emitter）
     * 同一用户同时只能有一条活跃流，新请求进来时会覆盖旧的
     */
    private final ConcurrentHashMap<Long, SseEmitter> activeSseMap = new ConcurrentHashMap<>();

    /**
     * 停止标志位（userId -> stopped）
     * 当用户触发停止时设置为 true，流式回调中检测到后立即终止
     */
    private final ConcurrentHashMap<Long, AtomicBoolean> stopFlagMap = new ConcurrentHashMap<>();

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
        ChatSession session = getOrCreateSession(userId, request.getSessionId(), user.getAiPersonality());

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

        // 注册停止标志，重置为 false（新请求覆盖旧标志）
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        stopFlagMap.put(userId, stopFlag);
        activeSseMap.put(userId, emitter);

        // 连接断开时清理映射
        final Long finalUserId = userId;
        emitter.onCompletion(() -> {
            activeSseMap.remove(finalUserId, emitter);
            stopFlagMap.remove(finalUserId, stopFlag);
        });
        emitter.onError(e -> {
            activeSseMap.remove(finalUserId, emitter);
            stopFlagMap.remove(finalUserId, stopFlag);
        });

        try {
            chatClient.prompt()
                    .messages(messages)
                    .stream()
                    .chatResponse()
                    .subscribe(
                            chunk -> {
                                // 检测到停止标志则直接返回，不再发送数据
                                if (stopFlag.get()) {
                                    return;
                                }
                                String text = chunk.getResult().getOutput().getText();
                                if (text != null && !text.isEmpty()) {
                                    fullResponse.append(text);
                                    try {
                                        emitter.send(SseEmitter.event().data(text, MediaType.TEXT_PLAIN));
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
                                // 保存 AI 回复（无论是否被截断，都保存已生成的内容）
                                if (!fullResponse.isEmpty()) {
                                    saveMessage(session.getId(), userId, RoleEnum.ASSISTANT.getCode(),
                                            fullResponse.toString(), null, RiskLevelEnum.LOW.getCode());
                                }

                                // 更新会话标题（第一条消息）
                                updateSessionTitle(session, request.getMessage());

                                // 异步提取记忆（仅在正常完成时，非停止截断时）
                                if (!stopFlag.get()) {
                                    memoryService.extractAndSaveMemoryAsync(userId, request.getMessage(), fullResponse.toString());
                                }

                                try {
                                    emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                                    emitter.complete();
                                } catch (IOException e) {
                                    log.error("SSE done event send error", e);
                                    emitter.completeWithError(e);
                                }
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
     * 高风险消息同样需要获取或创建会话，确保消息被正确归档
     */
    private SseEmitter handleHighRiskMessage(Long userId, SendMessageRequest request) {
        // 保存高风险消息记录：获取或创建会话后保存
        try {
            User user = userMapper.selectById(userId);
            String personality = user != null ? user.getAiPersonality() : personalityService.getDefaultCode();
            ChatSession session = getOrCreateSession(userId, request.getSessionId(), personality);
            saveMessage(session.getId(), userId, RoleEnum.USER.getCode(),
                    request.getMessage(), null, RiskLevelEnum.HIGH.getCode());
        } catch (Exception e) {
            log.warn("Failed to save high-risk message record: userId={}", userId, e);
        }

        String safetyResponse = riskService.getSafetyResponse();
        SseEmitter emitter = new SseEmitter(5000L);

        try {
            // 逐字符流式输出安全话术
            for (String part : safetyResponse.split("")) {
                emitter.send(SseEmitter.event().data(part, MediaType.TEXT_PLAIN));
            }
            emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 构建消息列表（System Prompt + 记忆 + 历史）
     *
     * <p>Prompt Cache 优化策略（DeepSeek prefix cache）：
     * <ul>
     *   <li>第一条 SystemMessage：静态人格 Prompt，内容固定，DeepSeek 可稳定缓存前缀</li>
     *   <li>第二条 SystemMessage（可选）：动态记忆上下文，每次可能不同，放在静态后面</li>
     *   <li>历史消息 + 当前输入：追加在最后</li>
     * </ul>
     * DeepSeek 会自动对 ≥64 tokens 的公共前缀进行缓存，命中时 prompt token 费用降低 90%。
     */
    private List<Message> buildMessages(Long userId, ChatSession session, String userInput) {
        List<Message> messages = new ArrayList<>();

        // 1. 静态 System Prompt（人格设定，内容稳定，便于 prefix cache 命中）
        String staticPrompt = promptService.buildStaticSystemPrompt(session.getAiPersonality());
        messages.add(new SystemMessage(staticPrompt));

        // 2. 动态记忆上下文（跨会话摘要 + 用户画像，每次对话可能变化）
        //    单独作为第二条 SystemMessage，不污染静态前缀
        String memoryPrompt = promptService.buildMemorySystemPrompt(userId);
        if (memoryPrompt != null && !memoryPrompt.isBlank()) {
            messages.add(new SystemMessage(memoryPrompt));
        }

        // 3. 最近 10 条历史消息
        List<ChatMessage> history = chatMessageMapper.findRecentMessages(session.getId(), 10);
        for (ChatMessage msg : history) {
            if (RoleEnum.USER.getCode().equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (RoleEnum.ASSISTANT.getCode().equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        // 4. 当前用户输入
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
        session.setAiPersonality(personality != null ? personality : personalityService.getDefaultCode());
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 更新会话标题（首次对话时）
     */
    private void updateSessionTitle(ChatSession session, String firstMessage) {
        if ("新对话".equals(session.getTitle()) && !firstMessage.isEmpty()) {
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
                .aiPersonality(session.getAiPersonality())
                .createdTime(session.getCreatedTime())
                .updatedTime(session.getUpdatedTime())
                .build());
    }

    /**
     * 分页获取会话消息列表（按时间倒序，前端向上滚动时懒加载更早的消息）
     *
     * <p>第 1 页返回最新 N 条消息；前端向上滚动到顶时请求第 2、3... 页拉取更早消息。
     * 返回结果仍按时间倒序排列，前端接收后需反转为正序展示。
     */
    public IPage<ChatMessageDTO> getMessageList(Long userId, Long sessionId, Integer page, Integer size) {
        // 校验会话归属，防止越权查询
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getDeleted, 0)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }

        Page<ChatMessage> pageParam = new Page<>(page, size);
        IPage<ChatMessage> msgPage = chatMessageMapper.pageBySessionId(pageParam, sessionId);

        return msgPage.convert(msg -> ChatMessageDTO.builder()
                .id(msg.getId())
                .role(msg.getRole())
                .content(msg.getContent())
                .emotion(msg.getEmotion())
                .riskLevel(msg.getRiskLevel())
                .createdTime(msg.getCreatedTime())
                .build());
    }

    /**
     * 停止当前用户的流式输出
     * 设置停止标志后，流式回调会在下一个 chunk 检测时中断；同时直接 complete 当前 emitter
     */
    public void stopStreaming(Long userId) {
        AtomicBoolean stopFlag = stopFlagMap.get(userId);
        if (stopFlag != null) {
            stopFlag.set(true);
        }
        SseEmitter emitter = activeSseMap.get(userId);
        if (emitter != null) {
            try {
                // 发送 done 事件让前端知道流已结束，再完成 emitter
                emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
            } catch (Exception e) {
                log.warn("Stop streaming: send done event failed, userId={}", userId, e);
            }
            emitter.complete();
        }
    }

    /**
     * 编辑用户消息并重新发送
     * 流程：校验消息归属 -> 删除该消息本身及之后的所有消息 -> 以新内容重新发送
     *
     * <p>注意：不再单独更新消息内容，而是将被编辑的消息连同后续消息一并删除，
     * 再调用 sendMessage 重新写入，避免产生重复消息记录。
     */
    public SseEmitter editMessage(Long userId, EditMessageRequest request) {
        // 1. 校验消息归属（必须是当前用户的、指定会话中的、未删除的 user 角色消息）
        ChatMessage message = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getId, request.getMessageId())
                        .eq(ChatMessage::getUserId, userId)
                        .eq(ChatMessage::getSessionId, request.getSessionId())
                        .eq(ChatMessage::getRole, RoleEnum.USER.getCode())
                        .eq(ChatMessage::getDeleted, 0)
        );
        if (message == null) {
            throw new BusinessException(ResultCode.MESSAGE_NOT_FOUND);
        }

        // 2. 删除该消息本身及之后（created_time >= 该消息时间）的所有消息
        //    包含：被编辑的用户消息 + 其对应的 AI 回复 + 后续所有对话
        //    由 sendMessage 统一写入新的用户消息，避免重复记录
        chatMessageMapper.deleteFromTime(request.getSessionId(), message.getCreatedTime());

        // 3. 以新内容重新发送（复用 sendMessage 流程，sessionId 已存在）
        SendMessageRequest sendRequest = new SendMessageRequest();
        sendRequest.setSessionId(request.getSessionId());
        sendRequest.setMessage(request.getMessage());
        return sendMessage(userId, sendRequest);
    }

    /**
     * 删除会话（逻辑删除），同时级联逻辑删除该会话下的所有消息
     * 使用 deleteById 触发 MyBatis Plus @TableLogic 机制，自动将 deleted 置为 1
     * 注意：不可用 setDeleted(1) + updateById，@TableLogic 字段在 updateById 中会被忽略
     */
    public void deleteSession(Long userId, Long sessionId) {
        // 先校验归属，防止越权删除
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        // deleteById 会执行 UPDATE chat_session SET deleted=1 WHERE id=?
        chatSessionMapper.deleteById(sessionId);
        // 级联逻辑删除该会话下的所有消息
        chatMessageMapper.deleteBySessionId(sessionId);
    }
}

