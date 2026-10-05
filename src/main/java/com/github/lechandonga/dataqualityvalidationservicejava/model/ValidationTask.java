package com.github.lechandonga.dataqualityvalidationservicejava.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验任务。任务创建时对数据集记录做快照拷贝，
 * 因此同一数据集并发提交多个任务时，各任务的输入互不污染。
 */
public class ValidationTask {
    private final String taskId;
    private final String datasetId;
    private final int contractVersion;
    private final long submittedAtMillis;
    private final List<Record> recordSnapshot;
    private volatile TaskStatus status = TaskStatus.PENDING;
    private volatile ValidationResult result;
    private volatile String failureMessage;
    private volatile long completedAtMillis;

    public ValidationTask(String taskId, String datasetId, int contractVersion,
                          long submittedAtMillis, List<Record> recordSnapshot) {
        this.taskId = taskId;
        this.datasetId = datasetId;
        this.contractVersion = contractVersion;
        this.submittedAtMillis = submittedAtMillis;
        this.recordSnapshot = new ArrayList<>(recordSnapshot);
    }

    public String getTaskId() {
        return taskId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public int getContractVersion() {
        return contractVersion;
    }

    public long getSubmittedAtMillis() {
        return submittedAtMillis;
    }

    public List<Record> getRecordSnapshot() {
        return recordSnapshot;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public ValidationResult getResult() {
        return result;
    }

    public void setResult(ValidationResult result) {
        this.result = result;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public long getCompletedAtMillis() {
        return completedAtMillis;
    }

    public void setCompletedAtMillis(long completedAtMillis) {
        this.completedAtMillis = completedAtMillis;
    }
}
