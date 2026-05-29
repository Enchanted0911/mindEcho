package com.mindecho.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.common.enums.RiskLevelEnum;
import com.mindecho.common.enums.RoleEnum;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.auth.entity.User;
import com.mindecho.module.auth.mapper.UserMapper;
import com.mindecho.module.billing.entity.AiUsageRecord;
import com.mindecho.module.billing.service.BillingService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天服务（核心模块）
 *
 * <p>集成了积分计费系统：
 * <ul>
 *   <li>发送消息前：预估上下文 token，执行预扣积分</li>
 *   <li>Streaming 完成后：获取真实 token 用量，执行最终结算</li>
 *   <li>AI 异常时：全额退回预扣积分</li>
 * </ul>
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
    private final BillingService billingService;

    @Value("${mindecho.free-daily-messages:10}")
    private Integer freeDailyMessages;

    /** 预估输出 token 数（用于预扣计算，max-tokens 设置的值） */
    @Value("${spring.ai.openai.chat.options.max-tokens:2048}")
    private int maxTokens;

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
     *
     * <p>计费流程：
     * 1. 计算上下文 token 估算 → 预扣积分
     * 2. 调用 AI → 流式返回
     * 3. 完成后根据真实 usage 最终结算
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

        // 6. 情绪分析（完全异步，不阻塞 SSE 响应）
        final Long msgId = userMsg.getId();
        final String msgContent = request.getMessage();
        emotionService.analyzeEmotionAsync(msgId, msgContent);

        // 7. 构建 Prompt 消息列表
        List<Message> messages = buildMessages(userId, session, request.getMessage());

        // 8. 计算上下文 token 估算（用于预扣）
        int estimatedContextTokens = estimateContextTokens(messages);

        // 9. 预扣积分（计费启用时）
        AiUsageRecord usageRecord = null;
        try {
            usageRecord = billingService.initUsageAndPreDeduct(userId, session.getId(),
                    "CHAT", estimatedContextTokens);
        } catch (BusinessException e) {
            if (ResultCode.INSUFFICIENT_POINTS.getCode().equals(e.getCode())) {
                throw e;
            }
            // 其他计费异常不阻断聊天，记录日志继续
            log.error("Billing pre-deduct failed, continue chat: userId={}", userId, e);
        }

        // 10. SSE 流式输出
        SseEmitter emitter = new SseEmitter(60000L);
        StringBuilder fullResponse = new StringBuilder();

        // token 统计（从 ChatResponse metadata 中累积）
        final AtomicInteger totalPromptTokens = new AtomicInteger(0);
        final AtomicInteger totalCompletionTokens = new AtomicInteger(0);
        final AtomicReference<AiUsageRecord> usageRef = new AtomicReference<>(usageRecord);

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

        final AiUsageRecord finalUsageRecord = usageRecord;

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

                                // 提取文本内容
                                if (chunk.getResult() != null && chunk.getResult().getOutput() != null) {
                                    String text = chunk.getResult().getOutput().getText();
                                    if (text != null && !text.isEmpty()) {
                                        fullResponse.append(text);
                                        try {
                                            emitter.send(SseEmitter.event().data(text, MediaType.TEXT_PLAIN));
                                        } catch (IllegalStateException e) {
                                            // emitter 已被 stopStreaming() 关闭，停止发送
                                            log.debug("SSE send: emitter already completed, stop sending");
                                            return;
                                        } catch (IOException e) {
                                            log.error("SSE send error", e);
                                            try {
                                                emitter.completeWithError(e);
                                            } catch (IllegalStateException ignored) {
                                                // ignore
                                            }
                                        }
                                    }
                                }

                                // 从每个 chunk 的 metadata 累积 token 使用量
                                // DeepSeek/OpenAI 在最后一个 chunk 才会返回完整 usage
                                try {
                                    if (chunk.getMetadata() != null && chunk.getMetadata().getUsage() != null) {
                                        var usage = chunk.getMetadata().getUsage();
                                        if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
                                            totalPromptTokens.set(usage.getPromptTokens().intValue());
                                        }
                                        if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0) {
                                            totalCompletionTokens.set(usage.getCompletionTokens().intValue());
                                        }
                                    }
                                } catch (Exception e) {
                                    // token 统计失败不影响业务
                                    log.debug("Token usage extraction failed: {}", e.getMessage());
                                }
                            },
                            error -> {
                                log.error("AI chat error", error);
                                // AI 异常：退回积分
                                if (finalUsageRecord != null) {
                                    billingService.failAndRefund(finalUsageRecord.getId(), finalUserId);
                                }
                                try {
                                    emitter.completeWithError(error);
                                } catch (IllegalStateException e) {
                                    log.debug("SSE error callback: emitter already completed");
                                }
                            },
                            () -> {
                                // 保存 AI 回复
                                if (!fullResponse.isEmpty()) {
                                    saveMessage(session.getId(), userId, RoleEnum.ASSISTANT.getCode(),
                                            fullResponse.toString(), null, RiskLevelEnum.LOW.getCode());
                                }

                                // 更新会话标题（第一条消息）
                                updateSessionTitle(session, request.getMessage());

                                // 异步提取记忆
                                if (!stopFlag.get()) {
                                    memoryService.extractAndSaveMemoryAsync(userId, request.getMessage(), fullResponse.toString());
                                }

                                // 积分结算（异步）
                                if (finalUsageRecord != null) {
                                    int promptTk = totalPromptTokens.get();
                                    int completionTk = totalCompletionTokens.get();

                                    if (stopFlag.get()) {
                                        // 用户主动中断：按已产生内容计费
                                        // completion token 估算（使用已生成内容字符数 / 4 粗估）
                                        int estimatedCompletion = fullResponse.length() / 4;
                                        billingService.handleStreamingInterrupt(
                                                finalUsageRecord.getId(), finalUserId,
                                                promptTk > 0 ? promptTk : estimatedContextTokens,
                                                completionTk > 0 ? completionTk : estimatedCompletion);
                                    } else {
                                        // 正常完成：结算
                                        if (promptTk == 0) {
                                            // 没有获取到真实 token，使用估算值
                                            promptTk = estimatedContextTokens;
                                            completionTk = fullResponse.length() / 4;
                                            log.debug("No token usage from response, using estimated: prompt={}, completion={}",
                                                    promptTk, completionTk);
                                        }
                                        billingService.settleUsage(finalUsageRecord.getId(), finalUserId,
                                                promptTk, completionTk);
                                    }
                                }

                                try {
                                    emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                                    emitter.complete();
                                } catch (IllegalStateException e) {
                                    // emitter 已被 stopStreaming() 关闭，忽略
                                    log.debug("SSE done event: emitter already completed");
                                } catch (IOException e) {
                                    log.error("SSE done event send error", e);
                                    try {
                                        emitter.completeWithError(e);
                                    } catch (IllegalStateException ignored) {
                                        // emitter 已关闭，忽略
                                    }
                                }
                            }
                    );
        } catch (Exception e) {
            log.error("Chat error", e);
            if (finalUsageRecord != null) {
                billingService.failAndRefund(finalUsageRecord.getId(), userId);
            }
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 处理高风险消息（返回安全话术）
     * 高风险消息同样需要获取或创建会话，确保消息被正确归档
     */
    private SseEmitter handleHighRiskMessage(Long userId, SendMessageRequest request) {
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
            // 按标点或固定长度分块发送，避免逐字发送导致的性能问题
            int chunkSize = 10;
            for (int i = 0; i < safetyResponse.length(); i += chunkSize) {
                String chunk = safetyResponse.substring(i, Math.min(i + chunkSize, safetyResponse.length()));
                emitter.send(SseEmitter.event().data(chunk, MediaType.TEXT_PLAIN));
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
     */
    private List<Message> buildMessages(Long userId, ChatSession session, String userInput) {
        List<Message> messages = new ArrayList<>();

        String staticPrompt = promptService.buildStaticSystemPrompt(session.getAiPersonality());
        messages.add(new SystemMessage(staticPrompt));

        String memoryPrompt = promptService.buildMemorySystemPrompt(userId);
        if (memoryPrompt != null && !memoryPrompt.isBlank()) {
            messages.add(new SystemMessage(memoryPrompt));
        }

        // 查询会话最近 10 条消息（按时间倒序取，返回正序）
        List<ChatMessage> history = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .eq(ChatMessage::getDeleted, 0)
                        .orderByDesc(ChatMessage::getCreatedTime)
                        .last("LIMIT 10")
        );
        // 倒序查出来需要反转以得到时间正序
        List<ChatMessage> orderedHistory = new ArrayList<>(history);
        java.util.Collections.reverse(orderedHistory);

        for (ChatMessage msg : orderedHistory) {
            if (RoleEnum.USER.getCode().equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (RoleEnum.ASSISTANT.getCode().equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        messages.add(new UserMessage(userInput));

        return messages;
    }

    /**
     * 预估消息列表的上下文 token 数（粗算：字符数 / 4）
     * 实际计费时会以模型返回的真实 promptTokens 为准
     */
    private int estimateContextTokens(List<Message> messages) {
        int totalChars = 0;
        for (Message msg : messages) {
            if (msg.getText() != null) {
                totalChars += msg.getText().length();
            }
        }
        // 中文字符平均约 2 token，英文约 4 字符/token，取折中 3 字符/token
        return Math.max(totalChars / 3, 100);
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
        // 统计用户今日消息数（role = 'user' 且 created_time 为今天）
        Long todayCount = chatMessageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getUserId, userId)
                        .eq(ChatMessage::getRole, RoleEnum.USER.getCode())
                        .apply("DATE(created_time) = CURDATE()")
                        .eq(ChatMessage::getDeleted, 0)
        );
        if (todayCount >= freeDailyMessages) {
            throw new BusinessException(ResultCode.DAILY_LIMIT_EXCEEDED);
        }
    }

    /**
     * 获取会话列表
     */
    public IPage<ChatSessionDTO> getSessionList(Long userId, Integer page, Integer size) {
        Page<ChatSession> pageParam = new Page<>(page, size);
        IPage<ChatSession> sessionPage = chatSessionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getDeleted, 0)
                        .orderByDesc(ChatSession::getUpdatedTime)
        );

        return sessionPage.convert(session -> ChatSessionDTO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .aiPersonality(session.getAiPersonality())
                .createdTime(session.getCreatedTime())
                .updatedTime(session.getUpdatedTime())
                .build());
    }

    /**
     * 分页获取会话消息列表
     */
    public IPage<ChatMessageDTO> getMessageList(Long userId, Long sessionId, Integer page, Integer size) {
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
        IPage<ChatMessage> msgPage = chatMessageMapper.selectPage(pageParam,
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getDeleted, 0)
                        .orderByDesc(ChatMessage::getCreatedTime)
        );

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
     * 先设置 stopFlag，再尝试发送 done 并关闭 emitter。
     * 由于 subscribe 回调线程和本方法可能并发操作 emitter，所有操作均需捕获 IllegalStateException。
     */
    public void stopStreaming(Long userId) {
        AtomicBoolean stopFlag = stopFlagMap.get(userId);
        if (stopFlag != null) {
            stopFlag.set(true);
        }
        SseEmitter emitter = activeSseMap.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                emitter.complete();
            } catch (IllegalStateException e) {
                // emitter 已经被 subscribe 回调线程关闭，忽略
                log.debug("Stop streaming: emitter already completed, userId={}", userId);
            } catch (Exception e) {
                log.warn("Stop streaming: send done event failed, userId={}", userId, e);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    /**
     * 编辑用户消息并重新发送
     */
    public SseEmitter editMessage(Long userId, EditMessageRequest request) {
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

        // 逻辑删除指定消息本身及之后（创建时间 >= fromTime）的所有消息
        LocalDateTime fromTime = message.getCreatedTime();
        chatMessageMapper.update(null,
                new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, request.getSessionId())
                        .ge(ChatMessage::getCreatedTime, fromTime)
                        .eq(ChatMessage::getDeleted, 0)
                        .set(ChatMessage::getDeleted, 1)
        );

        SendMessageRequest sendRequest = new SendMessageRequest();
        sendRequest.setSessionId(request.getSessionId());
        sendRequest.setMessage(request.getMessage());
        return sendMessage(userId, sendRequest);
    }

    /**
     * 删除会话（逻辑删除）
     */
    public void deleteSession(Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        chatSessionMapper.deleteById(sessionId);
        // 逻辑删除指定会话下的所有消息（级联删除）
        chatMessageMapper.update(null,
                new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getDeleted, 0)
                        .set(ChatMessage::getDeleted, 1)
        );
    }
}

