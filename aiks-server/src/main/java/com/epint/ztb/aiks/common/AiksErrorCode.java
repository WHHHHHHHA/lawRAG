package com.epint.ztb.aiks.common;

/**
 * 错误码定义
 */
public enum AiksErrorCode {

    OK("0", "success"),
    /** 参数错误 */
    PARAM_ERROR("AIKS_4001", "参数错误"),
    /** 资源不存在 */
    NOT_FOUND("AIKS_4044", "资源不存在"),
    /** X-Api-Key 校验失败 */
    UNAUTHORIZED("AIKS_4101", "未授权的调用"),
    /** 触发限流 */
    RATE_LIMITED("AIKS_4291", "请求过于频繁，请稍后重试"),
    /** 命中敏感词 */
    SENSITIVE_BLOCKED("AIKS_4301", "问题包含敏感内容，无法处理"),
    /** 大模型调用失败 */
    MODEL_CALL_FAILED("AIKS_5001", "大模型服务调用失败"),
    /** 服务降级中 */
    SERVICE_DEGRADED("AIKS_5002", "服务暂不可用");

    private final String code;
    private final String defaultMsg;

    AiksErrorCode(String code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMsg() {
        return defaultMsg;
    }
}
