package com.mindecho.module.prompt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.mapper.MemoryMapper;
import com.mindecho.module.personality.entity.AiPersonality;
import com.mindecho.module.personality.service.PersonalityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Prompt 构建服务
 * 负责组装 System Prompt（人格 + 用户画像 + 长期记忆摘要）
 * 人格信息从数据库动态加载，支持运营动态配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private final MemoryMapper memoryMapper;
    private final PersonalityService personalityService;

    /**
     * 静态基础 Prompt 模板（按人格变化，但同一用户/人格不变）
     * 放在第一条 SystemMessage，最大化 DeepSeek prefix cache 命中率
     */
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

    /**
     * 构建静态 System Prompt（人格设定，内容稳定，便于 DeepSeek prefix cache 缓存）
     * 应作为第一条 SystemMessage 发送，内容在同一人格下不会变化
     * 人格信息从数据库动态加载
     */
    public String buildStaticSystemPrompt(String personalityCode) {
        AiPersonality personality = personalityService.getByCode(personalityCode);
        return String.format(STATIC_SYSTEM_PROMPT,
                personality.getName(),
                personality.getSystemPrompt());
    }

    /**
     * 构建动态记忆上下文（每次对话可能变化）
     * 应作为第二条 SystemMessage 发送，放在静态 Prompt 之后
     * 若无记忆则返回 null，调用方可跳过不添加此条消息
     */
    public String buildMemorySystemPrompt(Long userId) {
        return buildMemoryContext(userId);
    }

    /**
     * 构建记忆上下文：
     * 优先展示 AI 生成的跨会话摘要（summary），其次展示用户画像（profile）
     */
    private String buildMemoryContext(Long userId) {
        List<Memory> memories = memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getDeleted, 0)
                        .orderByDesc(Memory::getImportanceScore)
        );
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\n【关于这位用户，你了解的信息】\n");
        boolean hasContent = false;

        // 1. 跨会话摘要（最重要，展示最新一条）
        boolean hasSummary = memories.stream()
                .filter(m -> "summary".equals(m.getMemoryType()))
                .findFirst()
                .map(m -> {
                    sb.append("历史对话摘要：").append(m.getContent()).append("\n");
                    return true;
                })
                .orElse(false);
        if (hasSummary) {
            hasContent = true;
        }

        // 2. 用户画像（去重展示，最多 5 条）
        List<Memory> profiles = memories.stream()
                .filter(m -> "profile".equals(m.getMemoryType()))
                .limit(5)
                .collect(Collectors.toList());
        if (!profiles.isEmpty()) {
            sb.append("\n用户画像：\n");
            profiles.forEach(m -> sb.append("- ").append(m.getContent()).append("\n"));
            hasContent = true;
        }

        if (!hasContent) {
            return "";
        }

        return sb.toString();
    }
}

