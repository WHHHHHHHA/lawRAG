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
import com.epint.ztb.aiks.dto.PageResult;
import com.epint.ztb.aiks.entity.ChatMessageEntity;
import com.epint.ztb.aiks.mapper.ChatMessageMapper;
import com.epint.ztb.aiks.rag.RagChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 智能问答接口（X-Api-Key 鉴权，见 ApiKeyAuthFilter）
 *
 * 注意：第一期为非流式同步接口，LLM 响应通常 5~60s，
 * 调用方（平台侧转发）读超时需设置为 180s 以上。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService ragChatService;
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 法规咨询问答（多轮：携带上一轮返回的 sessionId）
     */
    @PostMapping("/chat")
    public ApiResult<AskResult> ask(@Valid @RequestBody AskRequest request) {
        return ApiResult.ok(ragChatService.ask(request));
    }

    /**
     * 会话消息历史（分页，按时间正序）
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResult<PageResult<ChatMessageEntity>> messages(
            @PathVariable String sessionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<ChatMessageEntity> result = chatMessageMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionGuid, sessionId)
                        .orderByAsc(ChatMessageEntity::getSeq));
        return ApiResult.ok(new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords()));
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
