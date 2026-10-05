package com.github.lechandonga.dataqualityvalidationservicejava.metrics;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.Alert;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.AlertType;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.MetricSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 当前窗口与等长基线窗口对比决定是否告警；
 * 同一 dedupeKey 在抑制窗口内的重复触发收敛为 suppressedCount；
 * 数据恢复后告警失活，再次劣化可产生新告警。每条告警 evidence 给出完整触发依据。
 */
@Service
public class AlertService {

    private final MetricsService metricsService;
    private final Clock clock;

    @Value("${dqvs.alert.pass-rate-drop-threshold:0.10}")
    private double passRateDropThreshold = 0.10;

    @Value("${dqvs.alert.rule-error-spike-threshold:0.05}")
    private double ruleErrorSpikeThreshold = 0.05;

    @Value("${dqvs.alert.dedupe-window-millis:300000}")
    private long dedupeWindowMillis = 300_000L;

    private final Map<String, List<Alert>> alertsByDataset = new LinkedHashMap<>();

    public AlertService(MetricsService metricsService, Clock clock) {
        this.metricsService = metricsService;
        this.clock = clock;
    }

    /** 测试与精细场景使用：显式设置阈值与抑制窗口。 */
    public AlertService configure(double passRateDropThreshold, double ruleErrorSpikeThreshold,
                                  long dedupeWindowMillis) {
        this.passRateDropThreshold = passRateDropThreshold;
        this.ruleErrorSpikeThreshold = ruleErrorSpikeThreshold;
        this.dedupeWindowMillis = dedupeWindowMillis;
        return this;
    }

    public synchronized List<Alert> evaluate(String datasetId, long windowMillis) {
        long now = clock.millis();
        long currentFrom = now - windowMillis;
        long baselineFrom = now - 2 * windowMillis;

        MetricSnapshot current = metricsService.aggregate(datasetId, currentFrom, now, null);
        MetricSnapshot baseline = metricsService.aggregate(datasetId, baselineFrom, currentFrom, null);

        List<Alert> emitted = new ArrayList<>();
        double passRateDrop = baseline.passRate() - current.passRate();
        double errorSpike = current.ruleErrorRate() - baseline.ruleErrorRate();

        // 先依据本窗口健康度让旧告警失活，再登记新告警，避免把新告警误判为恢复。
        recoverIfHealthy(datasetId, now, AlertType.PASS_RATE_DROP,
                current.recordCount() > 0 && passRateDrop <= passRateDropThreshold);
        recoverIfHealthy(datasetId, now, AlertType.RULE_ERROR_SPIKE,
                current.recordCount() > 0 && errorSpike <= ruleErrorSpikeThreshold);

        if (current.recordCount() > 0 && passRateDrop > passRateDropThreshold) {
            emitted.add(register(datasetId, AlertType.PASS_RATE_DROP, now, Map.of(
                    "windowMillis", windowMillis,
                    "currentWindowStartMillis", currentFrom,
                    "baselineWindowStartMillis", baselineFrom,
                    "baselinePassRate", round(baseline.passRate()),
                    "currentPassRate", round(current.passRate()),
                    "drop", round(passRateDrop),
                    "threshold", passRateDropThreshold,
                    "currentRecordCount", current.recordCount(),
                    "taskIds", current.taskIds())));
        }

        if (current.recordCount() > 0 && errorSpike > ruleErrorSpikeThreshold) {
            emitted.add(register(datasetId, AlertType.RULE_ERROR_SPIKE, now, Map.of(
                    "windowMillis", windowMillis,
                    "currentWindowStartMillis", currentFrom,
                    "baselineWindowStartMillis", baselineFrom,
                    "baselineRuleErrorRate", round(baseline.ruleErrorRate()),
                    "currentRuleErrorRate", round(current.ruleErrorRate()),
                    "increase", round(errorSpike),
                    "threshold", ruleErrorSpikeThreshold,
                    "currentRuleErrorCount", current.ruleErrorCount(),
                    "taskIds", current.taskIds())));
        }

        return emitted;
    }

    private Alert register(String datasetId, AlertType type, long now, Map<String, Object> evidence) {
        String dedupeKey = datasetId + ":" + type.name();
        List<Alert> alerts = alertsByDataset.computeIfAbsent(datasetId, key -> new ArrayList<>());
        for (int i = 0; i < alerts.size(); i++) {
            Alert existing = alerts.get(i);
            if (!existing.dedupeKey().equals(dedupeKey)) {
                continue;
            }
            if (existing.active() && now - existing.createdAtMillis() < dedupeWindowMillis) {
                Alert suppressed = existing.withSuppressed(now);
                alerts.set(i, suppressed);
                return suppressed;
            }
            if (existing.active()) {
                alerts.set(i, existing.deactivated(now));
            }
        }
        Alert alert = new Alert(UUID.randomUUID().toString(), datasetId, type, dedupeKey,
                now, now, 0, true, evidence);
        alerts.add(alert);
        return alert;
    }

    private void recoverIfHealthy(String datasetId, long now, AlertType type, boolean healthy) {
        if (!healthy) {
            return;
        }
        List<Alert> alerts = alertsByDataset.get(datasetId);
        if (alerts == null) {
            return;
        }
        for (int i = 0; i < alerts.size(); i++) {
            Alert alert = alerts.get(i);
            if (alert.active() && alert.type() == type) {
                alerts.set(i, alert.deactivated(now));
            }
        }
    }

    public synchronized List<Alert> listAlerts(String datasetId) {
        List<Alert> alerts = alertsByDataset.get(datasetId);
        return alerts == null ? List.of() : List.copyOf(alerts);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }
}
