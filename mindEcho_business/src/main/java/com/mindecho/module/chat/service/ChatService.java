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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聊天服务（核心模块）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String DEFAULT_SESSION_TITLE = "新对话";

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
    private int freeDailyMessages;

    /** 用户当前活跃的 SSE 连接（userId -> emitter） */
    private final ConcurrentHashMap<String, SseEmitter> activeSseMap = new ConcurrentHashMap<>();

    /** 停止标志位（userId -> stopped） */
    private final ConcurrentHashMap<String, AtomicBoolean> stopFlagMap = new ConcurrentHashMap<>();

    // ─── 公开接口 ─────────────────────────────────────────────────────────────

    /**
     * 发送消息（SSE 流式返回）
     */
    public SseEmitter sendMessage(String userId, SendMessageRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        checkDailyLimit(userId, user);

        RiskLevelEnum riskLevel = riskService.detectRisk(request.getMessage());
        if (riskLevel.isHigh()) {
            return handleHighRiskMessage(userId, request);
        }

        ChatSession session = getOrCreateSession(userId, request.getSessionId(), user.getAiPersonality());

        ChatMessage userMsg = saveMessage(session.getId(), userId, RoleEnum.USER.getCode(),
                request.getMessage(), null, riskLevel.getCode());
        emotionService.analyzeEmotionAndSaveMemoryAsync(userId, userMsg.getId(), request.getMessage());

        List<Message> messages = buildMessages(userId, session, request.getMessage());
        int estimatedContextTokens = estimateContextTokens(messages);

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

        return buildSseStream(userId, session, request, messages, estimatedContextTokens, usageRecord);
    }

    /**
     * 停止当前用户的流式输出
     */
    public void stopStreaming(String userId) {
        AtomicBoolean stopFlag = stopFlagMap.get(userId);
        if (stopFlag != null) {
            stopFlag.set(true);
        }
        SseEmitter emitter = activeSseMap.get(userId);
        if (emitter != null) {
            sendDoneAndComplete(emitter, userId);
        }
    }

    /**
     * 编辑用户消息并重新发送
     */
    public SseEmitter editMessage(String userId, EditMessageRequest request) {
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

    /**
     * 获取会话列表
     */
    public IPage<ChatSessionDTO> getSessionList(String userId, Integer page, Integer size) {
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

    /**
     * 分页获取会话消息列表
     */
    public IPage<ChatMessageDTO> getMessageList(String userId, String sessionId, Integer page, Integer size) {
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

    /**
     * 删除会话（逻辑删除）
     */
    public void deleteSession(String userId, String sessionId) {
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

    private SseEmitter buildSseStream(String userId, ChatSession session, SendMessageRequest request,
                                       List<Message> messages, int estimatedContextTokens,
                                       AiUsageRecord usageRecord) {
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

        try {
            chatClient.prompt()
                    .messages(messages)
                    .stream()
                    .chatResponse()
                    .subscribe(
                            chunk -> {
                                if (stopFlag.get()) return;
                                // 发送文本片段
                                if (chunk.getResult() != null && chunk.getResult().getOutput() != null) {
                                    String text = chunk.getResult().getOutput().getText();
                                    if (text != null && !text.isEmpty()) {
                                        fullResponse.append(text);
                                        trySendText(emitter, text);
                                    }
                                }
                                // 收集 token 用量
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
                                log.error("AI chat error", error);
                                if (usageRecord != null) {
                                    billingService.failAndRefund(usageRecord.getId(), userId);
                                }
                                tryCompleteWithError(emitter, error);
                            },
                            () -> {
                                if (!fullResponse.isEmpty()) {
                                    saveMessage(session.getId(), userId, RoleEnum.ASSISTANT.getCode(),
                                            fullResponse.toString(), null, RiskLevelEnum.LOW.getCode());
                                }
                                updateSessionTitle(session, request.getMessage());

                                if (!stopFlag.get()) {
                                    memoryService.extractAndSaveMemoryAsync(userId, request.getMessage(), fullResponse.toString());
                                }

                                if (usageRecord != null && billingSettledFlag.compareAndSet(false, true)) {
                                    settleOrInterruptBilling(usageRecord.getId(), userId,
                                            totalPromptTokens.get(), totalCompletionTokens.get(),
                                            estimatedContextTokens, fullResponse.length(), stopFlag.get());
                                }

                                sendDoneAndComplete(emitter, userId);
                            }
                    );
        } catch (Exception e) {
            log.error("Chat error", e);
            if (usageRecord != null) {
                billingService.failAndRefund(usageRecord.getId(), userId);
            }
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * 处理高风险消息（直接返回安全提示，不调用 AI）
     */
    private SseEmitter handleHighRiskMessage(String userId, SendMessageRequest request) {
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

    // ─── SSE 辅助方法 ─────────────────────────────────────────────────────────

    /** 安全发送文本片段，忽略 emitter 已关闭的情况 */
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

    /** 安全发送 done 事件并完成 emitter */
    private void sendDoneAndComplete(SseEmitter emitter, String userId) {
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

    /** 安全关闭 emitter（携带错误） */
    private void tryCompleteWithError(SseEmitter emitter, Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (IllegalStateException ignored) {
        }
    }

    private void removeSseEntry(String userId, SseEmitter emitter, AtomicBoolean stopFlag) {
        activeSseMap.remove(userId, emitter);
        stopFlagMap.remove(userId, stopFlag);
    }

    // ─── 计费结算 ─────────────────────────────────────────────────────────────

    private void settleOrInterruptBilling(String usageRecordId, String userId,
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

    // ─── 业务逻辑辅助 ─────────────────────────────────────────────────────────

    private List<Message> buildMessages(String userId, ChatSession session, String userInput) {
        List<Message> messages = new ArrayList<>();

        messages.add(new SystemMessage(promptService.buildStaticSystemPrompt(session.getAiPersonality())));

        String memoryPrompt = promptService.buildMemorySystemPrompt(userId, userInput);
        if (memoryPrompt != null && !memoryPrompt.isBlank()) {
            messages.add(new SystemMessage(memoryPrompt));
        }

        List<ChatMessage> history = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .eq(ChatMessage::getDeleted, 0)
                        .orderByDesc(ChatMessage::getCreatedTime)
                        .last("LIMIT 10")
        );
        List<ChatMessage> orderedHistory = new ArrayList<>(history);
        Collections.reverse(orderedHistory);

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

    private int estimateContextTokens(List<Message> messages) {
        int totalChars = messages.stream()
                .mapToInt(msg -> msg.getText() != null ? msg.getText().length() : 0)
                .sum();
        return Math.max(totalChars / 3, 100);
    }

    private ChatSession getOrCreateSession(String userId, String sessionId, String personality) {
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

    private ChatMessage saveMessage(String sessionId, String userId, String role,
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

    private void checkDailyLimit(String userId, User user) {
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

