package com.epint.ztb.aiks.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 法规文档分块表（向量可重建的权威数据源，chunkGuid 同时作为向量库中的文档ID）
 */
@Data
@TableName("aiks_doc_chunk")
public class DocChunk {

    @TableId(value = "chunk_guid", type = IdType.INPUT)
    private String chunkGuid;

    private String docGuid;
    private Integer chunkIndex;
    /** 条款号，如"第二十二条"；非条款结构文本为空 */
    private String articleNo;
    private String chapterName;
    private String content;
    private Integer tokenCount;
    private String status;
    private LocalDateTime createDate;
}
