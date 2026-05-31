package com.mindecho.module.prompt.service;

import com.mindecho.module.memory.service.MemoryService;
import com.mindecho.module.personality.entity.AiPersonality;
import com.mindecho.module.personality.service.PersonalityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Prompt 构建服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private final MemoryService memoryService;
    private final PersonalityService personalityService;

    private static final String STATIC_SYSTEM_PROMPT = """
            你是一位%s的 AI 情绪陪伴助手，名叫"心屿"。

            【你的使命】
            - 温柔地倾听用户的心声
            - 理解并共情用户的情绪
            - 提供情感支持，帮助用户表达感受
            - 陪伴用户度过困难时刻

            【严格禁止】
            - 提供任何医疗诊断或建议
            - 推荐任何药物
            - 鼓励或引导自残、极端行为
            - 做出超出情感支持范围的专业建议

            【回复风格】
            - 温柔、真诚、有共情
            - 简洁不冗长（通常100字以内）
            - 先共情，再引导
            - 不说教，不评判
            - 可以适当使用 emoji 增加温度感

            %s
            """;

    public String buildStaticSystemPrompt(String personalityCode) {
        AiPersonality personality = personalityService.getByCode(personalityCode);
        return String.format(STATIC_SYSTEM_PROMPT,
                personality.getName(),
                personality.getSystemPrompt());
    }

    public String buildMemorySystemPrompt(UUID userId, String userMessage) {
        return buildMemoryContext(userId, userMessage);
    }

    public String buildMemorySystemPrompt(UUID userId) {
        return buildMemoryContext(userId, null);
    }

    private String buildMemoryContext(UUID userId, String userMessage) {
        List<String> contents = memoryService.recallRelevantMemoryContents(userId, userMessage);
        if (contents == null || contents.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\n【关于这位用户，你了解的信息】\n");
        contents.forEach(c -> sb.append("- ").append(c).append("\n"));

        log.debug("Memory context built: userId={}, entries={}", userId, contents.size());
        return sb.toString();
    }
}

