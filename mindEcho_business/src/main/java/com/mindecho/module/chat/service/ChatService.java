package com.mindecho.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.common.enums.RiskLevelEnum;
import com.mindecho.common.enums.RoleEnum;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.astrology.tool.AstrologyTools;
import com.mindecho.module.auth.entity.User;
import com.mindecho.module.auth.mapper.UserMapper;
import com.mindecho.module.billing.entity.AiUsageRecord;
import com.mindecho.module.billing.service.BillingService;
import com.mindecho.module.chat.advisor.LongTermMemoryAdvisor;
import com.mindecho.module.chat.advisor.MindEchoChatMemoryRepository;
import com.mindecho.module.chat.dto.ChatMessageDTO;
import com.mindecho.module.chat.dto.ChatSessionDTO;
import com.mindecho.module.chat.dto.EditMessageRequest;
import com.mindecho.module.chat.dto.SendMessageRequest;
import com.mindecho.module.chat.entity.ChatMessage;
import com.mindecho.module.chat.entity.ChatSession;
import com.mindecho.module.chat.mapper.ChatMessageMapper;
import com.mindecho.module.chat.mapper.ChatSessionMapper;
import com.mindecho.module.emotion.service.EmotionService;
import com.mindecho.module.personality.service.PersonalityService;
import com.mindecho.module.prompt.service.PromptService;
import com.mindecho.module.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聊天服务（核心模块）
 *
 * <p>使用 Spring AI Advisor 链实现以下功能：
 * <ul>
 *   <li>短期记忆（会话历史）：{@code MessageChatMemoryAdvisor}（在 SpringAiConfig 中配置）</li>
 *   <li>长期记忆注入：{@code LongTermMemoryAdvisor}（在 SpringAiConfig 中配置）</li>
 *   <li>记忆提取保存：{@code MemoryExtractionAdvisor}（在 SpringAiConfig 中配置）</li>
 * </ul>
 *
 * <p>每次请求时通过 {@code chatClient.prompt().advisors()} 动态传入以下上下文：
 * <ul>
 *   <li>{@code chat_memory_conversation_id}：会话 ID（格式 "session:{sessionId}"）</li>
 *   <li>{@code mindecho_userId}：用户 ID（供 LongTermMemoryAdvisor 使用）</li>
 *   <li>{@code mindecho_userMessage}：用户消息原文（供 MemoryExtractionAdvisor 使用）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String DEFAULT_SESSION_TITLE = "新对话";

    /** 星盘/占星相关关键词（用于渐进式工具披露） */
    private static final String[] ASTROLOGY_KEYWORDS = {
            "星盘", "本命盘", "合盘", "和盘", "流运", "占星", "星座",
            "上升", "月亮", "太阳星座", "天蝎", "白羊", "金牛", "双子", "巨蟹",
            "狮子", "处女", "天秤", "射手", "摩羯", "水瓶", "双鱼",
            "行星", "宫位", "相位", "土星", "木星", "火星", "金星", "水星",
            "天王", "海王", "冥王", "命盘", "运势", "星象"
    };

    private final ChatClient chatClient;
    private final UserMapper userMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RiskService riskService;
    private final EmotionService emotionService;
    private final PersonalityService personalityService;
    private final BillingService billingService;
    private final PromptService promptService;
    private final AstrologyTools astrologyTools;

    @Value("${mindecho.free-daily-messages:10}")
    private int freeDailyMessages;

    /** 用户当前活跃的 SSE 连接 */
    private final ConcurrentHashMap<UUID, SseEmitter> activeSseMap = new ConcurrentHashMap<>();

    /** 停止标志位 */
    private final ConcurrentHashMap<UUID, AtomicBoolean> stopFlagMap = new ConcurrentHashMap<>();

    // ─── 公开接口 ─────────────────────────────────────────────────────────────

    public SseEmitter sendMessage(UUID userId, SendMessageRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        checkDailyLimit(userId, user);

        // 积分余额预检
        billingService.checkBalanceBeforeRequest(userId, "CHAT");

        // 风险检测
        RiskLevelEnum riskLevel = riskService.detectRisk(request.getMessage());
        if (riskLevel.isHigh()) {
            return handleHighRiskMessage(userId, request);
        }

        ChatSession session = getOrCreateSession(userId, request.getSessionId(), user.getAiPersonality());

        // 持久化用户消息
        ChatMessage userMsg = saveMessage(session.getId(), userId, RoleEnum.USER.getCode(),
                request.getMessage(), null, riskLevel.getCode());

        // 异步情绪分析（保持原有行为）
        emotionService.analyzeEmotionAndSaveMemoryAsync(userId, userMsg.getId(), request.getMessage());

        // 估算 token 用于计费预扣
        int estimatedContextTokens = estimateContextTokens(request.getMessage());

        AiUsageRecord usageRecord = null;
        try {
            usageRecord = billingService.initUsageAndPreDeduct(userId, session.getId(),
                    "CHAT", estimatedContextTokens);
        } catch (BusinessException e) {
            if (ResultCode.INSUFFICIENT_POINTS.getCode().equals(e.getCode())) {
                throw e;
            }
            log.error("Billing pre-deduct failed, continue chat: userId={}", userId, e);
        }

        return buildSseStream(userId, session, request, estimatedContextTokens, usageRecord);
    }

    public void stopStreaming(UUID userId) {
        AtomicBoolean stopFlag = stopFlagMap.get(userId);
        if (stopFlag != null) {
            stopFlag.set(true);
        }
        SseEmitter emitter = activeSseMap.get(userId);
        if (emitter != null) {
            sendDoneAndComplete(emitter, userId);
        }
    }

    public SseEmitter editMessage(UUID userId, EditMessageRequest request) {
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

        OffsetDateTime fromTime = message.getCreatedTime();
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

    public IPage<ChatSessionDTO> getSessionList(UUID userId, Integer page, Integer size) {
        IPage<ChatSession> sessionPage = chatSessionMapper.selectPage(
                new Page<>(page, size),
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

    public IPage<ChatMessageDTO> getMessageList(UUID userId, UUID sessionId, Integer page, Integer size) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getDeleted, 0)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }

        IPage<ChatMessage> msgPage = chatMessageMapper.selectPage(
                new Page<>(page, size),
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

    public void deleteSession(UUID userId, UUID sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getDeleted, 0)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        chatSessionMapper.update(null,
                new LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getDeleted, 0)
                        .set(ChatSession::getDeleted, 1)
        );
        chatMessageMapper.update(null,
                new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getDeleted, 0)
                        .set(ChatMessage::getDeleted, 1)
        );
    }

    // ─── SSE 核心流 ───────────────────────────────────────────────────────────

    /**
     * 构建 SSE 流式响应
     *
     * <p>使用 Spring AI ChatClient Advisor 链：
     * <ol>
     *   <li>LongTermMemoryAdvisor：注入长期记忆（BEFORE_MODEL）</li>
     *   <li>MessageChatMemoryAdvisor：注入/保存会话历史（BEFORE + AFTER）</li>
     *   <li>MemoryExtractionAdvisor：异步提取记忆（AFTER_MODEL）</li>
     * </ol>
     *
     * <p>通过 {@code advisors()} 动态传入会话级上下文（conversationId、userId、userMessage）。
     */
    private SseEmitter buildSseStream(UUID userId, ChatSession session, SendMessageRequest request,
                                       int estimatedContextTokens, AiUsageRecord usageRecord) {
        SseEmitter emitter = new SseEmitter(60_000L);
        StringBuilder fullResponse = new StringBuilder();
        AtomicInteger totalPromptTokens = new AtomicInteger(0);
        AtomicInteger totalCompletionTokens = new AtomicInteger(0);
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        AtomicBoolean billingSettledFlag = new AtomicBoolean(false);

        stopFlagMap.put(userId, stopFlag);
        activeSseMap.put(userId, emitter);

        emitter.onCompletion(() -> removeSseEntry(userId, emitter, stopFlag));
        emitter.onError(e -> removeSseEntry(userId, emitter, stopFlag));

        // 构建 System Prompt（包含人格设置）
        String systemPrompt = promptService.buildStaticSystemPrompt(session.getAiPersonality());

        // conversationId = "session:{sessionId}"（MessageChatMemoryAdvisor 使用）
        String conversationId = MindEchoChatMemoryRepository.buildConversationId(session.getId());

        try {
            // 渐进式工具披露：仅当用户消息涉及星盘/占星时动态注入占星工具
            var promptSpec = chatClient.prompt()
                    .system(systemPrompt)
                    .user(request.getMessage())
                    // 通过 advisors() 传入会话级上下文（覆盖 Advisor 所需的动态参数）
                    .advisors(advisorSpec -> advisorSpec
                            // MessageChatMemoryAdvisor 需要的 conversationId
                            .param(ChatMemory.CONVERSATION_ID, conversationId)
                            // LongTermMemoryAdvisor 需要的 userId
                            .param(LongTermMemoryAdvisor.USER_ID_KEY, userId)
                            // MemoryExtractionAdvisor 需要的用户消息原文
                            .param("mindecho_userMessage", request.getMessage())
                    );

            // 渐进式工具披露：仅当检测到占星关键词时注入占星工具
            // 同时通过 toolContext 将 userId 传入工具调用链，解决 boundedElastic 线程切换导致 ThreadLocal 丢失的问题
            if (isAstrologyRelated(request.getMessage())) {
                log.debug("Astrology keywords detected, injecting astrology tools for userId={}", userId);
                promptSpec = promptSpec
                        .tools(astrologyTools)
                        .toolContext(java.util.Map.of("userId", userId));
            }

            promptSpec
                    .stream()
                    .chatResponse()
                    .subscribe(
                            chunk -> {
                                if (stopFlag.get()) return;
                                if (chunk.getResult() != null && chunk.getResult().getOutput() != null) {
                                    String text = chunk.getResult().getOutput().getText();
                                    if (text != null && !text.isEmpty()) {
                                        fullResponse.append(text);
                                        trySendText(emitter, text);
                                    }
                                }
                                try {
                                    var usage = chunk.getMetadata().getUsage();
                                    Integer pt = usage.getPromptTokens().intValue();
                                    Integer ct = usage.getCompletionTokens().intValue();
                                    if (pt > 0) totalPromptTokens.set(pt);
                                    if (ct > 0) totalCompletionTokens.set(ct);
                                } catch (Exception e) {
                                    log.debug("Token usage extraction failed: {}", e.getMessage());
                                }
                            },
                            error -> {
                                log.error("AI chat error for userId={}", userId, error);
                                if (usageRecord != null) {
                                    billingService.failAndRefund(usageRecord.getId(), userId);
                                }
                                tryCompleteWithError(emitter, error);
                            },
                            () -> {
                                // 持久化 AI 回复
                                if (!fullResponse.isEmpty()) {
                                    saveMessage(session.getId(), userId, RoleEnum.ASSISTANT.getCode(),
                                            fullResponse.toString(), null, RiskLevelEnum.LOW.getCode());
                                }
                                // 更新会话标题
                                updateSessionTitle(session, request.getMessage());

                                // 计费结算
                                if (usageRecord != null && billingSettledFlag.compareAndSet(false, true)) {
                                    settleOrInterruptBilling(usageRecord.getId(), userId,
                                            totalPromptTokens.get(), totalCompletionTokens.get(),
                                            estimatedContextTokens, fullResponse.length(), stopFlag.get());
                                }

                                sendDoneAndComplete(emitter, userId);
                            }
                    );
        } catch (Exception e) {
            log.error("Chat error for userId={}", userId, e);
            if (usageRecord != null) {
                billingService.failAndRefund(usageRecord.getId(), userId);
            }
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private SseEmitter handleHighRiskMessage(UUID userId, SendMessageRequest request) {
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
        SseEmitter emitter = new SseEmitter(5_000L);
        try {
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

    // ─── SSE 工具方法 ─────────────────────────────────────────────────────────

    private void trySendText(SseEmitter emitter, String text) {
        try {
            emitter.send(SseEmitter.event().data(text, MediaType.TEXT_PLAIN));
        } catch (IllegalStateException e) {
            log.debug("SSE send: emitter already completed, stop sending");
        } catch (IOException e) {
            log.error("SSE send error", e);
            tryCompleteWithError(emitter, e);
        }
    }

    private void sendDoneAndComplete(SseEmitter emitter, UUID userId) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
            emitter.complete();
        } catch (IllegalStateException e) {
            log.debug("SSE done event: emitter already completed, userId={}", userId);
        } catch (IOException e) {
            log.warn("SSE done event send error, userId={}", userId, e);
            tryCompleteWithError(emitter, e);
        }
    }

    private void tryCompleteWithError(SseEmitter emitter, Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (IllegalStateException ignored) {
        }
    }

    private void removeSseEntry(UUID userId, SseEmitter emitter, AtomicBoolean stopFlag) {
        activeSseMap.remove(userId, emitter);
        stopFlagMap.remove(userId, stopFlag);
    }

    // ─── 计费结算 ─────────────────────────────────────────────────────────────

    private void settleOrInterruptBilling(UUID usageRecordId, UUID userId,
                                           int promptTk, int completionTk,
                                           int estimatedContextTokens, int responseLength,
                                           boolean interrupted) {
        if (interrupted) {
            int estimatedCompletion = responseLength / 4;
            billingService.handleStreamingInterrupt(usageRecordId, userId,
                    promptTk > 0 ? promptTk : estimatedContextTokens,
                    completionTk > 0 ? completionTk : estimatedCompletion);
        } else {
            if (promptTk == 0) {
                promptTk = estimatedContextTokens;
                completionTk = responseLength / 4;
                log.debug("No token usage from response, using estimated: prompt={}, completion={}", promptTk, completionTk);
            }
            billingService.settleUsage(usageRecordId, userId, promptTk, completionTk);
        }
    }

    // ─── 业务辅助 ─────────────────────────────────────────────────────────────

    /**
     * 估算 Context Token 数量（用于计费预扣）
     *
     * <p>简化计算：仅基于用户消息长度估算，实际 token 数在响应完成时从 usage 中获取。
     */
    private int estimateContextTokens(String userMessage) {
        return Math.max((userMessage != null ? userMessage.length() : 0) / 3 + 500, 200);
    }

    /**
     * 判断消息是否与星盘/占星相关（用于渐进式工具披露）
     */
    private boolean isAstrologyRelated(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        for (String keyword : ASTROLOGY_KEYWORDS) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private ChatSession getOrCreateSession(UUID userId, UUID sessionId, String personality) {
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
        session.setTitle(DEFAULT_SESSION_TITLE);
        session.setAiPersonality(personality != null ? personality : personalityService.getDefaultCode());
        chatSessionMapper.insert(session);
        return session;
    }

    private void updateSessionTitle(ChatSession session, String firstMessage) {
        if (DEFAULT_SESSION_TITLE.equals(session.getTitle()) && !firstMessage.isEmpty()) {
            String title = firstMessage.length() > 20
                    ? firstMessage.substring(0, 20) + "..."
                    : firstMessage;
            session.setTitle(title);
            chatSessionMapper.updateById(session);
        }
    }

    private ChatMessage saveMessage(UUID sessionId, UUID userId, String role,
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

    private void checkDailyLimit(UUID userId, User user) {
        if (user.isVip()) return;
        Long todayCount = chatMessageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getUserId, userId)
                        .eq(ChatMessage::getRole, RoleEnum.USER.getCode())
                        .apply("(created_time AT TIME ZONE 'Asia/Shanghai')::date = (NOW() AT TIME ZONE 'Asia/Shanghai')::date")
                        .eq(ChatMessage::getDeleted, 0)
        );
        if (todayCount >= freeDailyMessages) {
            throw new BusinessException(ResultCode.DAILY_LIMIT_EXCEEDED);
        }
    }
}

