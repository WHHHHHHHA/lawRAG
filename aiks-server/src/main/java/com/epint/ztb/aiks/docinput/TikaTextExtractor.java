package com.epint.ztb.aiks.docinput;

import java.nio.charset.StandardCharsets;

import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于 Apache Tika 的文档文本提取（pdf/doc/docx/txt 统一处理）
 */
@Component
public class TikaTextExtractor {

    /**
     * 提取纯文本。扫描件等无文字层文档返回空串，由调用方判定 FAILED。
     */
    public String extract(String fileName, byte[] fileBytes) {
        Resource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                // 提供 文件名供 Tika 做内容类型嗅探
                return fileName;
            }
        };
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        StringBuilder sb = new StringBuilder();
        reader.get().forEach(doc -> sb.append(doc.getText()).append('\n'));
        return sb.toString().trim();
    }

    /**
     * 提取 UTF-8 文本文件内容（txt 直接读取，避免 Tika 对编码的误判）
     */
    public String extractTextFile(byte[] fileBytes) {
        return new String(fileBytes, StandardCharsets.UTF_8).trim();
    }

    /** 是否为纯文本文件 */
    public static boolean isTextFile(String fileExt) {
        return StringUtils.hasText(fileExt) && "txt".equalsIgnoreCase(fileExt);
    }
}
