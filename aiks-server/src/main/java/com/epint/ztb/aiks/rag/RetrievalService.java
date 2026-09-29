package com.epint.ztb.aiks.rag;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import com.epint.ztb.aiks.config.AiksProperties;
import com.epint.ztb.aiks.docinput.VectorStoreManager;

import lombok.RequiredArgsConstructor;

/**
 * 向量检索服务：topK + 相似度阈值（均可配置），预留 metadata 过滤扩展
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final VectorStoreManager vectorStoreManager;
    private final AiksProperties props;

    /**
     * @param question      用户问题（当前轮原文，历史仅作 prompt 上下文）
     * @param topKOverride  请求级 topK 覆盖，null 时用默认配置
     * @return 命中的法规片段，按相似度降序
     */
    public List<Document> retrieve(String question, Integer topKOverride) {
        int topK = topKOverride != null && topKOverride > 0 ? topKOverride : props.getRag().getTopK();
        double threshold = props.getRag().getSimilarityThreshold();
        return vectorStoreManager.getStore().similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(threshold)
                .build());
    }
}
