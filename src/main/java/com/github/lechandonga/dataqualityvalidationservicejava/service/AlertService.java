package com.github.lechandonga.dataqualityvalidationservicejava.service;

import com.github.lechandonga.dataqualityvalidationservicejava.model.Alert;
import com.github.lechandonga.dataqualityvalidationservicejava.model.AlertStatus;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;

/**
 * 告警服务：按时间窗口聚合问题率，同时满足“超过绝对阈值”和“相对基线放大”
 * 两个条件才告警；同一数据集在抑制窗口内的重复抖动被收敛为 SUPPRESSED，
 * 恢复正常后活动告警自动 RESOLVED。每条告警都记录可复核的触发依据。
 */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final InMemoryStore store;
    private final Clock clock;
    private final AlertProperties properties;

    public AlertService(InMemoryStore store, Clock clock, AlertProperties properties) {
        this.store = store;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * 基于最新指标评估一次告警，返回新产生/更新的告警；未触发时返回 null。
     */
    public Alert evaluate(String datasetId) {
        long now = clock.millis();
        long window = properties.getWindowMillis();

        List<ValidationMetric> currentWindow;
        double currentRatio;
        synchronized (store.metricsOf(datasetId)) {
            currentWindow = store.metricsOf(datasetId).stream()
                    .filter(m -> m.getTimestampMillis() > now - window && m.getTimestampMillis() <= now)
                    .toList();
            currentRatio = aggregate(currentWindow);
        }
        List<ValidationMetric> baselineWindow;
        double baselineRatio;
        synchronized (store.metricsOf(datasetId)) {
            baselineWindow = store.metricsOf(datasetId).stream()
                    .filter(m -> m.getTimestampMillis() > now - 2 * window && m.getTimestampMillis() <= now - window)
                    .toList();
            baselineRatio = baselineWindow.isEmpty() ? properties.getBaselineRatio() : aggregate(baselineWindow);
        }

        double relativeLimit = baselineRatio * properties.getBaselineMultiplier();
        boolean breached = currentRatio >= properties.getRatioThreshold() && currentRatio > relativeLimit;

        synchronized (store.alertsOf(datasetId)) {
            Alert active = activeAlert(datasetId);
            if (breached) {
                if (active != null && now - active.getLastTriggeredAtMillis() < properties.getSuppressMillis()) {
                    active.setStatus(AlertStatus.SUPPRESSED);
                    active.setTriggerCount(active.getTriggerCount() + 1);
                    active.setLastTriggeredAtMillis(now);
                    active.setCurrentProblemRatio(currentRatio);
                    log.info("告警去重收敛 dataset={} triggerCount={} ratio={}",
                            datasetId, active.getTriggerCount(), ratio(currentRatio));
                    return active;
                }
                Alert alert = newAlert(datasetId, now, currentRatio, baselineRatio,
                        relativeLimit, currentWindow.size(), baselineWindow.size(), window);
                if (active != null) {
                    active.setStatus(AlertStatus.RESOLVED);
                    active.setResolvedAtMillis(now);
                }
                store.alertsOf(datasetId).add(alert);
                log.warn("触发告警 dataset={} ratio={} baseline={} threshold={}>={} evidence={}",
                        datasetId, ratio(currentRatio), ratio(baselineRatio), ratio(currentRatio),
                        properties.getRatioThreshold(), alert.getEvidence());
                return alert;
            }
            if (active != null) {
                active.setStatus(AlertStatus.RESOLVED);
                active.setResolvedAtMillis(now);
                active.setCurrentProblemRatio(currentRatio);
                log.info("告警恢复 dataset={} ratio={}", datasetId, ratio(currentRatio));
            }
            return null;
        }
    }

    public List<Alert> history(String datasetId) {
        synchronized (store.alertsOf(datasetId)) {
            return List.copyOf(store.alertsOf(datasetId));
        }
    }

    private Alert activeAlert(String datasetId) {
        List<Alert> alerts = store.alertsOf(datasetId);
        for (int i = alerts.size() - 1; i >= 0; i--) {
            Alert alert = alerts.get(i);
            if (alert.getStatus() != AlertStatus.RESOLVED) {
                return alert;
            }
        }
        return null;
    }

    private Alert newAlert(String datasetId, long now, double currentRatio, double baselineRatio,
                           double relativeLimit, int currentSamples, int baselineSamples, long window) {
        Alert alert = new Alert();
        alert.setAlertId("alert-" + datasetId + "-" + now + "-" + System.identityHashCode(new Object()));
        alert.setDatasetId(datasetId);
        alert.setStatus(AlertStatus.FIRING);
        alert.setFirstTriggeredAtMillis(now);
        alert.setLastTriggeredAtMillis(now);
        alert.setTriggerCount(1);
        alert.setCurrentProblemRatio(currentRatio);
        alert.setBaselineProblemRatio(baselineRatio);
        alert.setRatioThreshold(properties.getRatioThreshold());
        alert.setWindowMillis(window);
        alert.setEvidence(String.format(
                "最近 %d ms 窗口内 %d 次校验的加权问题率 %s，超过绝对阈值 %s；"
                        + "基线窗口 %d 次校验问题率 %s，相对上限（基线×%s）= %s。",
                window, currentSamples, ratio(currentRatio), properties.getRatioThreshold(),
                baselineSamples, ratio(baselineRatio), properties.getBaselineMultiplier(), ratio(relativeLimit)));
        return alert;
    }

    private static double aggregate(List<ValidationMetric> metrics) {
        int records = metrics.stream().mapToInt(ValidationMetric::getRecordCount).sum();
        if (records == 0) {
            return 0.0;
        }
        double weighted = metrics.stream()
                .mapToDouble(m -> m.getProblemRatio() * m.getRecordCount())
                .sum();
        return weighted / records;
    }

    private static String ratio(double value) {
        return String.format("%.2f%%", value * 100);
    }
}
