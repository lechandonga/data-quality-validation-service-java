package com.github.lechandonga.dataqualityvalidationservicejava.domain;

/**
 * 对外稳定错误码及对应 HTTP 状态。
 */
public enum ErrorCode {
    MALFORMED_CONTRACT(400),
    DUPLICATE_CONTRACT(400),
    MALFORMED_DATASET(400),
    CONTRACT_NOT_FOUND(404),
    DATASET_NOT_FOUND(404),
    TASK_NOT_FOUND(404),
    BAD_REQUEST_BODY(400),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
