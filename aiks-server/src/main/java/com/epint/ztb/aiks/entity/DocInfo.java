package com.epint.ztb.aiks.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 法规文档信息表
 */
@Data
@TableName("aiks_doc_info")
public class DocInfo {

    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_CHUNKING = "CHUNKING";
    public static final String STATUS_EMBEDDING = "EMBEDDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(value = "doc_guid", type = IdType.INPUT)
    private String docGuid;

    private String docName;
    private String fileName;
    private String fileExt;
    private Long fileSize;

    private String lawName;
    /** 法律/行政法规/部门规章/规范性文件/其他 */
    private String lawLevel;
    /** 货物/工程/服务/通用 */
    private String category;
    private String docNo;
    private String publishOrg;
    private LocalDate publishDate;
    private LocalDate effectiveDate;

    private Integer chunkCount;
    private String status;
    private String failReason;
    private String sourceFrom;
    private String createBy;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;
}
