package com.epint.ztb.aiks.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 问答会话表
 */
@Data
@TableName("aiks_chat_session")
public class ChatSessionEntity {

    @TableId(value = "session_guid", type = IdType.INPUT)
    private String sessionGuid;

    /** 平台侧传入的匿名用户ID */
    private String userId;
    /** 取首个问题截断生成 */
    private String title;
    private Integer messageCount;
    private String status;
    private LocalDateTime createDate;
    private LocalDateTime lastActiveDate;
}
