package com.epint.ztb.aiks.common;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class AiksException extends RuntimeException {

    private final AiksErrorCode errorCode;

    public AiksException(AiksErrorCode errorCode) {
        super(errorCode.getDefaultMsg());
        this.errorCode = errorCode;
    }

    public AiksException(AiksErrorCode errorCode, String msg) {
        super(msg);
        this.errorCode = errorCode;
    }
}
