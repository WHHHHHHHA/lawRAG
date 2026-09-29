package com.epint.ztb.aiks.docinput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.epint.ztb.aiks.config.AiksProperties;
import com.epint.ztb.aiks.entity.DocChunk;
import com.epint.ztb.aiks.entity.DocInfo;
import com.epint.ztb.aiks.mapper.DocChunkMapper;
import com.epint.ztb.aiks.mapper.DocInfoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档摄取管道（异步状态机）：
 * UPLOADED → PARSING(Tika提取) → CHUNKING(条款分块) → EMBEDDING(批量向量化入库) → READY
 * 任一步骤异常 → FAILED + FailReason。
 *
 * 原始文件保存在 data/rawfiles/{docGuid}.{ext}，revectorize 重跑时无需重新上传。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestService {

    /** 向量 metadata 中的键 */
    public static final String META_DOC_GUID = "docGuid";
    public static final String META_LAW_NAME = "lawName";
    public static final String META_ARTICLE_NO = "articleNo";
    public static final String META_CHAPTER_NAME = "chapterName";
    public static final String META_DOC_NAME = "docName";

    private final DocInfoMapper docInfoMapper;
    private final DocChunkMapper docChunkMapper;
    private final TikaTextExtractor textExtractor;
    private final RegulationChunker regulationChunker;
    private final VectorStoreManager vectorStoreManager;
    private final AiksProperties props;

    @Async("aiksIngestExecutor")
    public void ingest(String docGuid) {
        DocInfo doc = docInfoMapper.selectById(docGuid);
        if (doc == null) {
            log.warn("摄取任务放弃：文档不存在 {}", docGuid);
            return;
        }
        try {
            Path rawFile = rawFilePath(doc);
            byte[] bytes = Files.readAllBytes(rawFile);

            // 1. PARSING：提取文本
            updateStatus(doc, DocInfo.STATUS_PARSING);
            String text = TikaTextExtractor.isTextFile(doc.getFileExt())
                    ? textExtractor.extractTextFile(bytes)
                    : textExtractor.extract(doc.getFileName(), bytes);
            if (text == null || text.length() < props.getIngest().getMinTextChars()) {
                fail(docGuid, "提取文本仅" + (text == null ? 0 : text.length()) + "字符，疑似扫描版/图片型文件，暂不支持OCR解析");
                return;
            }

            // 2. CHUNKING：条款结构分块（先清理旧分块，支持重复摄取）
            updateStatus(doc, DocInfo.STATUS_CHUNKING);
            deleteOldChunks(docGuid);
            List<Chunk> chunks = regulationChunker.chunk(text);
            if (chunks.isEmpty()) {
                fail(docGuid, "分块结果为空");
                return;
            }

            // 3. EMBEDDING：分批向量化入库（batch-size 受云端 embedding 接口批量上限约束）
            updateStatus(doc, DocInfo.STATUS_EMBEDDING);
            int batchSize = props.getEmbedding().getBatchSize();
            int total = 0;
            List<Document> batchDocs = new ArrayList<>(batchSize);
            List<DocChunk> batchRows = new ArrayList<>(batchSize);
            int index = 0;
            for (Chunk c : chunks) {
                String chunkGuid = newGuid();
                batchDocs.add(toVectorDocument(chunkGuid, doc, c));
                batchRows.add(toChunkRow(chunkGuid, doc, index++, c));
                if (batchDocs.size() >= batchSize) {
                    vectorStoreManager.add(batchDocs);
                    batchRows.forEach(docChunkMapper::insert);
                    total += batchDocs.size();
                    batchDocs.clear();
                    batchRows.clear();
                }
            }
            if (!batchDocs.isEmpty()) {
                vectorStoreManager.add(batchDocs);
                batchRows.forEach(docChunkMapper::insert);
                total += batchDocs.size();
            }

            // 4. READY（failReason 需显式置空——updateById 默认策略会忽略 null 字段，
            //    重建场景下旧的失败原因会残留）
            doc.setChunkCount(total);
            doc.setStatus(DocInfo.STATUS_READY);
            doc.setUpdateDate(LocalDateTime.now());
            docInfoMapper.update(doc, new LambdaUpdateWrapper<DocInfo>()
                    .eq(DocInfo::getDocGuid, docGuid)
                    .set(DocInfo::getFailReason, null));
            log.info("文档摄取完成: docGuid={}, 法规={}, 分块数={}", docGuid, doc.getLawName(), total);
        } catch (IOException e) {
            log.error("文档摄取失败: 读取原始文件异常 {}", docGuid, e);
            fail(docGuid, "读取原始文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("文档摄取失败: {}", docGuid, e);
            fail(docGuid, truncate(e.getMessage(), 900));
        }
    }

    /** 构造向量库文档：chunkGuid 作为向量库文档ID（删除向量时按ID定位） */
    private Document toVectorDocument(String chunkGuid, DocInfo doc, Chunk c) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_DOC_GUID, doc.getDocGuid());
        metadata.put(META_LAW_NAME, doc.getLawName() != null ? doc.getLawName() : doc.getDocName());
        metadata.put(META_DOC_NAME, doc.getDocName());
        // 兜底分块（TokenTextSplitter）的 chunk 无条款号/章节名；Spring AI Document 不允许 metadata 含 null 值，空值跳过
        if (c.getArticleNo() != null) {
            metadata.put(META_ARTICLE_NO, c.getArticleNo());
        }
        if (c.getChapterName() != null) {
            metadata.put(META_CHAPTER_NAME, c.getChapterName());
        }
        return Document.builder().id(chunkGuid).text(c.getContent()).metadata(metadata).build();
    }

    private DocChunk toChunkRow(String chunkGuid, DocInfo doc, int index, Chunk c) {
        DocChunk row = new DocChunk();
        row.setChunkGuid(chunkGuid);
        row.setDocGuid(doc.getDocGuid());
        row.setChunkIndex(index);
        row.setArticleNo(c.getArticleNo());
        row.setChapterName(c.getChapterName());
        row.setContent(c.getContent());
        row.setTokenCount(c.estimateTokens());
        row.setStatus("OK");
        row.setCreateDate(LocalDateTime.now());
        return row;
    }

    /** 删除旧分块记录与对应向量（重复摄取/重建场景） */
    private void deleteOldChunks(String docGuid) {
        List<DocChunk> olds = docChunkMapper.selectList(
                new LambdaQueryWrapper<DocChunk>().eq(DocChunk::getDocGuid, docGuid));
        if (olds.isEmpty()) {
            return;
        }
        List<String> ids = olds.stream().map(DocChunk::getChunkGuid).toList();
        vectorStoreManager.delete(ids);
        docChunkMapper.delete(new LambdaQueryWrapper<DocChunk>().eq(DocChunk::getDocGuid, docGuid));
    }

    private void updateStatus(DocInfo doc, String status) {
        doc.setStatus(status);
        doc.setUpdateDate(LocalDateTime.now());
        docInfoMapper.updateById(doc);
    }

    private void fail(String docGuid, String reason) {
        DocInfo doc = docInfoMapper.selectById(docGuid);
        if (doc == null) {
            return;
        }
        doc.setStatus(DocInfo.STATUS_FAILED);
        doc.setFailReason(truncate(reason, 990));
        doc.setUpdateDate(LocalDateTime.now());
        docInfoMapper.updateById(doc);
    }

    public static Path rawFilePath(DocInfo doc) {
        // 必须转为绝对路径：MultipartFile.transferTo 会把相对 File 解析到 Tomcat 上传临时目录，
        // 而 Files.* 解析到进程工作目录（项目目录），相对路径两边不一致导致保存失败。
        return Paths.get("data", "rawfiles", doc.getDocGuid() + "." + doc.getFileExt()).toAbsolutePath();
    }

    public static String newGuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
