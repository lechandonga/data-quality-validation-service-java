package com.github.lechandonga.dataqualityvalidationservicejava.metrics;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.MetricSnapshot;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationReport;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 指标累积存储。每个完成的任务追加一份报告，按时间窗口与契约版本聚合。
 */
@Service
public class MetricsService {

    private final List<ValidationReport> reports = new CopyOnWriteArrayList<>();

    public void append(ValidationReport report) {
        reports.add(report);
    }

    public List<ValidationReport> reports(String datasetId, long fromMillis, long toMillis,
                                          Integer contractVersion) {
        return reports.stream()
                .filter(report -> report.datasetId().equals(datasetId))
                .filter(report -> report.finishedAtMillis() >= fromMillis
                        && report.finishedAtMillis() < toMillis)
                .filter(report -> contractVersion == null
                        || report.contractVersion() == contractVersion)
                .toList();
    }

    public MetricSnapshot aggregate(String datasetId, long fromMillis, long toMillis,
                                    Integer contractVersion) {
        List<ValidationReport> window = reports(datasetId, fromMillis, toMillis, contractVersion);
        long recordCount = 0;
        long passed = 0;
        long failed = 0;
        long ruleErrors = 0;
        for (ValidationReport report : window) {
            recordCount += report.totalCount();
            passed += report.passedCount();
            failed += report.failedCount();
            ruleErrors += report.ruleErrorCount();
        }
        double passRate = recordCount == 0 ? 1.0 : (double) passed / recordCount;
        double ruleErrorRate = recordCount == 0 ? 0.0 : (double) ruleErrors / recordCount;
        return new MetricSnapshot(datasetId, fromMillis, toMillis, contractVersion,
                window.size(), recordCount, passed, failed, ruleErrors,
                passRate, ruleErrorRate,
                window.stream().map(ValidationReport::taskId).toList());
    }
}
