package com.github.lechandonga.dataqualityvalidationservicejava.web;

import com.github.lechandonga.dataqualityvalidationservicejava.exception.ServiceException;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ErrorCode;
import com.github.lechandonga.dataqualityvalidationservicejava.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常归一化：业务异常映射错误码，未预期异常只返回通用提示，
 * 原始异常与堆栈仅记录到服务日志，绝不透传给调用方。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleService(ServiceException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_CONTRACT, INVALID_ARGUMENT, INVALID_RECORD, MALFORMED_REQUEST -> HttpStatus.BAD_REQUEST;
            case TASK_EXECUTION_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return org.springframework.http.ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("请求体无法解析：{}", ex.getMessage());
        return new ErrorResponse(ErrorCode.MALFORMED_REQUEST, "请求体不是合法 JSON 或字段类型错误",
                java.util.List.of());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex) {
        log.error("未预期的服务端异常", ex);
        return new ErrorResponse(ErrorCode.TASK_EXECUTION_ERROR, "服务内部错误，已归一化处理",
                java.util.List.of());
    }
}
