package com.github.lechandonga.dataqualityvalidationservicejava.domain;

/**
 * 告警类型：通过率相对基线下降；规则异常率相对基线抬升。
 */
public enum AlertType {
    PASS_RATE_DROP,
    RULE_ERROR_SPIKE
}
