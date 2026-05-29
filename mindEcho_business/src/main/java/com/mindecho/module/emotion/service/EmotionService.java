package com.mindecho.module.emotion.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mindecho.common.enums.EmotionEnum;
import com.mindecho.module.chat.entity.ChatMessage;
import com.mindecho.module.chat.mapper.ChatMessageMapper;
import com.mindecho.module.emotion.dto.EmotionAnalyzeRequest;
import com.mindecho.module.emotion.dto.EmotionAnalyzeResponse;
import com.mindecho.module.emotion.entity.EmotionRecord;
import com.mindecho.module.emotion.mapper.EmotionRecordMapper;
import com.mindecho.module.memory.service.MemoryService;
import com.mindecho.module.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 情绪分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionService {

    private final ChatClient chatClient;
    private final EmotionRecordMapper emotionRecordMapper;
    private final RiskService riskService;
    private final ChatMessageMapper chatMessageMapper;
    private final MemoryService memoryService;

    private static final String EMOTION_ANALYZE_PROMPT = """
            请分析以下文本表达的主要情绪，从以下情绪类别中选择最符合的一个，只返回英文代码，不要有任何其他内容：
            anxiety（焦虑）, depression（抑郁）, anger（愤怒）, loneliness（孤独）, sadness（悲伤）,
            happiness（快乐）, fear（恐惧）, neutral（平静）, stress（压力）

            待分析文本："%s"

            只返回情绪英文代码（如：anxiety）：
            """;

    /**
     * 分析情绪（同步，用于批量分析或接口调用）
     */
    public EmotionAnalyzeResponse analyze(String userId, EmotionAnalyzeRequest request) {
        String emotionCode = analyzeEmotion(request.getText());
        String riskLevel = riskService.detectRisk(request.getText()).getCode();

        EmotionRecord record = new EmotionRecord();
        record.setUserId(userId);
        record.setEmotion(emotionCode);
        record.setRiskLevel(riskLevel);
        record.setSourceText(request.getText().length() > 200
                ? request.getText().substring(0, 200)
                : request.getText());
        emotionRecordMapper.insert(record);

        saveEmotionMemoryIfSignificant(userId, emotionCode, request.getText());

        return EmotionAnalyzeResponse.builder()
                .emotion(emotionCode)
                .riskLevel(riskLevel)
                .build();
    }

    /**
     * 异步情绪分析并更新聊天消息记录
     */
    @Async
    public void analyzeEmotionAsync(String messageId, String text) {
        try {
            String emotionCode = analyzeEmotion(text);
            chatMessageMapper.update(null,
                    new LambdaUpdateWrapper<ChatMessage>()
                            .eq(ChatMessage::getId, messageId)
                            .eq(ChatMessage::getDeleted, 0)
                            .set(ChatMessage::getEmotion, emotionCode)
            );
        } catch (Exception e) {
            log.warn("Async emotion analysis failed for messageId={}: {}", messageId, e.getMessage());
        }
    }

    /**
     * 异步情绪分析 + 向量记忆写入
     *
     * @param userId    用户 ID（UUID 字符串）
     * @param messageId 聊天消息 ID（UUID 字符串，若非来自聊天则传 null）
     * @param text      待分析文本
     */
    @Async
    public void analyzeEmotionAndSaveMemoryAsync(String userId, String messageId, String text) {
        try {
            String emotionCode = analyzeEmotion(text);

            if (messageId != null) {
                chatMessageMapper.update(null,
                        new LambdaUpdateWrapper<ChatMessage>()
                                .eq(ChatMessage::getId, messageId)
                                .eq(ChatMessage::getDeleted, 0)
                                .set(ChatMessage::getEmotion, emotionCode)
                );
            }

            saveEmotionMemoryIfSignificant(userId, emotionCode, text);

        } catch (Exception e) {
            log.warn("Async emotion analysis+memory failed for userId={}, messageId={}: {}",
                    userId, messageId, e.getMessage());
        }
    }

    /**
     * 轻量情绪分析（内部调用）
     */
    public String analyzeEmotion(String text) {
        try {
            String prompt = String.format(EMOTION_ANALYZE_PROMPT, text);
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String emotionCode = response != null ? response.trim().toLowerCase() : "neutral";
            EmotionEnum emotion = EmotionEnum.fromCode(emotionCode);
            return emotion.getCode();
        } catch (Exception e) {
            log.error("Emotion analysis failed", e);
            return EmotionEnum.NEUTRAL.getCode();
        }
    }

    private void saveEmotionMemoryIfSignificant(String userId, String emotionCode, String text) {
        if (EmotionEnum.NEUTRAL.getCode().equals(emotionCode)) {
            return;
        }
        try {
            EmotionEnum emotion = EmotionEnum.fromCode(emotionCode);
            String snippet = text.length() > 150 ? text.substring(0, 150) + "…" : text;
            String content = String.format("[%s] 用户表达：%s", emotion.getDesc(), snippet);
            int score = isNegativeEmotion(emotion) ? 7 : 5;
            memoryService.saveMemory(userId, "emotion", content, score);
            log.debug("Emotion memory saved: userId={}, emotion={}", userId, emotionCode);
        } catch (Exception e) {
            log.warn("Failed to save emotion memory: userId={}, emotion={}", userId, emotionCode, e);
        }
    }

    private boolean isNegativeEmotion(EmotionEnum emotion) {
        return switch (emotion) {
            case ANXIETY, DEPRESSION, LONELINESS, SADNESS, FEAR, STRESS, ANGER -> true;
            default -> false;
        };
    }
}

