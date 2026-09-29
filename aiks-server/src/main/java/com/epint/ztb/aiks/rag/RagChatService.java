package com.epint.ztb.aiks.rag;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import com.epint.ztb.aiks.chatmemory.MysqlChatMemoryRepository;
import com.epint.ztb.aiks.common.AiksErrorCode;
import com.epint.ztb.aiks.common.AiksException;
import com.epint.ztb.aiks.config.AiksProperties;
import com.epint.ztb.aiks.docinput.DocumentIngestService;
import com.epint.ztb.aiks.dto.ChatDtos.AskRequest;
import com.epint.ztb.aiks.dto.ChatDtos.AskResult;
import com.epint.ztb.aiks.dto.ChatDtos.ReferenceItem;
import com.epint.ztb.aiks.dto.ChatDtos.UsageInfo;
import com.epint.ztb.aiks.entity.ChatSessionEntity;
import com.epint.ztb.aiks.mapper.ChatSessionMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * RAG 问答主流程：
 * 会话保障 → 向量检索（0命中时代码层固定拒答，不依赖模型自觉）→ 拼装 prompt
 * （系统提示词 + 会话历史窗口 + 编号片段用户消息）→ 大模型生成 → 引用解析 → 记忆落库。
 *
 * 说明：不使用 MessageWindowChatMemory.add()——其窗口裁剪为"全删+重写"实现，
 * 会破坏本服务 append-only 的消息留痕表（丢失 token/延迟等元数据）。
 * 这里直接使用 ChatMemoryRepository 追加写入，窗口裁剪在读侧完成。
 */
@Slf4j
@Service
public class RagChatService {

    private static final String FALLBACK_ANSWER =
            "现有法规库中未找到该问题的直接依据。建议您：1）尝试更换问法或使用法规中的规范表述；2）确认相关法规文件是否已录入知识库。";

    private final RetrievalService retrievalService;
    private final RagPromptBuilder promptBuilder;
    private final CitationResolver citationResolver;
    private final OpenAiChatModel chatModel;
    private final ChatSessionMapper chatSessionMapper;
    private final MysqlChatMemoryRepository chatMemoryRepository;
    private final AiksProperties props;

    public RagChatService(RetrievalService retrievalService, RagPromptBuilder promptBuilder,
            CitationResolver citationResolver, OpenAiChatModel chatModel,
            ChatSessionMapper chatSessionMapper, MysqlChatMemoryRepository chatMemoryRepository,
            AiksProperties props) {
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.citationResolver = citationResolver;
        this.chatModel = chatModel;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMemoryRepository = chatMemoryRepository;
        this.props = props;
    }

    public AskResult ask(AskRequest request) {
        long start = System.currentTimeMillis();
        String question = request.question().trim();

        // 1. 会话保障：无 sessionId 则生成；会话不存在则创建（标题取首个问题）
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? DocumentIngestService.newGuid()
                : request.sessionId().trim();
        ensureSession(sessionId, request.userId(), question);

        // 2. 向量检索
        List<Document> fragments = retrievalService.retrieve(question, request.topK());
        if (fragments.isEmpty()) {
            log.info("检索无命中，代码层固定拒答: sessionId={}", sessionId);
            int latency = (int) (System.currentTimeMillis() - start);
            saveExchange(sessionId, question, FALLBACK_ANSWER, List.of(), 0, 0, latency);
            return new AskResult(sessionId, FALLBACK_ANSWER, true, List.of(),
                    new UsageInfo(0, 0), latency);
        }

        // 3. 拼装消息：系统提示词 + 历史窗口（读侧裁剪）+ 编号片段用户消息
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptBuilder.systemPrompt()));
        messages.addAll(recentHistory(sessionId));
        messages.add(new UserMessage(promptBuilder.buildUserMessage(question, fragments)));

        // 4. 大模型生成
        ChatResponse response;
        try {
            response = chatModel.call(new Prompt(messages,
                    OpenAiChatOptions.builder()
                            .model(props.getChat().getModel())
                            .temperature(props.getChat().getTemperature())
                            .build()));
        } catch (Exception e) {
            log.error("大模型调用失败: sessionId={}", sessionId, e);
            throw new AiksException(AiksErrorCode.MODEL_CALL_FAILED, "大模型服务调用失败，请稍后重试");
        }

        String answer = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();
        int promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        int latency = (int) (System.currentTimeMillis() - start);

        // 5. 引用解析（〔n〕 → references）
        List<ReferenceItem> references = citationResolver.resolve(answer, fragments);

        // 6. 记忆落库（问题原文与回答，附带用量元数据）
        saveExchange(sessionId, question, answer, references, promptTokens, completionTokens, latency);

        log.info("问答完成: sessionId={}, 引用数={}, tokens={}/{}, latency={}ms",
                sessionId, references.size(), promptTokens, completionTokens, latency);
        return new AskResult(sessionId, answer, false, references,
                new UsageInfo(promptTokens, completionTokens), latency);
    }

    private void ensureSession(String sessionId, String userId, String question) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            return;
        }
        session = new ChatSessionEntity();
        session.setSessionGuid(sessionId);
        session.setUserId(userId);
        session.setTitle(truncate(question, 50));
        session.setMessageCount(0);
        session.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        session.setCreateDate(now);
        session.setLastActiveDate(now);
        chatSessionMapper.insert(session);
    }

    /**
     * 追加写入会话记忆：用户问题原文 + 携带用量元数据的回答。
     */
    private void saveExchange(String sessionId, String question, String answer,
            List<ReferenceItem> references, int promptTokens, int completionTokens, int latencyMs) {
        Map<String, Object> assistantMetadata = new HashMap<>();
        assistantMetadata.put(MysqlChatMemoryRepository.META_PROMPT_TOKENS, promptTokens);
        assistantMetadata.put(MysqlChatMemoryRepository.META_COMPLETION_TOKENS, completionTokens);
        assistantMetadata.put(MysqlChatMemoryRepository.META_LATENCY_MS, latencyMs);
        if (references != null && !references.isEmpty()) {
            assistantMetadata.put(MysqlChatMemoryRepository.META_REF_DOC_GUIDS,
                    references.stream().map(ReferenceItem::docGuid).distinct()
                            .collect(Collectors.joining(",")));
        }
        chatMemoryRepository.saveAll(sessionId, List.of(
                new UserMessage(question),
                // Spring AI 1.0.0 无 AssistantMessage.builder()（1.1 才引入），用构造函数传入 metadata
                new AssistantMessage(answer, assistantMetadata)));
    }

    /** 读取最近 N 条历史（读侧窗口裁剪，全量留痕仍在库中） */
    private List<Message> recentHistory(String sessionId) {
        List<Message> all = chatMemoryRepository.findByConversationId(sessionId);
        int max = props.getRag().getHistoryMaxMessages();
        if (all.size() <= max) {
            return all;
        }
        return all.subList(all.size() - max, all.size());
    }

    /** 删除会话（清空消息留痕，会话标记 DELETED） */
    public void deleteSession(String sessionId) {
        chatMemoryRepository.deleteByConversationId(sessionId);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
