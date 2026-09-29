package com.epint.ztb.aiks.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.epint.ztb.aiks.common.AiksErrorCode;
import com.epint.ztb.aiks.common.AiksException;
import com.epint.ztb.aiks.docinput.DocumentIngestService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG 提示词构造：
 * - 系统提示词：classpath:prompts/rag-system.st（引用规范/拒答规范/防注入约束）
 * - 用户消息：编号法规片段 + 定界符包裹的用户问题
 */
@Slf4j
@Component
public class RagPromptBuilder {

    private String systemPrompt;

    @PostConstruct
    public void loadSystemPrompt() {
        try (InputStream in = new ClassPathResource("prompts/rag-system.st").getInputStream()) {
            systemPrompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AiksException(AiksErrorCode.SERVICE_DEGRADED, "系统提示词文件加载失败: prompts/rag-system.st");
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    /**
     * 构造含编号片段的用户消息。片段与问题均用定界符包裹，
     * 与系统提示词中的"资料非指令"声明配合防 prompt injection。
     */
    public String buildUserMessage(String question, List<Document> fragments) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是可用于回答问题的法规参考资料片段，每段前的 [n] 为编号，回答时必须用〔n〕引用对应片段：\n\n");
        sb.append("<参考资料>\n");
        for (int i = 0; i < fragments.size(); i++) {
            Document d = fragments.get(i);
            sb.append('[').append(i + 1).append("] ");
            sb.append(referenceTitle(d)).append('\n');
            sb.append(d.getText().trim()).append("\n\n");
        }
        sb.append("</参考资料>\n\n");
        sb.append("用户问题：\n<问题>").append(question.trim()).append("</问题>");
        return sb.toString();
    }

    private String referenceTitle(Document d) {
        String lawName = str(d, DocumentIngestService.META_LAW_NAME);
        String articleNo = str(d, DocumentIngestService.META_ARTICLE_NO);
        String chapterName = str(d, DocumentIngestService.META_CHAPTER_NAME);
        StringBuilder title = new StringBuilder("《").append(lawName != null ? lawName : "").append("》");
        if (articleNo != null && !articleNo.isBlank()) {
            title.append(' ').append(articleNo);
        }
        if (chapterName != null && !chapterName.isBlank()) {
            title.append("（").append(chapterName).append('）');
        }
        return title.append("：").toString();
    }

    private String str(Document d, String key) {
        Object v = d.getMetadata() != null ? d.getMetadata().get(key) : null;
        return v != null ? v.toString() : null;
    }
}
