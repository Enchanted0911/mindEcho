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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 情绪分析服务
 *
 * <p>注意：此服务直接使用 {@link ChatModel}（裸模型调用），而非 {@link org.springframework.ai.chat.client.ChatClient}，
 * 目的是避免与 SpringAiConfig 中的 ChatClient Bean 产生循环依赖：
 * ChatClient → MemoryExtractionAdvisor → EmotionService → ChatClient。
 */
@Slf4j
@Service
public class EmotionService {

    private final ChatModel chatModel;
    private final EmotionRecordMapper emotionRecordMapper;
    private final RiskService riskService;
    private final ChatMessageMapper chatMessageMapper;
    private final MemoryService memoryService;

    public EmotionService(@Qualifier("openAiChatModel") ChatModel chatModel,
                          EmotionRecordMapper emotionRecordMapper,
                          RiskService riskService,
                          ChatMessageMapper chatMessageMapper,
                          MemoryService memoryService) {
        this.chatModel = chatModel;
        this.emotionRecordMapper = emotionRecordMapper;
        this.riskService = riskService;
        this.chatMessageMapper = chatMessageMapper;
        this.memoryService = memoryService;
    }

    private static final String EMOTION_ANALYZE_PROMPT = """
            你是一位专业的心理咨询师，请客观、严格地分析以下文本所表达的主要情绪。

            【判断标准 - 必须满足以下条件才能判断为消极情绪】
            - anxiety（焦虑）：明确描述担忧、不安、紧张感，且持续强烈
            - depression（抑郁）：明确表达持续的情绪低落、空虚感、失去兴趣，不包括短暂的难过
            - anger（愤怒）：明确表达生气、愤怒情绪，语气强烈
            - loneliness（孤独）：明确表达孤单、被隔离感
            - sadness（悲伤）：有明确的触发事件导致的悲伤，不包括日常抱怨
            - fear（恐惧）：明确的害怕或恐惧情绪
            - stress（压力）：明确表达压力过大、承受不了
            - happiness（快乐）：明确积极正面的情绪
            - neutral（平静）：日常闲聊、一般性陈述、轻微情绪波动、抱怨日常琐事、轻微的烦恼

            【重要提示】
            - 日常生活中的抱怨（如"好累啊"、"今天好烦"）应判断为 neutral，而非消极情绪
            - 只有明确、强烈的情绪表达才归入对应消极类别
            - 模糊、轻微、日常的情绪默认为 neutral
            - 倾向于给出 neutral 而非过度判断消极情绪

            待分析文本："%s"

            只返回情绪英文代码（如：neutral）：
            """;

    /**
     * 分析情绪（同步，用于批量分析或接口调用）
     */
    public EmotionAnalyzeResponse analyze(UUID userId, EmotionAnalyzeRequest request) {
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
    public void analyzeEmotionAsync(UUID messageId, String text) {
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
     * @param userId    用户 ID
     * @param messageId 聊天消息 ID（若非来自聊天则传 null）
     * @param text      待分析文本
     */
    @Async
    public void analyzeEmotionAndSaveMemoryAsync(UUID userId, UUID messageId, String text) {
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
            String response = chatModel.call(
                    new Prompt(new UserMessage(prompt)))
                    .getResult().getOutput().getText();

            String emotionCode = response != null ? response.trim().toLowerCase() : "neutral";
            EmotionEnum emotion = EmotionEnum.fromCode(emotionCode);
            return emotion.getCode();
        } catch (Exception e) {
            log.error("Emotion analysis failed", e);
            return EmotionEnum.NEUTRAL.getCode();
        }
    }

    private void saveEmotionMemoryIfSignificant(UUID userId, String emotionCode, String text) {
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

