package com.mindecho.module.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.mapper.MemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 长期记忆服务（Summary 方案）
 * <p>
 * 设计思路：
 * 1. 每次对话后，将用户发言追加到当前会话的临时缓冲（event 类型记忆，最多 MAX_BUFFER_TURNS 条）
 * 2. 当缓冲满时，调用 AI 将现有摘要 + 缓冲内容压缩成新的摘要（summary 类型记忆，每用户仅保留一条）
 * 3. 同时提取用户画像关键词（profile 类型记忆）
 * 4. PromptService 读取 summary + profile 注入 System Prompt，实现跨会话长期记忆
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryMapper memoryMapper;
    private final ChatClient chatClient;

    /** 触发摘要压缩的对话轮数阈值 */
    @Value("${mindecho.memory.summary-threshold:6}")
    private Integer summaryThreshold;

    // ─── Prompt 模板 ─────────────────────────────────────────────────────────

    private static final String SUMMARIZE_PROMPT = """
            你是一个专注于情绪健康的 AI 助手。请将以下对话摘要和最新对话整合成一段简洁的长期记忆摘要（200字以内）。
            摘要应该保留用户的关键情绪状态、重要经历和个人特征，去掉重复和不重要的内容。

            【现有摘要】
            %s

            【最新对话片段】
            %s

            请直接输出新的摘要内容，不要有任何前缀：
            """;

    private static final String EXTRACT_PROFILE_PROMPT = """
            从以下用户发言中提取值得长期记忆的用户画像信息（职业、性格、爱好、家庭状况等），
            用一句话描述，若没有明显信息则返回"无"。

            用户说："%s"

            只返回一句描述，不要任何前缀：
            """;

    // ─── 对外接口 ────────────────────────────────────────────────────────────

    /**
     * 异步处理对话记忆（在每轮对话结束后调用）
     */
    @Async
    public void extractAndSaveMemoryAsync(Long userId, String userMessage, String aiResponse) {
        try {
            processMemory(userId, userMessage, aiResponse);
        } catch (Exception e) {
            log.error("Async memory processing failed: userId={}", userId, e);
        }
    }

    /**
     * 获取用户的相关记忆（供 PromptService 注入 System Prompt）
     */
    public List<Memory> recallRelevantMemories(Long userId, String query) {
        return memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getDeleted, 0)
                        .orderByDesc(Memory::getImportanceScore)
        );
    }

    /**
     * 手动保存记忆条目
     */
    public Memory saveMemory(Long userId, String type, String content, Integer importanceScore) {
        // 简单查重：同类型同内容不重复存
        List<Memory> existing = memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getMemoryType, type)
                        .eq(Memory::getDeleted, 0)
                        .orderByDesc(Memory::getImportanceScore)
        );
        for (Memory mem : existing) {
            if (mem.getContent().equals(content)) {
                return mem;
            }
        }

        Memory memory = new Memory();
        memory.setUserId(userId);
        memory.setMemoryType(type);
        memory.setContent(content);
        memory.setImportanceScore(importanceScore);
        memoryMapper.insert(memory);
        log.debug("Memory saved: userId={}, type={}", userId, type);
        return memory;
    }

    // ─── 核心逻辑 ─────────────────────────────────────────────────────────────

    /**
     * 处理记忆：
     * 1. 提取并保存用户画像（关键词匹配）
     * 2. 将本轮对话追加到 event 缓冲
     * 3. 若缓冲达到阈值，触发 AI 摘要压缩
     */
    private void processMemory(Long userId, String userMessage, String aiResponse) {
        // 1. 规则提取用户画像
        if (containsProfileInfo(userMessage)) {
            extractAndSaveProfile(userId, userMessage);
        }

        // 2. 追加本轮对话到 event 缓冲
        String eventContent = "用户：" + truncate(userMessage, 150) + "\nAI：" + truncate(aiResponse, 150);
        saveMemory(userId, "event", eventContent, 5);

        // 3. 判断是否需要触发摘要压缩
        // 按创建时间正序排列，确保摘要内容的时间顺序正确
        List<Memory> events = memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getMemoryType, "event")
                        .eq(Memory::getDeleted, 0)
                        .orderByAsc(Memory::getCreatedTime)
        );
        if (events.size() >= summaryThreshold) {
            triggerSummaryCompression(userId, events);
        }
    }

    /**
     * 触发 AI 摘要压缩：
     * - 读取当前 summary（若有）
     * - 将 event 缓冲拼接后请求 AI 生成新摘要
     * - 更新 summary，清空 event 缓冲
     */
    private void triggerSummaryCompression(Long userId, List<Memory> events) {
        try {
            // 获取已有摘要
            List<Memory> summaries = memoryMapper.selectList(
                    new LambdaQueryWrapper<Memory>()
                            .eq(Memory::getUserId, userId)
                            .eq(Memory::getMemoryType, "summary")
                            .eq(Memory::getDeleted, 0)
            );
            String existingSummary = summaries.isEmpty() ? "（暂无历史摘要）" : summaries.get(0).getContent();

            // 拼接最新 event 片段
            StringBuilder eventBuffer = new StringBuilder();
            events.forEach(e -> eventBuffer.append(e.getContent()).append("\n---\n"));

            // 调用 AI 生成新摘要
            String prompt = String.format(SUMMARIZE_PROMPT, existingSummary, eventBuffer);
            String newSummary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (newSummary == null || newSummary.isBlank()) {
                log.warn("AI returned empty summary for userId={}", userId);
                return;
            }

            // 更新摘要：删旧存新
            if (!summaries.isEmpty()) {
                summaries.forEach(s -> {
                    s.setDeleted(1);
                    memoryMapper.updateById(s);
                });
            }
            Memory summaryMemory = new Memory();
            summaryMemory.setUserId(userId);
            summaryMemory.setMemoryType("summary");
            summaryMemory.setContent(newSummary.trim());
            summaryMemory.setImportanceScore(10);
            memoryMapper.insert(summaryMemory);

            // 清空 event 缓冲（软删除）
            events.forEach(e -> {
                e.setDeleted(1);
                memoryMapper.updateById(e);
            });

            log.info("Memory summary compressed for userId={}, events={}", userId, events.size());

        } catch (Exception e) {
            log.error("Summary compression failed for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * AI 提取用户画像并保存
     */
    private void extractAndSaveProfile(Long userId, String userMessage) {
        try {
            String prompt = String.format(EXTRACT_PROFILE_PROMPT, userMessage);
            String profile = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (profile != null && !profile.isBlank() && !"无".equals(profile.trim())) {
                saveMemory(userId, "profile", profile.trim(), 8);
            }
        } catch (Exception e) {
            log.warn("Profile extraction failed for userId={}: {}", userId, e.getMessage());
        }
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    private boolean containsProfileInfo(String text) {
        String[] keywords = {"我是", "我叫", "我的工作", "我的职业", "我喜欢", "我不喜欢",
                "我的性格", "我比较内向", "我比较外向", "我是程序员", "我是学生",
                "我今年", "我住在", "我有一个"};
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }
}

