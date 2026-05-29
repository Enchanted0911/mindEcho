package com.mindecho.module.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.mapper.MemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 长期记忆服务（Summary + pgvector 语义检索方案）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final String TYPE_PROFILE  = "profile";
    private static final String TYPE_EMOTION  = "emotion";
    private static final String TYPE_SUMMARY  = "summary";
    private static final String TYPE_EVENT    = "event";
    private static final String NO_PROFILE    = "无";

    private static final List<String> VECTORIZED_TYPES =
            List.of(TYPE_PROFILE, TYPE_EMOTION, TYPE_SUMMARY, "relationship", "astrology_history");

    private static final String[] PROFILE_KEYWORDS = {
            "我是", "我叫", "我的工作", "我的职业", "我喜欢", "我不喜欢",
            "我的性格", "我比较内向", "我比较外向", "我是程序员", "我是学生",
            "我今年", "我住在", "我有一个"
    };

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

    private final MemoryMapper memoryMapper;
    private final ChatClient chatClient;
    private final MemoryVectorService memoryVectorService;

    @Value("${mindecho.memory.summary-threshold:6}")
    private int summaryThreshold;

    // ─── 对外接口 ────────────────────────────────────────────────────────────

    /**
     * 异步处理对话记忆
     */
    @Async
    public void extractAndSaveMemoryAsync(String userId, String userMessage, String aiResponse) {
        try {
            processMemory(userId, userMessage, aiResponse);
        } catch (Exception e) {
            log.error("Async memory processing failed: userId={}", userId, e);
        }
    }

    /**
     * 基于语义相似度召回用户的相关记忆
     */
    public List<String> recallRelevantMemoryContents(String userId, String query) {
        if (query == null || query.isBlank()) {
            return recallByImportance(userId);
        }
        List<Document> docs = memoryVectorService.searchMemoriesByTypes(
                userId, List.of(TYPE_SUMMARY, TYPE_PROFILE, TYPE_EMOTION), query, 3);
        List<String> contents = MemoryVectorService.extractContents(docs);
        log.debug("Vector recall: userId={}, query.len={}, recalled={}", userId, query.length(), contents.size());
        return contents;
    }

    /**
     * 手动保存记忆条目
     */
    public Memory saveMemory(String userId, String type, String content, int importanceScore) {
        List<Memory> existing = memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getMemoryType, type)
                        .eq(Memory::getDeleted, 0)
                        .orderByDesc(Memory::getImportanceScore)
        );
        return existing.stream()
                .filter(m -> m.getContent().equals(content))
                .findFirst()
                .orElseGet(() -> insertMemory(userId, type, content, importanceScore));
    }

    /**
     * 软删除记忆，同时从 pgvector 中删除向量
     */
    public void deleteMemory(Memory memory) {
        memory.setDeleted(1);
        memoryMapper.updateById(memory);
        if (VECTORIZED_TYPES.contains(memory.getMemoryType())) {
            memoryVectorService.deleteByMemoryId(memory.getId());
        }
    }

    // ─── 核心逻辑 ─────────────────────────────────────────────────────────────

    private void processMemory(String userId, String userMessage, String aiResponse) {
        if (containsProfileInfo(userMessage)) {
            extractAndSaveProfile(userId, userMessage);
        }

        String eventContent = "用户：" + truncate(userMessage, 150) + "\nAI：" + truncate(aiResponse, 150);
        saveMemory(userId, TYPE_EVENT, eventContent, 5);

        List<Memory> events = memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getMemoryType, TYPE_EVENT)
                        .eq(Memory::getDeleted, 0)
                        .orderByAsc(Memory::getCreatedTime)
        );
        if (events.size() >= summaryThreshold) {
            triggerSummaryCompression(userId, events);
        }
    }

    private void triggerSummaryCompression(String userId, List<Memory> events) {
        try {
            List<Memory> summaries = memoryMapper.selectList(
                    new LambdaQueryWrapper<Memory>()
                            .eq(Memory::getUserId, userId)
                            .eq(Memory::getMemoryType, TYPE_SUMMARY)
                            .eq(Memory::getDeleted, 0)
                            .orderByDesc(Memory::getCreatedTime)
            );
            String existingSummary = summaries.isEmpty() ? "（暂无历史摘要）" : summaries.getFirst().getContent();

            String eventText = events.stream()
                    .map(Memory::getContent)
                    .reduce("", (a, b) -> a + b + "\n---\n");

            String newSummary = chatClient.prompt()
                    .user(String.format(SUMMARIZE_PROMPT, existingSummary, eventText))
                    .call()
                    .content();

            if (newSummary == null || newSummary.isBlank()) {
                log.warn("AI returned empty summary for userId={}", userId);
                return;
            }

            summaries.forEach(this::deleteMemory);
            saveMemory(userId, TYPE_SUMMARY, newSummary.trim(), 10);

            events.forEach(e -> {
                e.setDeleted(1);
                memoryMapper.updateById(e);
            });

            log.info("Memory summary compressed for userId={}, events={}", userId, events.size());
        } catch (Exception e) {
            log.error("Summary compression failed for userId={}: {}", userId, e.getMessage());
        }
    }

    private void extractAndSaveProfile(String userId, String userMessage) {
        try {
            String profile = chatClient.prompt()
                    .user(String.format(EXTRACT_PROFILE_PROMPT, userMessage))
                    .call()
                    .content();
            if (profile != null && !profile.isBlank() && !NO_PROFILE.equals(profile.trim())) {
                saveMemory(userId, TYPE_PROFILE, profile.trim(), 8);
            }
        } catch (Exception e) {
            log.warn("Profile extraction failed for userId={}: {}", userId, e.getMessage());
        }
    }

    private List<String> recallByImportance(String userId) {
        return memoryMapper.selectList(
                        new LambdaQueryWrapper<Memory>()
                                .eq(Memory::getUserId, userId)
                                .eq(Memory::getDeleted, 0)
                                .in(Memory::getMemoryType, List.of(TYPE_SUMMARY, TYPE_PROFILE))
                                .orderByDesc(Memory::getImportanceScore)
                                .last("LIMIT 8")
                ).stream()
                .map(Memory::getContent)
                .toList();
    }

    private Memory insertMemory(String userId, String type, String content, int importanceScore) {
        Memory memory = new Memory();
        memory.setUserId(userId);
        memory.setMemoryType(type);
        memory.setContent(content);
        memory.setImportanceScore(importanceScore);
        memoryMapper.insert(memory);
        log.debug("Memory saved: userId={}, type={}", userId, type);
        if (VECTORIZED_TYPES.contains(type)) {
            memoryVectorService.upsertMemoryVector(memory.getId(), userId, type, content, importanceScore);
        }
        return memory;
    }

    private boolean containsProfileInfo(String text) {
        for (String kw : PROFILE_KEYWORDS) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }
}

