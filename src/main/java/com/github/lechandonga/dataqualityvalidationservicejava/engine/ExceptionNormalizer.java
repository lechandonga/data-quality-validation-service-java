package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import java.util.IllegalFormatException;
import java.util.UnknownFormatConversionException;

/**
 * 把规则内部抛出的异常归一化为稳定错误类型。
 * 原始异常类型、消息与堆栈仅写入服务端日志，绝不直接透传给调用方。
 */
public final class ExceptionNormalizer {

    private ExceptionNormalizer() {
    }

    public static RuleErrorType normalize(Throwable thrown) {
        if (thrown instanceof NullPointerException
                || thrown instanceof IndexOutOfBoundsException
                || thrown instanceof ClassCastException) {
            return RuleErrorType.RULE_DEFECT;
        }
        if (thrown instanceof IllegalStateException
                || thrown instanceof IllegalFormatException
                || thrown instanceof UnknownFormatConversionException) {
            return RuleErrorType.RULE_CONFIG_ERROR;
        }
        if (thrown instanceof NumberFormatException
                || thrown instanceof IllegalArgumentException) {
            return RuleErrorType.RULE_DATA_COERCION_ERROR;
        }
        if (thrown instanceof ArithmeticException) {
            return RuleErrorType.RULE_COMPUTATION_ERROR;
        }
        if (thrown instanceof UnsupportedOperationException) {
            return RuleErrorType.RULE_UNSUPPORTED;
        }
        return RuleErrorType.RULE_INTERNAL_ERROR;
    }

    /**
     * 面向调用方的脱敏描述，不包含原始异常消息。
     */
    public static String safeMessage(RuleErrorType type, String ruleName, String field) {
        return "rule '%s' on field '%s' failed with %s; record marked failed and batch continues"
                .formatted(ruleName, field, type.name());
    }
}
