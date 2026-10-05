package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractService;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.FieldSpecRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.dataset.DatasetService;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.Alert;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.AlertType;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.IssueCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.RecordReport;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationIssue;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationReport;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationTask;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.metrics.AlertService;
import com.github.lechandonga.dataqualityvalidationservicejava.metrics.MetricsService;
import com.github.lechandonga.dataqualityvalidationservicejava.store.InMemoryStore;
import com.github.lechandonga.dataqualityvalidationservicejava.task.ReplayResult;
import com.github.lechandonga.dataqualityvalidationservicejava.task.TaskService;
import com.github.lechandonga.dataqualityvalidationservicejava.tests.support.BusinessCoverageLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertAndReproducibilityTest {

    @RegisterExtension
    static final BusinessCoverageLogger COVERAGE = new BusinessCoverageLogger();

    private static final long WINDOW = 100_000L;

    private static final class MutableClock extends Clock {
        private long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        void setMillis(long value) {
            this.millis = value;
        }

        void advance(long delta) {
            this.millis += delta;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    private ValidationReport report(String taskId, String datasetId, int version,
                                    long finishedAt, int passed, int failed, int ruleErrors) {
        List<RecordReport> records = new ArrayList<>();
        for (int i = 0; i < passed; i++) {
            records.add(new RecordReport("p-" + i, List.of()));
        }
        for (int i = 0; i < failed; i++) {
            List<ValidationIssue> issues = new ArrayList<>();
            issues.add(ValidationIssue.of("REQUIRED", "id",
                    IssueCode.REQUIRED_MISSING, "missing"));
            if (ruleErrors > 0 && i < ruleErrors) {
                issues.add(ValidationIssue.ruleError("R", "id",
                        com.github.lechandonga.dataqualityvalidationservicejava.engine
                                .RuleErrorType.RULE_DEFECT, "rule failed"));
            }
            records.add(new RecordReport("f-" + i, issues));
        }
        return new ValidationReport(taskId, datasetId, version, "checksum-v" + version,
                finishedAt, records);
    }

    @Test
    @DisplayName("通过率相对基线骤降时告警，evidence 完整；抖动重复评估被去重收敛")
    void passRateDropAlertsAndDeduplicatesJitter() {
        MutableClock clock = new MutableClock(2_000_000L);
        MetricsService metrics = new MetricsService();
        AlertService alerts = new AlertService(metrics, clock).configure(0.10, 0.05, WINDOW);

        metrics.append(report("base-1", "ds", 1, 1_850_000L, 10, 0, 0));

        metrics.append(report("cur-1", "ds", 1, 1_950_000L, 1, 9, 0));

        List<Alert> first = alerts.evaluate("ds", WINDOW);
        Alert alert = first.stream().filter(a -> a.type() == AlertType.PASS_RATE_DROP)
                .findFirst().orElseThrow(() -> new AssertionError(first));
        assertEquals(AlertType.PASS_RATE_DROP, alert.type());
        assertTrue(alert.active());
        Map<String, Object> evidence = alert.evidence();
        assertEquals(1.0, evidence.get("baselinePassRate"));
        assertEquals(0.1, evidence.get("currentPassRate"));
        assertEquals("ds:PASS_RATE_DROP", alert.dedupeKey());
        assertTrue(evidence.get("taskIds").toString().contains("cur-1"));

        clock.advance(1_000L);
        metrics.append(report("cur-2", "ds", 1, 1_951_000L, 1, 9, 0));
        List<Alert> second = alerts.evaluate("ds", WINDOW);
        assertTrue(second.stream().allMatch(Alert::active));
        assertEquals(1, second.size());
        assertEquals(alert.id(), second.get(0).id(), "jitter must converge to same alert");
        assertEquals(1, second.get(0).suppressedCount());

        clock.advance(500L);
        List<Alert> third = alerts.evaluate("ds", WINDOW);
        assertEquals(1, third.size());
        assertEquals(2, third.get(0).suppressedCount());

        clock.advance(2_000L);
        metrics.append(report("cur-ok", "ds", 1, 1_999_000L, 10, 0, 0));
        clock.setMillis(2_000_000L + WINDOW + 10);
        metrics.append(report("cur-ok2", "ds", 1, clock.millis() - 1_000, 10, 0, 0));
        alerts.evaluate("ds", WINDOW);
        Alert stored = alerts.listAlerts("ds").get(0);
        assertFalse(stored.active(), "recovery deactivates the alert");
    }

    @Test
    @DisplayName("超过抑制窗口后再次劣化会产生新告警，旧告警失活")
    void realertsAfterDedupeWindow() {
        MutableClock clock = new MutableClock(3_000_000L);
        MetricsService metrics = new MetricsService();
        AlertService alerts = new AlertService(metrics, clock).configure(0.10, 0.05, WINDOW);

        metrics.append(report("b1", "ds", 1, 2_850_000L, 10, 0, 0));
        metrics.append(report("c1", "ds", 1, 2_950_000L, 1, 9, 0));
        Alert first = alerts.evaluate("ds", WINDOW).stream()
                .filter(alert -> alert.type() == AlertType.PASS_RATE_DROP)
                .findFirst().orElseThrow();

        clock.setMillis(3_000_000L + WINDOW + 1);
        metrics.append(report("c2", "ds", 1, clock.millis() - 1_000L, 1, 9, 0));
        metrics.append(report("b2", "ds", 1, clock.millis() - WINDOW - 1_000L, 10, 0, 0));
        List<Alert> again = alerts.evaluate("ds", WINDOW);
        assertEquals(1, again.size());
        Alert second = again.get(0);
        assertFalse(second.id().equals(first.id()));
        assertTrue(second.active());
        assertFalse(alerts.listAlerts("ds").stream()
                .filter(alert -> alert.id().equals(first.id()))
                .findFirst().orElseThrow().active());
    }

    @Test
    @DisplayName("规则异常率相对基线抬升触发 RULE_ERROR_SPIKE")
    void ruleErrorSpikeAlert() {
        MutableClock clock = new MutableClock(4_000_000L);
        MetricsService metrics = new MetricsService();
        AlertService alerts = new AlertService(metrics, clock).configure(0.10, 0.05, WINDOW);

        metrics.append(report("b1", "ds", 1, 3_850_000L, 100, 0, 0));
        metrics.append(report("c1", "ds", 1, 3_950_000L, 90, 10, 10));

        metrics.append(report("b2", "ds", 1, 3_840_000L, 100, 0, 0));
        List<Alert> emitted = alerts.evaluate("ds", WINDOW);
        Alert spike = emitted.stream().filter(a -> a.type() == AlertType.RULE_ERROR_SPIKE)
                .findFirst().orElseThrow(() -> new AssertionError(emitted));
        assertEquals(0.1, spike.evidence().get("currentRuleErrorRate"));
    }

    @Test
    @DisplayName("契约升版后历史任务用旧契约重放结论一致，指标可按版本分别查询")
    void historicalConclusionsReproduceAcrossContractVersions() throws Exception {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(5_000_000L);
        ContractService contracts = new ContractService(store, clock);
        DatasetService datasets = new DatasetService(store);
        MetricsService metrics = new MetricsService();
        AlertService alerts = new AlertService(metrics, clock).configure(0.10, 0.05, WINDOW);
        ValidationEngine engine = new ValidationEngine(clock);
        TaskService tasks = new TaskService(store, contracts, engine, metrics, alerts);

        contracts.register(new ContractRequest("people", List.of(
                new FieldSpecRequest("id", "STRING", true, null, null, true, false, null),
                new FieldSpecRequest("age", "INTEGER", false, 0.0, 120.0, false, false, null))));
        datasets.replaceRecords("people", List.of(
                Map.of("id", "p1", "age", 200),
                Map.of("id", "p2", "age", 30)));

        ValidationTask first = tasks.submit("people");
        tasks.awaitCompletion(first.taskId(), 10_000);
        assertEquals(1, first.report().failedCount());

        contracts.register(new ContractRequest("people", List.of(
                new FieldSpecRequest("id", "STRING", true, null, null, true, false, null),
                new FieldSpecRequest("age", "INTEGER", false, 0.0, 200.0, false, false, null),
                new FieldSpecRequest("city", "STRING", true, null, null, false, false, null))));
        datasets.replaceRecords("people", List.of(
                Map.of("id", "p1", "age", 200, "city", "SH"),
                Map.of("id", "p2", "age", 30, "city", "BJ")));

        ValidationTask second = tasks.submit("people");
        tasks.awaitCompletion(second.taskId(), 10_000);
        assertEquals(2, second.report().passedCount());
        assertEquals(2, second.report().contractVersion());

        ReplayResult replayFirst = tasks.replay(first.taskId());
        assertTrue(replayFirst.identical(), replayFirst.toString());
        assertEquals(1, replayFirst.contractVersion());
        assertEquals(first.contractChecksum(), replayFirst.contractChecksum());

        ReplayResult replaySecond = tasks.replay(second.taskId());
        assertTrue(replaySecond.identical());

        Contract historical = contracts.requireVersion("people", 1);
        assertEquals(2, historical.fields().size());
        var v1Metrics = metrics.aggregate("people", 0, Long.MAX_VALUE, 1);
        var v2Metrics = metrics.aggregate("people", 0, Long.MAX_VALUE, 2);
        assertEquals(1, v1Metrics.taskCount());
        assertEquals(1, v2Metrics.taskCount());
        assertEquals(0.5, v1Metrics.passRate(), 0.001);
        assertEquals(1.0, v2Metrics.passRate(), 0.001);

        tasks.shutdown();
    }
}
