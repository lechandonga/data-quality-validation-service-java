package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.model.Alert;
import com.github.lechandonga.dataqualityvalidationservicejava.model.AlertStatus;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationMetric;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：异常告警去重——抖动产生的重复告警在抑制窗口内收敛，恢复后自动 RESOLVED，
 * 抑制窗口过后再次异常才产生新告警；每条告警带触发依据。
 */
@BusinessArea("告警触发、去重收敛与恢复")
@ExtendWith(BusinessAreaLogger.class)
class AlertDedupTest {

    private static final String DS = "ds-alert";

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

    /** 在当前时钟时刻写入一条指标：passed/total 决定问题率。 */
    private void metric(int total, int passed, int ruleErrors) {
        ValidationResult result = new ValidationResult();
        result.setContractVersion(1);
        result.setRecordCount(total);
        result.setPassedRecordCount(passed);
        result.setViolatedRecordCount(total - passed - ruleErrors);
        for (int i = 0; i < total - passed - ruleErrors; i++) {
            result.getViolations().add(new com.github.lechandonga.dataqualityvalidationservicejava.model.Violation(
                    "r" + i, "f", com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType.RANGE,
                    "x", null));
        }
        for (int i = 0; i < ruleErrors; i++) {
            result.getRuleErrors().add(new com.github.lechandonga.dataqualityvalidationservicejava.model.RuleError(
                    "e" + i, "f", com.github.lechandonga.dataqualityvalidationservicejava.model.RuleErrorType.RULE_EXECUTION_ERROR,
                    "x"));
        }
        metrics.record("t-" + clock.millis() + "-" + System.nanoTime(), DS, clock.millis(), result);
    }

    @Test
    @DisplayName("首次异常 FIRING；抑制窗口内连续抖动只更新计数为 SUPPRESSED，不产生新告警")
    void repeatedJitterIsSuppressed() {
        clock.setMillis(-10_000L);
        metric(2, 2, 0);
        assertNull(alerts.evaluate(DS), "全量通过不应告警");
        clock.setMillis(0L);

        metric(2, 0, 0);
        Alert first = alerts.evaluate(DS);
        assertNotNull(first);
        assertEquals(AlertStatus.FIRING, first.getStatus());
        assertNotNull(first.getEvidence());
        assertTrue(first.getEvidence().contains("阈值"));

        clock.advanceMillis(500);
        metric(2, 0, 0);
        Alert second = alerts.evaluate(DS);
        assertEquals(AlertStatus.SUPPRESSED, second.getStatus());
        assertEquals(first.getAlertId(), second.getAlertId());

        clock.advanceMillis(499);
        metric(2, 0, 0);
        Alert third = alerts.evaluate(DS);
        assertEquals(AlertStatus.SUPPRESSED, third.getStatus());
        assertEquals(3, third.getTriggerCount());

        List<Alert> history = alerts.history(DS);
        assertEquals(1, history.size(), "抖动期间只能有一条活动告警");
    }

    @Test
    @DisplayName("指标恢复后活动告警 RESOLVED；抑制窗口过后再次异常产生新告警")
    void recoverThenReAlertAfterSuppressWindow() {
        metric(2, 0, 0);
        Alert firing = alerts.evaluate(DS);
        assertEquals(AlertStatus.FIRING, firing.getStatus());

        clock.advanceMillis(1_000);
        metric(2, 2, 0);
        assertNull(alerts.evaluate(DS));
        assertEquals(AlertStatus.RESOLVED, alerts.history(DS).get(0).getStatus());

        clock.advanceMillis(6_000);
        metric(2, 0, 0);
        Alert reFired = alerts.evaluate(DS);
        assertNotNull(reFired);
        assertEquals(AlertStatus.FIRING, reFired.getStatus());
        assertFalse(reFired.getAlertId().equals(firing.getAlertId()));
        assertEquals(2, alerts.history(DS).size());
    }

    @Test
    @DisplayName("规则错误计入问题率，可独立触发告警")
    void ruleErrorsCountAsProblems() {
        metric(4, 4, 3);
        Alert alert = alerts.evaluate(DS);
        assertNotNull(alert);
        ValidationMetric only = metrics.history(DS).get(0);
        assertTrue(only.getProblemRatio() >= 0.5d);
    }
}
