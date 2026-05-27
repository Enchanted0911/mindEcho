package com.mindecho.module.prompt.service;

import com.mindecho.common.enums.PersonalityEnum;
import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.mapper.MemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Prompt 构建服务
 * 负责组装 System Prompt（人格 + 用户画像 + 长期记忆）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private final MemoryMapper memoryMapper;

    /** 基础 System Prompt */
    private static final String BASE_SYSTEM_PROMPT = """
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

            %s
            """;

    /**
     * 构建完整 System Prompt
     */
    public String buildSystemPrompt(Long userId, String personalityCode) {
        PersonalityEnum personality = PersonalityEnum.fromCode(personalityCode);

        // 获取用户记忆
        String memoryContext = buildMemoryContext(userId);

        return String.format(BASE_SYSTEM_PROMPT,
                personality.getName(),
                personality.getSystemPrompt(),
                memoryContext);
    }

    /**
     * 构建记忆上下文
     */
    private String buildMemoryContext(Long userId) {
        List<Memory> memories = memoryMapper.findByUserId(userId);
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        // 用户画像记忆
        List<Memory> profileMemories = memories.stream()
                .filter(m -> "profile".equals(m.getMemoryType()))
                .collect(Collectors.toList());

        // 事件记忆
        List<Memory> eventMemories = memories.stream()
                .filter(m -> "event".equals(m.getMemoryType()))
                .limit(5)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();

        if (!profileMemories.isEmpty()) {
            sb.append("【用户画像】\n");
            profileMemories.forEach(m -> sb.append("- ").append(m.getContent()).append("\n"));
        }

        if (!eventMemories.isEmpty()) {
            sb.append("\n【用户近期经历】\n");
            eventMemories.forEach(m -> sb.append("- ").append(m.getContent()).append("\n"));
        }

        if (sb.length() > 0) {
            return "\n【关于这位用户，你了解的信息】\n" + sb;
        }

        return "";
    }
}

