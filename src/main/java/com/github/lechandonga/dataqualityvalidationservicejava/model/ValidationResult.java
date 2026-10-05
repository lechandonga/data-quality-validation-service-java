package com.github.lechandonga.dataqualityvalidationservicejava.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次校验执行的完整结论：违规与规则错误分列、计数汇总，
 * 并冻结所使用的契约版本，保证历史结论可复现。
 */
public class ValidationResult {
    private int contractVersion;
    private int recordCount;
    private int passedRecordCount;
    private int violatedRecordCount;
    private final List<Violation> violations = new ArrayList<>();
    private final List<RuleError> ruleErrors = new ArrayList<>();

    public int getContractVersion() {
        return contractVersion;
    }

    public void setContractVersion(int contractVersion) {
        this.contractVersion = contractVersion;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }

    public int getPassedRecordCount() {
        return passedRecordCount;
    }

    public void setPassedRecordCount(int passedRecordCount) {
        this.passedRecordCount = passedRecordCount;
    }

    public int getViolatedRecordCount() {
        return violatedRecordCount;
    }

    public void setViolatedRecordCount(int violatedRecordCount) {
        this.violatedRecordCount = violatedRecordCount;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public List<RuleError> getRuleErrors() {
        return ruleErrors;
    }

    public int getRuleErrorCount() {
        return ruleErrors.size();
    }

    public boolean isClean() {
        return violations.isEmpty() && ruleErrors.isEmpty();
    }
}
