package com.epint.ztb.aiks.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 问答消息留痕表
 */
@Data
@TableName("aiks_chat_message")
public class ChatMessageEntity {

    @TableId(value = "msg_guid", type = IdType.INPUT)
    private String msgGuid;

    private String sessionGuid;
    /** user / assistant */
    private String role;
    private String content;
    /** 回答引用的文档GUID，逗号分隔 */
    private String refDocGuids;
    /** 回答引用详情JSON（[{refNo,docGuid,lawName,articleNo,chapterName,snippet,score}]），回看历史时还原〔n〕出处 */
    private String refJson;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer latencyMs;
    /** 追加序号（数据库自增，插入时不需要赋值），保证同秒消息顺序 */
    private Long seq;
    private LocalDateTime createDate;
}
