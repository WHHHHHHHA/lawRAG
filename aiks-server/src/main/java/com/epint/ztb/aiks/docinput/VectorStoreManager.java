package com.epint.ztb.aiks.docinput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import com.epint.ztb.aiks.common.AiksErrorCode;
import com.epint.ztb.aiks.common.AiksException;
import com.epint.ztb.aiks.config.AiksProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 向量库管理器：封装 SimpleVectorStore 的文件持久化（加载/保存）与写并发控制。
 *
 * 检索读操作直接走 {@link #getStore()}（SimpleVectorStore 内部为 ConcurrentHashMap，读安全）；
 * 写操作（增/删）统一走本类，加锁串行并在每次写后落盘。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreManager {

    private final VectorStore vectorStore;
    private final AiksProperties props;

    private final Object writeLock = new Object();
    private File storeFile;

    @PostConstruct
    public void init() throws IOException {
        Path path = Paths.get(props.getVectorstore().getSimpleFile());
        Files.createDirectories(path.getParent());
        storeFile = path.toFile();
        if (storeFile.exists() && vectorStore instanceof SimpleVectorStore) {
            SimpleVectorStore svs = (SimpleVectorStore) vectorStore;
            svs.load(storeFile);
            log.info("向量库文件已加载: {}", storeFile.getAbsolutePath());
        }
    }

    public void add(List<Document> documents) {
        synchronized (writeLock) {
            vectorStore.add(documents);
            persist();
        }
    }

    public void delete(List<String> documentIds) {
        synchronized (writeLock) {
            vectorStore.delete(documentIds);
            persist();
        }
    }

    public VectorStore getStore() {
        return vectorStore;
    }

    private void persist() {
        if (vectorStore instanceof SimpleVectorStore) {
            SimpleVectorStore svs = (SimpleVectorStore) vectorStore;
            svs.save(storeFile);
        } else {
            throw new AiksException(AiksErrorCode.SERVICE_DEGRADED, "当前向量库类型不支持文件持久化，请配置 redis/milvus");
        }
    }
}
