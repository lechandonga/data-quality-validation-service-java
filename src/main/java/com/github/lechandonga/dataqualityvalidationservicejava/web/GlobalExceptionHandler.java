package com.github.lechandonga.dataqualityvalidationservicejava.web;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.ErrorCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 所有异常在此归一化：业务异常携带稳定码与明细；
 * 未预期异常返回脱敏 INTERNAL_ERROR，原始堆栈只进服务端日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(ValidationException ex) {
        return ResponseEntity.status(ex.errorCode().httpStatus()).body(Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", ex.errorCode().name(),
                "message", ex.getMessage(),
                "details", ex.details()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST_BODY.httpStatus()).body(Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", ErrorCode.BAD_REQUEST_BODY.name(),
                "message", "request body is missing or not valid JSON",
                "details", List.of()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Throwable ex) {
        log.error("unexpected error", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus()).body(Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", ErrorCode.INTERNAL_ERROR.name(),
                "message", "an unexpected internal error occurred",
                "details", List.of()));
    }
}
