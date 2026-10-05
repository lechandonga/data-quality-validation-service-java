package com.github.lechandonga.dataqualityvalidationservicejava.domain;

/**
 * 单条校验问题的稳定分类码。一条记录可同时命中多个不同码，全部保留。
 */
public enum IssueCode {
    REQUIRED_MISSING,
    FIELD_TYPE_MISMATCH,
    VALUE_OUT_OF_RANGE,
    NOT_UNIQUE,
    FRESHNESS_VIOLATION,
    RULE_EVALUATION_ERROR
}
