package com.mindecho.module.memory.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 向量存储配置
 *
 * <p>项目同时引入了 OpenAI（ChatModel + EmbeddingModel）和 Ollama（本地 bge-m3 EmbeddingModel），
 * Spring AI 会自动装配两个 {@link EmbeddingModel} Bean，因此 pgvector 的 {@link VectorStore}
 * 无法自动推断使用哪个，需在此显式指定使用 Ollama 的 {@link OllamaEmbeddingModel}。
 *
 * <p>bge-m3 输出维度为 1024，与 {@code memory_vector} 表的 {@code vector(1024)} 一致。
 */
@Configuration
public class VectorStoreConfig {

    /**
     * 使用本地 Ollama bge-m3 EmbeddingModel 构建 PgVectorStore
     *
 * <p>声明为 @Primary，确保 {@link com.mindecho.module.memory.service.MemoryVectorService}
 * 中注入的 {@link VectorStore} 使用 Ollama embedding，而不是 OpenAI text-embedding-3-small。
     *
     * @param jdbcTemplate     Spring JDBC Template（由 spring-boot-starter-jdbc 自动装配）
     * @param ollamaEmbedding  Ollama EmbeddingModel（由 spring-ai-starter-model-ollama 自动装配）
     */
    @Bean
    @Primary
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate,
                                     @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbedding) {
        return PgVectorStore.builder(jdbcTemplate, ollamaEmbedding)
                .vectorTableName("memory_vector")           // 指定表名（默认是 vector_store）
                .dimensions(1024)                           // bge-m3 输出维度
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .idType(PgVectorStore.PgIdType.UUID)        // id 列为 UUID 类型（与建表 DDL 一致）
                .initializeSchema(false)                    // 由 init.sql 手动管理
                .build();
    }
}

