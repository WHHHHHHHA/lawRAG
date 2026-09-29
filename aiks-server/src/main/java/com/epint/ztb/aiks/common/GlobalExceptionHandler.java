package com.epint.ztb.aiks.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AiksException.class)
    public ApiResult<Void> handleAiks(AiksException e) {
        log.warn("业务异常: code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        return ApiResult.fail(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class,
            IllegalArgumentException.class})
    public ApiResult<Void> handleParam(Exception e) {
        log.warn("参数错误: {}", e.getMessage());
        return ApiResult.fail(AiksErrorCode.PARAM_ERROR, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResult<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return ApiResult.fail(AiksErrorCode.PARAM_ERROR, "上传文件超过大小限制（单文件不超过20MB）");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNotFound(NoResourceFoundException e) {
        return ApiResult.fail(AiksErrorCode.NOT_FOUND, "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleOther(Exception e) {
        log.error("服务内部异常", e);
        return ApiResult.fail(AiksErrorCode.SERVICE_DEGRADED, null);
    }
}
