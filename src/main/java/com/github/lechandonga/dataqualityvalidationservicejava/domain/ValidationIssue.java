package com.github.lechandonga.dataqualityvalidationservicejava.domain;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleErrorType;

/**
 * 一条可区分原因的校验问题。errorType 仅在 code=RULE_EVALUATION_ERROR 时非空。
 */
public record ValidationIssue(
        String ruleName,
        String field,
        IssueCode code,
        String message,
        RuleErrorType errorType
) {
    public static ValidationIssue of(String ruleName, String field, IssueCode code, String message) {
        return new ValidationIssue(ruleName, field, code, message, null);
    }

    public static ValidationIssue ruleError(String ruleName, String field, RuleErrorType errorType, String message) {
        return new ValidationIssue(ruleName, field, IssueCode.RULE_EVALUATION_ERROR, message, errorType);
    }
}
