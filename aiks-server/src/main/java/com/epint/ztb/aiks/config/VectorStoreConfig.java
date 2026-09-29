package com.epint.ztb.aiks.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.epint.ztb.aiks.common.AiksErrorCode;
import com.epint.ztb.aiks.common.AiksException;

/**
 * 向量库装配。
 *
 * 第一期使用 SimpleVectorStore + JSON 文件持久化（法规库 1~5 万 chunk 规模下
 * 内存占用可控、零新增中间件）。所有读写收敛到 Spring AI 的 VectorStore 接口，
 * 后续通过 aiks.vectorstore.type 切换 redis/milvus 时业务代码无需改动。
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(AiksProperties props, EmbeddingModel embeddingModel) {
        String type = props.getVectorstore().getType();
        if ("simple".equalsIgnoreCase(type)) {
            return SimpleVectorStore.builder(embeddingModel).build();
        }
        // 预留：redis（Redis Stack RediSearch）/ milvus，二期按需引入对应 starter
        throw new AiksException(AiksErrorCode.SERVICE_DEGRADED, "暂不支持的向量库类型: " + type);
    }
}
