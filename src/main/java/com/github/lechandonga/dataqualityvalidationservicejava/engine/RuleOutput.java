package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.RuleError;
import com.github.lechandonga.dataqualityvalidationservicejava.model.RuleErrorType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Violation;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则输出收集器：违规与规则错误分别收集，互不混淆。
 */
public class RuleOutput {
    private final List<Violation> violations = new ArrayList<>();
    private final List<RuleError> errors = new ArrayList<>();

    public void violation(String recordId, String fieldName, ConstraintType type, String reason, Object actual) {
        violations.add(new Violation(recordId, fieldName, type, reason, actual));
    }

    public void error(String recordId, String fieldName, RuleErrorType type, String message) {
        errors.add(new RuleError(recordId, fieldName, type, message));
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public List<RuleError> getErrors() {
        return errors;
    }
}
