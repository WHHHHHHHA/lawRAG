package com.epint.ztb.aiks.common;

import lombok.Data;

/**
 * 统一返回结构
 */
@Data
public class ApiResult<T> {

    /** "0" 成功，其余为 AIKS_ 前缀错误码 */
    private String code;
    private String msg;
    private String traceId;
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = AiksErrorCode.OK.getCode();
        r.msg = "success";
        r.traceId = TraceIdFilter.currentTraceId();
        r.data = data;
        return r;
    }

    public static <T> ApiResult<T> fail(AiksErrorCode errorCode, String msg) {
        ApiResult<T> r = new ApiResult<>();
        r.code = errorCode.getCode();
        r.msg = msg != null ? msg : errorCode.getDefaultMsg();
        r.traceId = TraceIdFilter.currentTraceId();
        return r;
    }
}
