package com.github.lechandonga.dataqualityvalidationservicejava.domain;

import java.util.List;

/**
 * 业务层唯一可上抛到 API 的异常，携带稳定错误码与可展示的明细。
 */
public class ValidationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<String> details;

    public ValidationException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ValidationException(ErrorCode errorCode, String message, List<String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<String> details() {
        return details;
    }
}
