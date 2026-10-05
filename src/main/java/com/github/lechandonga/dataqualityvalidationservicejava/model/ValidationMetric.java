package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 每次校验完成后沉淀的一条指标。指标按数据集维度累积，
 * 供时间窗口聚合与基线对比使用。
 */
public class ValidationMetric {
    private String taskId;
    private String datasetId;
    private int contractVersion;
    private long timestampMillis;
    private int recordCount;
    private int passedRecordCount;
    private int violationCount;
    private int ruleErrorCount;

    public ValidationMetric() {
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public int getContractVersion() {
        return contractVersion;
    }

    public void setContractVersion(int contractVersion) {
        this.contractVersion = contractVersion;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public void setTimestampMillis(long timestampMillis) {
        this.timestampMillis = timestampMillis;
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

    public int getViolationCount() {
        return violationCount;
    }

    public void setViolationCount(int violationCount) {
        this.violationCount = violationCount;
    }

    public int getRuleErrorCount() {
        return ruleErrorCount;
    }

    public void setRuleErrorCount(int ruleErrorCount) {
        this.ruleErrorCount = ruleErrorCount;
    }

    /**
     * 问题率 = （违规记录数 + 规则错误数）/ 校验记录数；规则错误计入问题，绝不计为通过。
     */
    public double getProblemRatio() {
        if (recordCount == 0) {
            return 0.0;
        }
        int problemRecords = recordCount - passedRecordCount;
        return (double) (problemRecords + ruleErrorCount) / recordCount;
    }
}
