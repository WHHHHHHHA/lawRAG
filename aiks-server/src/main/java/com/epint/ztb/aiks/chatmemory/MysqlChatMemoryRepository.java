package com.epint.ztb.aiks.chatmemory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.epint.ztb.aiks.docinput.DocumentIngestService;
import com.epint.ztb.aiks.entity.ChatMessageEntity;
import com.epint.ztb.aiks.entity.ChatSessionEntity;
import com.epint.ztb.aiks.mapper.ChatMessageMapper;
import com.epint.ztb.aiks.mapper.ChatSessionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话历史落库（实现 Spring AI ChatMemoryRepository）：
 * 重启不丢、可审计、可分页查询。配合 MessageWindowChatMemory 实现窗口裁剪。
 *
 * AssistantMessage 的 metadata 中可携带 promptTokens/completionTokens/latencyMs/refDocGuids，
 * 持久化时写入消息留痕表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MysqlChatMemoryRepository implements ChatMemoryRepository {

    public static final String META_PROMPT_TOKENS = "promptTokens";
    public static final String META_COMPLETION_TOKENS = "completionTokens";
    public static final String META_LATENCY_MS = "latencyMs";
    public static final String META_REF_DOC_GUIDS = "refDocGuids";
    /** 引用详情JSON串（由 RagChatService 序列化，此处不解析直接落 ref_json 列） */
    public static final String META_REF_JSON = "refJson";

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;

    @Override
    public List<String> findConversationIds() {
        List<ChatSessionEntity> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSessionEntity>().eq(ChatSessionEntity::getStatus, "ACTIVE"));
        return sessions.stream().map(ChatSessionEntity::getSessionGuid).toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<ChatMessageEntity> rows = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionGuid, conversationId)
                        .orderByAsc(ChatMessageEntity::getSeq));
        List<Message> messages = new ArrayList<>(rows.size());
        for (ChatMessageEntity row : rows) {
            messages.add(toMessage(row));
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (Message message : messages) {
            ChatMessageEntity row = new ChatMessageEntity();
            row.setMsgGuid(DocumentIngestService.newGuid());
            row.setSessionGuid(conversationId);
            row.setRole(message.getMessageType().getValue());
            row.setContent(message.getText());
            applyMetadata(row, message.getMetadata());
            row.setCreateDate(LocalDateTime.now());
            chatMessageMapper.insert(row);
        }
        touchSession(conversationId, messages.size());
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionGuid, conversationId));
        ChatSessionEntity session = chatSessionMapper.selectById(conversationId);
        if (session != null) {
            session.setStatus("DELETED");
            session.setLastActiveDate(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
        log.info("会话已删除: {}", conversationId);
    }

    /** 会话不存在时创建最小会话行，存在时更新活跃时间与消息数 */
    private void touchSession(String conversationId, int addedCount) {
        ChatSessionEntity session = chatSessionMapper.selectById(conversationId);
        if (session == null) {
            session = new ChatSessionEntity();
            session.setSessionGuid(conversationId);
            session.setMessageCount(addedCount);
            session.setStatus("ACTIVE");
            LocalDateTime now = LocalDateTime.now();
            session.setCreateDate(now);
            session.setLastActiveDate(now);
            chatSessionMapper.insert(session);
        } else {
            session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + addedCount);
            session.setLastActiveDate(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
    }

    private void applyMetadata(ChatMessageEntity row, Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        row.setPromptTokens(intValue(metadata.get(META_PROMPT_TOKENS)));
        row.setCompletionTokens(intValue(metadata.get(META_COMPLETION_TOKENS)));
        row.setLatencyMs(intValue(metadata.get(META_LATENCY_MS)));
        Object refDocGuids = metadata.get(META_REF_DOC_GUIDS);
        row.setRefDocGuids(refDocGuids != null ? refDocGuids.toString() : null);
        Object refJson = metadata.get(META_REF_JSON);
        row.setRefJson(refJson != null ? refJson.toString() : null);
    }

    private Integer intValue(Object v) {
        if (v instanceof Number) {
            return Integer.valueOf(v.toString());
        }
        return null;
    }

    private Message toMessage(ChatMessageEntity row) {
        if (MessageType.ASSISTANT.getValue().equals(row.getRole())) {
            return new AssistantMessage(row.getContent());
        }
        if (MessageType.USER.getValue().equals(row.getRole())) {
            return new UserMessage(row.getContent());
        }
        return new SystemMessage(row.getContent() == null ? "" : row.getContent());
    }
}
