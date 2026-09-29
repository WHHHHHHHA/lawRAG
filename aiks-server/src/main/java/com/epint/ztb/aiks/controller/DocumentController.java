package com.epint.ztb.aiks.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.epint.ztb.aiks.common.ApiResult;
import com.epint.ztb.aiks.docinput.DocumentService;
import com.epint.ztb.aiks.dto.PageResult;
import com.epint.ztb.aiks.entity.DocChunk;
import com.epint.ztb.aiks.entity.DocInfo;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * 法规文档管理接口（X-Api-Key 鉴权，见 ApiKeyAuthFilter）
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传法规文档（multipart），异步摄取，返回 docGuid 供轮询状态
     */
    @PostMapping("/upload")
    public ApiResult<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "lawName", required = false) String lawName,
            @RequestParam(value = "lawLevel", required = false) String lawLevel,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "docNo", required = false) String docNo,
            @RequestParam(value = "publishOrg", required = false) String publishOrg,
            @RequestParam(value = "publishDate", required = false) String publishDate,
            @RequestParam(value = "effectiveDate", required = false) String effectiveDate,
            @RequestParam(value = "userId", required = false) String userId) {
        DocInfo doc = documentService.upload(file, lawName, lawLevel, category, docNo, publishOrg,
                publishDate, effectiveDate, userId);
        Map<String, String> data = new HashMap<>();
        data.put("docGuid", doc.getDocGuid());
        data.put("status", doc.getStatus());
        return ApiResult.ok(data);
    }

    /**
     * 文档分页列表（keyword 匹配文档名/法规名，status 过滤状态）
     */
    @GetMapping
    public ApiResult<PageResult<DocInfo>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResult.ok(documentService.list(keyword, status, page, size));
    }

    /**
     * 文档详情 + 分块摘要
     */
    @GetMapping("/{docGuid}")
    public ApiResult<Map<String, Object>> detail(@PathVariable @NotBlank String docGuid) {
        DocInfo doc = documentService.detail(docGuid);
        List<DocChunk> chunks = documentService.chunkSummary(docGuid).stream()
                .peek(c -> c.setContent(null))
                .toList();
        Map<String, Object> data = new HashMap<>();
        data.put("doc", doc);
        data.put("chunks", chunks);
        return ApiResult.ok(data);
    }

    /**
     * 删除文档（向量 + 分块 + 原始文件 + 记录）
     */
    @DeleteMapping("/{docGuid}")
    public ApiResult<Void> delete(@PathVariable @NotBlank String docGuid) {
        documentService.delete(docGuid);
        return ApiResult.ok(null);
    }

    /**
     * 重新摄取/向量化（换 embedding 模型后逐文档重建）
     */
    @PostMapping("/{docGuid}/revectorize")
    public ApiResult<Map<String, String>> revectorize(@PathVariable @NotBlank String docGuid) {
        documentService.revectorize(docGuid);
        Map<String, String> data = new HashMap<>();
        data.put("docGuid", docGuid);
        data.put("status", "UPLOADED");
        return ApiResult.ok(data);
    }
}
