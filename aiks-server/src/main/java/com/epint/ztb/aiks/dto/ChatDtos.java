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
            /** true=检索无命中，answer 为兜底回答（模型按兜底提示词生成：寒暄引导或未命中提示，调用失败时为固定话术），references 为空 */
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

    /** 会话历史消息（留痕表行 + 反序列化的引用详情） */
    public record ChatMessageItem(
            String msgGuid,
            String sessionId,
            /** user / assistant */
            String role,
            String content,
            /** assistant 消息：回答中〔n〕对应的结构化出处（与实时问答 references 同构）；user 消息为 null */
            List<ReferenceItem> references,
            /** 引用的文档GUID，逗号分隔（冗余保留，便于按文档统计） */
            String refDocGuids,
            Integer promptTokens,
            Integer completionTokens,
            Integer latencyMs,
            Long seq,
            java.time.LocalDateTime createDate) {
    }
}
