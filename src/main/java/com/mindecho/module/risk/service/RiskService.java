package com.mindecho.module.risk.service;

import com.mindecho.common.enums.RiskLevelEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 风险控制服务
 * 负责检测用户消息中的高风险内容（自杀、自残等）
 */
@Slf4j
@Service
public class RiskService {

    @Value("${mindecho.risk.high-risk-keywords}")
    private List<String> highRiskKeywords;

    @Value("${mindecho.risk.medium-risk-keywords}")
    private List<String> mediumRiskKeywords;

    @Value("${mindecho.safety-hotline}")
    private String safetyHotline;

    /**
     * 检测消息风险等级
     */
    public RiskLevelEnum detectRisk(String text) {
        if (text == null || text.isEmpty()) {
            return RiskLevelEnum.LOW;
        }

        // 检测高风险关键词
        for (String keyword : highRiskKeywords) {
            if (text.contains(keyword)) {
                log.warn("HIGH RISK detected: keyword={}", keyword);
                return RiskLevelEnum.HIGH;
            }
        }

        // 检测中风险关键词
        for (String keyword : mediumRiskKeywords) {
            if (text.contains(keyword)) {
                log.info("MEDIUM RISK detected: keyword={}", keyword);
                return RiskLevelEnum.MEDIUM;
            }
        }

        return RiskLevelEnum.LOW;
    }

    /**
     * 获取高风险安全话术
     */
    public String getSafetyResponse() {
        return """
                我注意到你现在可能正在经历非常痛苦的时刻。💙

                我想让你知道：你的感受是真实的，你值得被认真倾听和关怀。

                虽然我无法提供专业的医疗帮助，但有很多人愿意支持你。

                请考虑联系：
                • 你信任的家人或朋友
                • 专业心理热线：%s

                你不需要独自承受这一切。在你准备好的时候，我们可以慢慢聊聊。🌙
                """.formatted(safetyHotline);
    }

    /**
     * 针对中等风险的温柔引导话术补充
     */
    public String getMediumRiskGuidance() {
        return "我感受到你现在情绪有些低落。能告诉我是什么让你有这种感觉吗？我在这里陪着你。";
    }
}

