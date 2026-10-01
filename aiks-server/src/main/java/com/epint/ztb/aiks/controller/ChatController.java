package com.epint.ztb.aiks.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.epint.ztb.aiks.common.ApiResult;
import com.epint.ztb.aiks.dto.ChatDtos.AskRequest;
import com.epint.ztb.aiks.dto.ChatDtos.AskResult;
import com.epint.ztb.aiks.dto.ChatDtos.ChatMessageItem;
import com.epint.ztb.aiks.dto.ChatDtos.ReferenceItem;
import com.epint.ztb.aiks.dto.PageResult;
import com.epint.ztb.aiks.entity.ChatMessageEntity;
import com.epint.ztb.aiks.mapper.ChatMessageMapper;
import com.epint.ztb.aiks.rag.RagChatService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 智能问答接口（X-Api-Key 鉴权，见 ApiKeyAuthFilter）
 *
 * 注意：第一期为非流式同步接口，LLM 响应通常 5~60s，
 * 调用方（平台侧转发）读超时需设置为 180s 以上。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService ragChatService;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    /**
     * 法规咨询问答（多轮：携带上一轮返回的 sessionId）
     */
    @PostMapping("/chat")
    public ApiResult<AskResult> ask(@Valid @RequestBody AskRequest request) {
        return ApiResult.ok(ragChatService.ask(request));
    }

    /**
     * 会话消息历史（分页，按时间正序）。assistant 消息的引用详情（ref_json）反序列化为
     * references[]，与实时问答接口同构，前端回看历史时可同样渲染〔n〕出处。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResult<PageResult<ChatMessageItem>> messages(
            @PathVariable String sessionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<ChatMessageEntity> result = chatMessageMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionGuid, sessionId)
                        .orderByAsc(ChatMessageEntity::getSeq));
        List<ChatMessageItem> items = result.getRecords().stream().map(this::toItem).toList();
        return ApiResult.ok(new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), items));
    }

    private ChatMessageItem toItem(ChatMessageEntity row) {
        return new ChatMessageItem(row.getMsgGuid(), row.getSessionGuid(), row.getRole(), row.getContent(),
                parseReferences(row.getRefJson()), row.getRefDocGuids(),
                row.getPromptTokens(), row.getCompletionTokens(), row.getLatencyMs(),
                row.getSeq(), row.getCreateDate());
    }

    /** ref_json 反序列化；为空或格式异常时返回 null，不影响历史查询 */
    private List<ReferenceItem> parseReferences(String refJson) {
        if (refJson == null || refJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(refJson, new TypeReference<List<ReferenceItem>>() {
            });
        } catch (Exception e) {
            log.warn("历史消息引用详情反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 删除会话（清空消息留痕，会话标记 DELETED）
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResult<Void> deleteSession(@PathVariable String sessionId) {
        ragChatService.deleteSession(sessionId);
        return ApiResult.ok(null);
    }
}
