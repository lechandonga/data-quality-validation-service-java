package com.github.lechandonga.dataqualityvalidationservicejava.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 校验任务。提交时固化契约版本与记录快照，保证并发任务互不污染。
 */
public class ValidationTask {

    private final String taskId;
    private final String datasetId;
    private final int contractVersion;
    private final String contractChecksum;
    private final List<Map<String, Object>> recordSnapshot;
    private final long submittedAtMillis;

    private volatile TaskState state = TaskState.PENDING;
    private volatile ValidationReport report;

    public ValidationTask(String taskId, String datasetId, Contract contract,
                         List<Map<String, Object>> recordSnapshot, long submittedAtMillis) {
        this.taskId = taskId;
        this.datasetId = datasetId;
        this.contractVersion = contract.version();
        this.contractChecksum = contract.checksum();
        this.recordSnapshot = new ArrayList<>(recordSnapshot);
        this.submittedAtMillis = submittedAtMillis;
    }

    public String taskId() {
        return taskId;
    }

    public String datasetId() {
        return datasetId;
    }

    public int contractVersion() {
        return contractVersion;
    }

    public String contractChecksum() {
        return contractChecksum;
    }

    public List<Map<String, Object>> recordSnapshot() {
        return recordSnapshot;
    }

    public long submittedAtMillis() {
        return submittedAtMillis;
    }

    public TaskState state() {
        return state;
    }

    public ValidationReport report() {
        return report;
    }

    public void markRunning() {
        this.state = TaskState.RUNNING;
    }

    public void markCompleted(ValidationReport report) {
        this.report = report;
        this.state = TaskState.COMPLETED;
    }
}
