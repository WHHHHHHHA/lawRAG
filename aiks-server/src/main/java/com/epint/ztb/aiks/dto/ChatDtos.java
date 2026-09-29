package com.epint.ztb.aiks.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 问答相关 DTO
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    /** 问答请求 */
    public record AskRequest(
            /** 会话ID，首次问答可不传，由服务生成并在结果中返回 */
            String sessionId,
            @NotBlank(message = "question 不能为空")
            @Size(max = 2000, message = "question 长度不能超过2000")
            String question,
            /** 平台侧匿名用户ID */
            String userId,
            /** 检索条数覆盖（默认取 aiks.rag.top-k） */
            Integer topK) {
    }

    /** 问答结果 */
    public record AskResult(
            String sessionId,
            String answer,
            /** true=检索无命中，answer 为固定拒答话术，非模型生成 */
            boolean fallback,
            List<ReferenceItem> references,
            UsageInfo usage,
            int latencyMs) {
    }

    /** 引用出处（对应回答中的〔n〕标注） */
    public record ReferenceItem(
            int refNo,
            String docGuid,
            String lawName,
            String articleNo,
            String chapterName,
            /** 命中片段原文（截断） */
            String snippet,
            Double score) {
    }

    /** token 用量 */
    public record UsageInfo(
            Integer promptTokens,
            Integer completionTokens) {
    }
}
