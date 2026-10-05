package com.github.lechandonga.dataqualityvalidationservicejava.exception;

import com.github.lechandonga.dataqualityvalidationservicejava.model.ErrorCode;

import java.util.List;

/**
 * 服务统一业务异常：携带错误码与可对外展示的安全说明，
 * 不携带底层堆栈信息。
 */
public class ServiceException extends RuntimeException {
    private final ErrorCode errorCode;
    private final List<String> details;

    public ServiceException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ServiceException(ErrorCode errorCode, String message, List<String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<String> getDetails() {
        return details;
    }
}
