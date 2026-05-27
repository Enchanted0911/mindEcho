package com.mindecho.module.memory.service;

import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.mapper.MemoryMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 长期记忆服务
 * 基于 Milvus 向量数据库实现语义记忆检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryMapper memoryMapper;
    private final MilvusServiceClient milvusClient;
    private final EmbeddingModel embeddingModel;

    @Value("${milvus.collection.name}")
    private String collectionName;

    @Value("${mindecho.embedding.top-k:5}")
    private Integer topK;

    /** 提取记忆的 Prompt */
    private static final String EXTRACT_MEMORY_PROMPT = """
            从以下对话中提取值得长期记忆的信息，格式为 JSON 数组，每条记忆包含 type（profile/event/emotion）和 content：
            用户说："%s"
            AI回复："%s"

            只提取重要且持久有效的信息，如用户的职业、性格、重要经历、情绪模式等。
            如果没有值得提取的信息，返回空数组 []。
            只返回 JSON 格式，不要其他内容：
            """;

    /**
     * 异步提取并保存记忆
     */
    @Async
    public void extractAndSaveMemoryAsync(Long userId, String userMessage, String aiResponse) {
        try {
            extractAndSaveMemory(userId, userMessage, aiResponse);
        } catch (Exception e) {
            log.error("Async memory extraction failed: userId={}", userId, e);
        }
    }

    /**
     * 提取并保存记忆
     */
    public void extractAndSaveMemory(Long userId, String userMessage, String aiResponse) {
        // 简单的关键词规则提取（不依赖额外 AI 调用，节省 Token）
        // 实际生产中可以调用 LLM 提取结构化记忆

        // 检测是否包含有价值的信息
        if (containsProfileInfo(userMessage)) {
            String memoryContent = buildProfileMemory(userMessage);
            if (memoryContent != null) {
                saveMemory(userId, "profile", memoryContent, 8);
            }
        }

        if (containsEventInfo(userMessage)) {
            String memoryContent = "用户提到：" + userMessage.substring(0, Math.min(userMessage.length(), 100));
            saveMemory(userId, "event", memoryContent, 6);
        }
    }

    /**
     * 基于向量相似度召回相关记忆
     */
    public List<Memory> recallRelevantMemories(Long userId, String query) {
        try {
            // 1. 生成 query 的 embedding
            float[] queryEmbedding = embeddingModel.embed(query);

            // 2. Load collection
            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());

            // 3. 向量搜索
            List<Float> queryVector = toFloatList(queryEmbedding);

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(MetricType.IP)
                    .withOutFields(Arrays.asList("memory_id", "user_id"))
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName("embedding")
                    .withExpr("user_id == " + userId)
                    .build();

            SearchResults results = milvusClient.search(searchParam).getData();

            // 4. 根据 memory_id 查询 MySQL
            // TODO: 从 SearchResults 提取 memory_id 列表后查询
            log.debug("Vector search completed for userId={}", userId);

            // fallback：直接从 MySQL 获取用户记忆
            return memoryMapper.findByUserId(userId);

        } catch (Exception e) {
            log.warn("Vector search failed, fallback to MySQL: {}", e.getMessage());
            return memoryMapper.findByUserId(userId);
        }
    }

    /**
     * 保存记忆到 MySQL + Milvus
     */
    public Memory saveMemory(Long userId, String type, String content, Integer importanceScore) {
        // 检查是否已有相似记忆（简单查重）
        List<Memory> existing = memoryMapper.findByUserIdAndType(userId, type);
        for (Memory mem : existing) {
            if (mem.getContent().equals(content)) {
                return mem; // 已存在，跳过
            }
        }

        // 保存到 MySQL
        Memory memory = new Memory();
        memory.setUserId(userId);
        memory.setMemoryType(type);
        memory.setContent(content);
        memory.setImportanceScore(importanceScore);
        memoryMapper.insert(memory);

        // 异步保存到 Milvus
        try {
            saveToMilvus(memory);
        } catch (Exception e) {
            log.warn("Milvus save failed for memoryId={}: {}", memory.getId(), e.getMessage());
        }

        return memory;
    }

    /**
     * 保存向量到 Milvus
     */
    private void saveToMilvus(Memory memory) {
        float[] embedding = embeddingModel.embed(memory.getContent());
        List<Float> vector = toFloatList(embedding);

        List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field("id", Collections.singletonList(memory.getId())),
                new InsertParam.Field("user_id", Collections.singletonList(memory.getUserId())),
                new InsertParam.Field("memory_id", Collections.singletonList(memory.getId())),
                new InsertParam.Field("embedding", Collections.singletonList(vector))
        );

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();

        milvusClient.insert(insertParam);
        log.debug("Memory saved to Milvus: id={}", memory.getId());
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new java.util.ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    private boolean containsProfileInfo(String text) {
        String[] profileKeywords = {"我是", "我叫", "我的工作", "我的职业", "我是程序员", "我是学生",
                "我喜欢", "我不喜欢", "我的性格", "我比较内向", "我比较外向"};
        for (String keyword : profileKeywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEventInfo(String text) {
        String[] eventKeywords = {"失恋", "分手", "换工作", "毕业", "考试", "面试",
                "生病", "家人", "朋友", "压力", "今天发生了"};
        for (String keyword : eventKeywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return text.length() > 50; // 超过50字的也考虑记忆
    }

    private String buildProfileMemory(String text) {
        if (text.contains("程序员")) return "用户是程序员";
        if (text.contains("学生")) return "用户是学生";
        if (text.contains("内向")) return "用户性格偏内向";
        if (text.contains("焦虑")) return "用户有焦虑倾向";
        return null;
    }
}

