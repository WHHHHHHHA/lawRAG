package com.epint.ztb.aiks.docinput;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.epint.ztb.aiks.common.AiksErrorCode;
import com.epint.ztb.aiks.common.AiksException;
import com.epint.ztb.aiks.dto.PageResult;
import com.epint.ztb.aiks.entity.DocChunk;
import com.epint.ztb.aiks.entity.DocInfo;
import com.epint.ztb.aiks.mapper.DocChunkMapper;
import com.epint.ztb.aiks.mapper.DocInfoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 法规文档管理：上传（保存原始文件+登记+触发异步摄取）、列表、详情、删除、重建向量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> ALLOWED_EXTS = Set.of("pdf", "doc", "docx", "txt");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final DocInfoMapper docInfoMapper;
    private final DocChunkMapper docChunkMapper;
    private final DocumentIngestService ingestService;
    private final VectorStoreManager vectorStoreManager;

    /**
     * 上传文档，立即返回 docGuid，摄取异步进行（轮询 status 直到 READY）
     */
    public DocInfo upload(MultipartFile file, String lawName, String lawLevel, String category,
            String docNo, String publishOrg, String publishDate, String effectiveDate, String userId) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            throw new AiksException(AiksErrorCode.PARAM_ERROR, "缺少文件名或扩展名");
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTS.contains(ext)) {
            throw new AiksException(AiksErrorCode.PARAM_ERROR,
                    "不支持的文件类型: " + ext + "，仅支持 " + String.join("/", ALLOWED_EXTS));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AiksException(AiksErrorCode.PARAM_ERROR, "文件超过大小限制（20MB）");
        }

        DocInfo doc = new DocInfo();
        doc.setDocGuid(DocumentIngestService.newGuid());
        doc.setDocName(lawName != null && !lawName.isBlank() ? lawName : fileName);
        doc.setFileName(fileName);
        doc.setFileExt(ext);
        doc.setFileSize(file.getSize());
        doc.setLawName(lawName);
        doc.setLawLevel(lawLevel);
        doc.setCategory(category);
        doc.setDocNo(docNo);
        doc.setPublishOrg(publishOrg);
        doc.setPublishDate(parseDate(publishDate));
        doc.setEffectiveDate(parseDate(effectiveDate));
        doc.setChunkCount(0);
        doc.setStatus(DocInfo.STATUS_UPLOADED);
        doc.setSourceFrom("manual");
        doc.setCreateBy(userId);
        LocalDateTime now = LocalDateTime.now();
        doc.setCreateDate(now);
        doc.setUpdateDate(now);

        // 原始文件落盘（revectorize 重跑时复用，无需重新上传）
        try {
            Files.createDirectories(DocumentIngestService.rawFilePath(doc).getParent());
            file.transferTo(DocumentIngestService.rawFilePath(doc).toFile());
        } catch (IOException e) {
            log.error("保存原始文件失败", e);
            throw new AiksException(AiksErrorCode.SERVICE_DEGRADED, "保存上传文件失败");
        }

        docInfoMapper.insert(doc);
        ingestService.ingest(doc.getDocGuid());
        log.info("文档已上传并触发摄取: docGuid={}, fileName={}", doc.getDocGuid(), fileName);
        return doc;
    }

    public PageResult<DocInfo> list(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<DocInfo> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(DocInfo::getDocName, keyword).or().like(DocInfo::getLawName, keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(DocInfo::getStatus, status);
        }
        wrapper.orderByDesc(DocInfo::getCreateDate);
        Page<DocInfo> result = docInfoMapper.selectPage(Page.of(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public DocInfo detail(String docGuid) {
        DocInfo doc = docInfoMapper.selectById(docGuid);
        if (doc == null) {
            throw new AiksException(AiksErrorCode.NOT_FOUND, "文档不存在: " + docGuid);
        }
        return doc;
    }

    /**
     * 分块摘要列表（不含 content 正文，避免大响应）
     */
    public List<DocChunk> chunkSummary(String docGuid) {
        return docChunkMapper.selectList(new LambdaQueryWrapper<DocChunk>()
                .eq(DocChunk::getDocGuid, docGuid)
                .orderByAsc(DocChunk::getChunkIndex));
    }

    /**
     * 删除文档：向量 + 分块记录 + 原始文件 + 文档记录。
     * 摄取进行中（PARSING/CHUNKING/EMBEDDING）的文档不允许删除，避免状态错乱。
     */
    public void delete(String docGuid) {
        DocInfo doc = detail(docGuid);
        if (Arrays.asList(DocInfo.STATUS_PARSING, DocInfo.STATUS_CHUNKING, DocInfo.STATUS_EMBEDDING)
                .contains(doc.getStatus())) {
            throw new AiksException(AiksErrorCode.PARAM_ERROR,
                    "文档正在摄取中（" + doc.getStatus() + "），请等待完成后再删除");
        }
        List<DocChunk> chunks = docChunkMapper.selectList(
                new LambdaQueryWrapper<DocChunk>().eq(DocChunk::getDocGuid, docGuid));
        if (!chunks.isEmpty()) {
            vectorStoreManager.delete(chunks.stream().map(DocChunk::getChunkGuid).toList());
        }
        docChunkMapper.delete(new LambdaQueryWrapper<DocChunk>().eq(DocChunk::getDocGuid, docGuid));
        docInfoMapper.deleteById(docGuid);
        try {
            Files.deleteIfExists(DocumentIngestService.rawFilePath(doc));
        } catch (IOException e) {
            log.warn("删除原始文件失败（忽略）: {}", docGuid, e);
        }
        log.info("文档已删除: docGuid={}", docGuid);
    }

    /**
     * 重新向量化（换 embedding 模型后按文档逐个重建）
     */
    public void revectorize(String docGuid) {
        DocInfo doc = detail(docGuid);
        if (!Files.exists(DocumentIngestService.rawFilePath(doc))) {
            throw new AiksException(AiksErrorCode.PARAM_ERROR, "原始文件不存在，请重新上传");
        }
        doc.setStatus(DocInfo.STATUS_UPLOADED);
        doc.setUpdateDate(LocalDateTime.now());
        docInfoMapper.updateById(doc);
        ingestService.ingest(docGuid);
        log.info("文档重新摄取已触发: docGuid={}", docGuid);
    }

    private java.time.LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(date.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new AiksException(AiksErrorCode.PARAM_ERROR, "日期格式错误（应为 yyyy-MM-dd）: " + date);
        }
    }
}
