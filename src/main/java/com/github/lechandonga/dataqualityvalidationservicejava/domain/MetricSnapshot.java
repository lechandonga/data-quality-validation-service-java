package com.github.lechandonga.dataqualityvalidationservicejava.domain;

import java.util.List;

/**
 * 时间窗口内（可再按契约版本过滤）聚合的质量指标。
 */
public record MetricSnapshot(
        String datasetId,
        long fromMillis,
        long toMillis,
        Integer contractVersion,
        int taskCount,
        long recordCount,
        long passedCount,
        long failedCount,
        long ruleErrorCount,
        double passRate,
        double ruleErrorRate,
        List<String> taskIds
) {
    public MetricSnapshot {
        taskIds = List.copyOf(taskIds);
    }
}
