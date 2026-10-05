package com.github.lechandonga.dataqualityvalidationservicejava.engine;

/**
 * 规则内部异常的归一化类型。原始异常类与堆栈不对外透传。
 */
public enum RuleErrorType {
    RULE_DEFECT,
    RULE_CONFIG_ERROR,
    RULE_DATA_COERCION_ERROR,
    RULE_COMPUTATION_ERROR,
    RULE_UNSUPPORTED,
    RULE_INTERNAL_ERROR
}
