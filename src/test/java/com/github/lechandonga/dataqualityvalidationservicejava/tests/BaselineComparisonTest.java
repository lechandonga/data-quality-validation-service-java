package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.model.Alert;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationResult;
import com.github.lechandonga.dataqualityvalidationservicejava.service.AlertProperties;
import com.github.lechandonga.dataqualityvalidationservicejava.service.AlertService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.InMemoryStore;
import com.github.lechandonga.dataqualityvalidationservicejava.service.MetricService;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessArea;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessAreaLogger;
import com.github.lechandonga.dataqualityvalidationservicejava.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 业务：基线对比——必须同时超过绝对阈值与“基线×倍数”才告警，
 * 避免绝对阈值虚高和基线抖动误报。
 */
@BusinessArea("时间窗口与基线对比")
@ExtendWith(BusinessAreaLogger.class)
class BaselineComparisonTest {

    private static final String DS = "ds-baseline";

    private InMemoryStore store;
    private MutableClock clock;
    private MetricService metrics;
    private AlertService alerts;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        clock = new MutableClock(0L);
        metrics = new MetricService(store);
        AlertProperties props = new AlertProperties();
        props.setWindowMillis(1_000L);
        props.setSuppressMillis(5_000L);
        props.setRatioThreshold(0.5d);
        props.setBaselineRatio(0.1d);
        props.setBaselineMultiplier(2.0d);
        alerts = new AlertService(store, clock, props);
    }

    private void metricAt(long timestamp, int total, int passed) {
        ValidationResult result = new ValidationResult();
        result.setContractVersion(1);
        result.setRecordCount(total);
        result.setPassedRecordCount(passed);
        result.setViolatedRecordCount(total - passed);
        for (int i = 0; i < total - passed; i++) {
            result.getViolations().add(new com.github.lechandonga.dataqualityvalidationservicejava.model.Violation(
                    "r" + i, "f", com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType.RANGE,
                    "x", null));
        }
        metrics.record("t-" + timestamp + "-" + System.nanoTime(), DS, timestamp, result);
    }

    @Test
    @DisplayName("问题率低于绝对阈值时不告警")
    void belowAbsoluteThresholdNoAlert() {
        metricAt(0, 10, 8);
        assertNull(alerts.evaluate(DS));
    }

    @Test
    @DisplayName("超过绝对阈值但未超过基线窗口问题率的 2 倍时不告警")
    void highBaselineSuppressesAlert() {
        metricAt(-1_500L, 10, 5);
        metricAt(0, 10, 4);
        assertNull(alerts.evaluate(DS),
                "当前 60% 虽过绝对阈值 50%，但基线 50%×2=100%，不应告警");
    }

    @Test
    @DisplayName("同时超过绝对阈值与基线倍数时告警，证据写明基线对比")
    void breachBothConditionsAlerts() {
        metricAt(-1_500L, 10, 9);
        metricAt(0, 10, 1);
        Alert alert = alerts.evaluate(DS);
        assertNotNull(alert);
        assertNotNull(alert.getEvidence());
        org.junit.jupiter.api.Assertions.assertTrue(alert.getEvidence().contains("基线"));
    }
}
