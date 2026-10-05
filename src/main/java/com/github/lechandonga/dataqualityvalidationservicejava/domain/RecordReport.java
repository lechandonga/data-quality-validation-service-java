package com.github.lechandonga.dataqualityvalidationservicejava.domain;

import java.util.List;

/**
 * 单条记录的校验结论。问题列表为空表示通过；规则异常同样判失败。
 */
public record RecordReport(String recordId, List<ValidationIssue> issues) {
    public RecordReport {
        issues = List.copyOf(issues);
    }

    public boolean passed() {
        return issues.isEmpty();
    }
}
