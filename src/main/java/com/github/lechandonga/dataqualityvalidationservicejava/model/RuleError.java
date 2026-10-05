package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 单条规则执行失败的归一化描述。规则失败不等于记录通过，
 * 也不会中断整批校验，而是收集后随结果返回并计入指标。
 */
public class RuleError {
    private String recordId;
    private String fieldName;
    private RuleErrorType errorType;
    private String message;

    public RuleError() {
    }

    public RuleError(String recordId, String fieldName, RuleErrorType errorType, String message) {
        this.recordId = recordId;
        this.fieldName = fieldName;
        this.errorType = errorType;
        this.message = message;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public RuleErrorType getErrorType() {
        return errorType;
    }

    public String getMessage() {
        return message;
    }
}
