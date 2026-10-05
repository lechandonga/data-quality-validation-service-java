package com.github.lechandonga.dataqualityvalidationservicejava.web.dto;

import com.github.lechandonga.dataqualityvalidationservicejava.model.ErrorCode;

import java.util.List;

/**
 * 统一错误响应：只含错误码、安全说明与明细，不含原始异常信息。
 */
public class ErrorResponse {
    private final ErrorCode errorCode;
    private final String message;
    private final List<String> details;

    public ErrorResponse(ErrorCode errorCode, String message, List<String> details) {
        this.errorCode = errorCode;
        this.message = message;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getDetails() {
        return details;
    }
}
