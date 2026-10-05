package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 一条告警。每条告警携带触发依据（当前值、阈值、基线、对比窗口），
 * 相同数据集在抑制窗口内的重复触发只会更新计数而不产生新告警。
 */
public class Alert {
    private String alertId;
    private String datasetId;
    private AlertStatus status;
    private long firstTriggeredAtMillis;
    private long lastTriggeredAtMillis;
    private long resolvedAtMillis;
    private int triggerCount;
    private double currentProblemRatio;
    private double baselineProblemRatio;
    private double ratioThreshold;
    private long windowMillis;
    private String evidence;

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public long getFirstTriggeredAtMillis() {
        return firstTriggeredAtMillis;
    }

    public void setFirstTriggeredAtMillis(long firstTriggeredAtMillis) {
        this.firstTriggeredAtMillis = firstTriggeredAtMillis;
    }

    public long getLastTriggeredAtMillis() {
        return lastTriggeredAtMillis;
    }

    public void setLastTriggeredAtMillis(long lastTriggeredAtMillis) {
        this.lastTriggeredAtMillis = lastTriggeredAtMillis;
    }

    public long getResolvedAtMillis() {
        return resolvedAtMillis;
    }

    public void setResolvedAtMillis(long resolvedAtMillis) {
        this.resolvedAtMillis = resolvedAtMillis;
    }

    public int getTriggerCount() {
        return triggerCount;
    }

    public void setTriggerCount(int triggerCount) {
        this.triggerCount = triggerCount;
    }

    public double getCurrentProblemRatio() {
        return currentProblemRatio;
    }

    public void setCurrentProblemRatio(double currentProblemRatio) {
        this.currentProblemRatio = currentProblemRatio;
    }

    public double getBaselineProblemRatio() {
        return baselineProblemRatio;
    }

    public void setBaselineProblemRatio(double baselineProblemRatio) {
        this.baselineProblemRatio = baselineProblemRatio;
    }

    public double getRatioThreshold() {
        return ratioThreshold;
    }

    public void setRatioThreshold(double ratioThreshold) {
        this.ratioThreshold = ratioThreshold;
    }

    public long getWindowMillis() {
        return windowMillis;
    }

    public void setWindowMillis(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }
}
