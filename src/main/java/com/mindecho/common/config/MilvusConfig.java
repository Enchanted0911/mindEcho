package com.mindecho.common.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Milvus 向量数据库配置
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private Integer port;

    @Value("${milvus.username:}")
    private String username;

    @Value("${milvus.password:}")
    private String password;

    @Value("${milvus.collection.name}")
    private String collectionName;

    @Value("${milvus.collection.dimension}")
    private Integer dimension;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port);

        if (username != null && !username.isEmpty()) {
            builder.withAuthorization(username, password);
        }

        MilvusServiceClient client = new MilvusServiceClient(builder.build());
        log.info("Milvus client connected to {}:{}", host, port);

        // 初始化 collection
        try {
            initCollection(client);
        } catch (Exception e) {
            log.warn("Milvus collection init failed (will retry on first use): {}", e.getMessage());
        }

        return client;
    }

    /**
     * 初始化 Milvus Collection（若不存在则创建）
     */
    private void initCollection(MilvusServiceClient client) {
        Boolean hasCollection = client.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        ).getData();

        if (Boolean.TRUE.equals(hasCollection)) {
            log.info("Milvus collection '{}' already exists", collectionName);
            return;
        }

        // 定义字段
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();

        FieldType userIdField = FieldType.newBuilder()
                .withName("user_id")
                .withDataType(DataType.Int64)
                .build();

        FieldType memoryIdField = FieldType.newBuilder()
                .withName("memory_id")
                .withDataType(DataType.Int64)
                .build();

        FieldType embeddingField = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(dimension)
                .build();

        CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("MindEcho 用户长期记忆向量集合")
                .withFieldTypes(Arrays.asList(idField, userIdField, memoryIdField, embeddingField))
                .build();

        client.createCollection(createCollectionParam);

        // 创建向量索引
        client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.IP)
                .withExtraParam("{\"nlist\":1024}")
                .build());

        log.info("Milvus collection '{}' created with dimension {}", collectionName, dimension);
    }
}

