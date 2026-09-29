-- ============================================================
-- AIKS 智能问答（法规咨询）服务建库脚本
-- 目标库：aiks（独立 MySQL 5.7+/8.x，utf8mb4）
-- 用法：create database aiks default character set utf8mb4; 然后在本库执行本脚本
-- 注意：向量本体不入库（存于向量库文件），aiks_doc_chunk 为可重建的权威数据源
-- ============================================================

CREATE TABLE IF NOT EXISTS aiks_doc_info (
    doc_guid        VARCHAR(50)  NOT NULL COMMENT '文档GUID',
    doc_name        VARCHAR(200) NOT NULL COMMENT '文档显示名称',
    file_name       VARCHAR(200) NOT NULL COMMENT '原始文件名',
    file_ext        VARCHAR(10)  NOT NULL COMMENT '文件扩展名(pdf/doc/docx/txt)',
    file_size       BIGINT       NOT NULL COMMENT '文件大小(字节)',
    law_name        VARCHAR(200) DEFAULT NULL COMMENT '法规名称',
    law_level       VARCHAR(20)  DEFAULT NULL COMMENT '法律/行政法规/部门规章/规范性文件/其他',
    category        VARCHAR(50)  DEFAULT NULL COMMENT '业务分类(货物/工程/服务/通用)',
    doc_no          VARCHAR(100) DEFAULT NULL COMMENT '文号',
    publish_org     VARCHAR(200) DEFAULT NULL COMMENT '发布机关',
    publish_date    DATE         DEFAULT NULL COMMENT '发布日期',
    effective_date  DATE         DEFAULT NULL COMMENT '生效日期',
    chunk_count     INT          NOT NULL DEFAULT 0 COMMENT '分块数量',
    status          VARCHAR(20)  NOT NULL COMMENT 'UPLOADED/PARSING/CHUNKING/EMBEDDING/READY/FAILED',
    fail_reason     VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
    source_from     VARCHAR(50)  NOT NULL DEFAULT 'manual' COMMENT '来源(manual人工上传)',
    create_by       VARCHAR(50)  DEFAULT NULL COMMENT '创建人(匿名ID)',
    create_date     DATETIME     NOT NULL,
    update_date     DATETIME     NOT NULL,
    PRIMARY KEY (doc_guid),
    KEY idx_status (status),
    KEY idx_law_name (law_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法规文档信息表';

CREATE TABLE IF NOT EXISTS aiks_doc_chunk (
    chunk_guid      VARCHAR(50)  NOT NULL COMMENT '分块GUID(同时作为向量库文档ID)',
    doc_guid        VARCHAR(50)  NOT NULL COMMENT '所属文档',
    chunk_index     INT          NOT NULL COMMENT '块序号',
    article_no      VARCHAR(50)  DEFAULT NULL COMMENT '条款号(如 第二十二条)',
    chapter_name    VARCHAR(200) DEFAULT NULL COMMENT '所属章名',
    content         MEDIUMTEXT   NOT NULL COMMENT '分块文本',
    token_count     INT          NOT NULL DEFAULT 0 COMMENT 'token估算数',
    status          VARCHAR(20)  NOT NULL DEFAULT 'OK' COMMENT 'OK/DELETED',
    create_date     DATETIME     NOT NULL,
    PRIMARY KEY (chunk_guid),
    KEY idx_doc_guid (doc_guid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法规文档分块表(向量可重建的权威源)';

CREATE TABLE IF NOT EXISTS aiks_chat_session (
    session_guid    VARCHAR(50)  NOT NULL COMMENT '会话GUID',
    user_id         VARCHAR(64)  DEFAULT NULL COMMENT '用户标识(平台侧匿名ID)',
    title           VARCHAR(200) DEFAULT NULL COMMENT '会话标题(取首个问题截断)',
    message_count   INT          NOT NULL DEFAULT 0,
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DELETED',
    create_date     DATETIME     NOT NULL,
    last_active_date DATETIME    NOT NULL,
    PRIMARY KEY (session_guid),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答会话表';

CREATE TABLE IF NOT EXISTS aiks_chat_message (
    msg_guid        VARCHAR(50)  NOT NULL COMMENT '消息GUID',
    session_guid    VARCHAR(50)  NOT NULL COMMENT '会话GUID',
    role            VARCHAR(10)  NOT NULL COMMENT 'user/assistant',
    content         MEDIUMTEXT   NOT NULL COMMENT '消息内容',
    ref_doc_guids   VARCHAR(500) DEFAULT NULL COMMENT '回答引用的文档GUID(逗号分隔)',
    prompt_tokens   INT          DEFAULT NULL,
    completion_tokens INT        DEFAULT NULL,
    latency_ms      INT          DEFAULT NULL,
    seq             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '追加序号(保证同秒消息顺序)',
    create_date     DATETIME     NOT NULL,
    PRIMARY KEY (msg_guid),
    UNIQUE KEY uk_seq (seq),
    KEY idx_session_guid (session_guid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答消息留痕表(仅追加,不重写)';

CREATE TABLE IF NOT EXISTS aiks_llm_usage_log (
    log_guid        VARCHAR(50)  NOT NULL,
    biz_type        VARCHAR(20)  NOT NULL COMMENT 'chat/embedding',
    model           VARCHAR(100) NOT NULL,
    prompt_tokens   INT          DEFAULT NULL,
    completion_tokens INT        DEFAULT NULL,
    total_tokens    INT          DEFAULT NULL,
    cost_estimate   DECIMAL(10,4) DEFAULT NULL COMMENT '费用估算(元)',
    session_guid    VARCHAR(50)  DEFAULT NULL,
    user_id         VARCHAR(64)  DEFAULT NULL,
    success_flag    TINYINT      NOT NULL DEFAULT 1,
    err_msg         VARCHAR(500) DEFAULT NULL,
    create_date     DATETIME     NOT NULL,
    PRIMARY KEY (log_guid),
    KEY idx_create_date (create_date),
    KEY idx_biz_type (biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型调用用量日志';

CREATE TABLE IF NOT EXISTS aiks_sensitive_word (
    word_guid       VARCHAR(50)  NOT NULL,
    word            VARCHAR(100) NOT NULL,
    category        VARCHAR(50)  DEFAULT NULL,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (word_guid),
    UNIQUE KEY uk_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词表';

CREATE TABLE IF NOT EXISTS aiks_config (
    config_key      VARCHAR(100) NOT NULL COMMENT '运行时可变配置键(chat.model/chat.temperature/rag.topK/rag.similarityThreshold)',
    config_value    VARCHAR(2000) DEFAULT NULL,
    description     VARCHAR(500) DEFAULT NULL,
    update_date     DATETIME     NOT NULL,
    PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行时配置表(yml为默认值,此处为覆盖值)';

-- 初始敏感词可按需插入，示例：
-- INSERT INTO aiks_sensitive_word (word_guid, word, category) VALUES (REPLACE(UUID(),'-',''), '爆炸物制作', '违禁');
