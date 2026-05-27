package com.mindecho.module.emotion.service;

import com.mindecho.common.enums.EmotionEnum;
import com.mindecho.module.emotion.dto.EmotionAnalyzeRequest;
import com.mindecho.module.emotion.dto.EmotionAnalyzeResponse;
import com.mindecho.module.emotion.entity.EmotionRecord;
import com.mindecho.module.emotion.mapper.EmotionRecordMapper;
import com.mindecho.module.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 情绪分析服务
 * 使用 AI 分析用户消息的情绪类型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionService {

    private final ChatClient chatClient;
    private final EmotionRecordMapper emotionRecordMapper;
    private final RiskService riskService;

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
    public EmotionAnalyzeResponse analyze(Long userId, EmotionAnalyzeRequest request) {
        String emotionCode = analyzeEmotion(request.getText());
        String riskLevel = riskService.detectRisk(request.getText()).getCode();

        // 保存情绪记录
        EmotionRecord record = new EmotionRecord();
        record.setUserId(userId);
        record.setEmotion(emotionCode);
        record.setRiskLevel(riskLevel);
        record.setSourceText(request.getText().length() > 200
                ? request.getText().substring(0, 200)
                : request.getText());
        emotionRecordMapper.insert(record);

        return EmotionAnalyzeResponse.builder()
                .emotion(emotionCode)
                .riskLevel(riskLevel)
                .build();
    }

    /**
     * 轻量情绪分析（内部调用，返回情绪代码）
     */
    public String analyzeEmotion(String text) {
        try {
            String prompt = String.format(EMOTION_ANALYZE_PROMPT, text);
            String response = chatClient.prompt(prompt)
                    .call()
                    .content();

            String emotionCode = response != null ? response.trim().toLowerCase() : "neutral";

            // 验证是否是合法的情绪代码
            EmotionEnum emotion = EmotionEnum.fromCode(emotionCode);
            return emotion.getCode();
        } catch (Exception e) {
            log.error("Emotion analysis failed", e);
            return EmotionEnum.NEUTRAL.getCode();
        }
    }
}

