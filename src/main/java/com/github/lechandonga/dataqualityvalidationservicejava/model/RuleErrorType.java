package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 规则执行异常归一化后的错误类型。
 * 原始异常（含堆栈与底层信息）不会透传给调用方，只保留该类型与脱敏说明。
 */
public enum RuleErrorType {
    /** 规则内部计算错误（NPE、除零、类转换等） */
    RULE_EXECUTION_ERROR,
    /** 规则读取到的值类型无法解释 */
    TYPE_COERCION_ERROR,
    /** 规则依赖的字段不存在 */
    MISSING_FIELD_ERROR,
    /** 规则超时未完成 */
    TIMEOUT_ERROR
}
