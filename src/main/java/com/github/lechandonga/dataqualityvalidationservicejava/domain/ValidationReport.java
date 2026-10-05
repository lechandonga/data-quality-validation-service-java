package com.github.lechandonga.dataqualityvalidationservicejava.domain;

import java.util.List;

/**
 * 一个校验任务的完整结论快照，含契约版本与 checksum 以支持历史复现。
 */
public record ValidationReport(
        String taskId,
        String datasetId,
        int contractVersion,
        String contractChecksum,
        long finishedAtMillis,
        List<RecordReport> records
) {
    public ValidationReport {
        records = List.copyOf(records);
    }

    public long totalCount() {
        return records.size();
    }

    public long passedCount() {
        return records.stream().filter(RecordReport::passed).count();
    }

    public long failedCount() {
        return totalCount() - passedCount();
    }

    public long ruleErrorCount() {
        return records.stream()
                .flatMap(report -> report.issues().stream())
                .filter(issue -> issue.code() == IssueCode.RULE_EVALUATION_ERROR)
                .count();
    }
}
