package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractService;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.FieldSpecRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.dataset.DatasetService;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationReport;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationTask;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.metrics.AlertService;
import com.github.lechandonga.dataqualityvalidationservicejava.metrics.MetricsService;
import com.github.lechandonga.dataqualityvalidationservicejava.store.InMemoryStore;
import com.github.lechandonga.dataqualityvalidationservicejava.task.TaskService;
import com.github.lechandonga.dataqualityvalidationservicejava.tests.support.BusinessCoverageLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrentTaskTest {

    @RegisterExtension
    static final BusinessCoverageLogger COVERAGE = new BusinessCoverageLogger();

    private static final int TASK_COUNT = 12;

    @Test
    @DisplayName("同一数据集并发提交：快照隔离，任务结果不被中途数据替换污染")
    void concurrentSubmissionsAreIsolated() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ContractService contractService = new ContractService(store, Clock.systemUTC());
        DatasetService datasetService = new DatasetService(store);
        MetricsService metricsService = new MetricsService();
        AlertService alertService = new AlertService(metricsService, Clock.systemUTC());
        TaskService taskService = new TaskService(store, contractService,
                new ValidationEngine(Clock.systemUTC()), metricsService, alertService);

        contractService.register(new ContractRequest("items", List.of(
                new FieldSpecRequest("id", "STRING", true, null, null, true, false, null),
                new FieldSpecRequest("qty", "INTEGER", true, 1.0, 5.0, false, false, null))));

        List<Map<String, Object>> initialRecords = List.of(
                Map.of("id", "i-1", "qty", 2),
                Map.of("id", "i-2", "qty", 4));
        datasetService.replaceRecords("items", initialRecords);

        CountDownLatch ready = new CountDownLatch(TASK_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(TASK_COUNT);
        List<String> taskIds = new ArrayList<>();

        for (int i = 0; i < TASK_COUNT; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    ValidationTask task = taskService.submit("items");
                    synchronized (taskIds) {
                        taskIds.add(task.taskId());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            });
        }
        ready.await();
        start.countDown();

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        datasetService.replaceRecords("items", List.of(
                Map.of("id", "i-9", "qty", 999)));

        taskService.awaitIdle(10_000);

        assertEquals(TASK_COUNT, taskIds.size());
        for (String taskId : taskIds) {
            ValidationReport report = taskService.requireTask(taskId).report();
            assertEquals(2, report.totalCount(), taskId + " must see the submission snapshot");
            assertEquals(2, report.passedCount(), taskId + " snapshot records were all valid");
        }

        ValidationTask later = taskService.submit("items");
        taskService.awaitCompletion(later.taskId(), 10_000);
        assertEquals(1, later.report().totalCount());
        assertEquals(0, later.report().passedCount(), "post-replace task must see new bad data");

        taskService.shutdown();
    }

    @Test
    @DisplayName("并发完成的任务指标全部累积，无丢失、无串扰")
    void concurrentMetricsAreFullyAccumulated() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ContractService contractService = new ContractService(store, Clock.systemUTC());
        DatasetService datasetService = new DatasetService(store);
        MetricsService metricsService = new MetricsService();
        AlertService alertService = new AlertService(metricsService, Clock.systemUTC());
        TaskService taskService = new TaskService(store, contractService,
                new ValidationEngine(Clock.systemUTC()), metricsService, alertService);

        contractService.register(new ContractRequest("items", List.of(
                new FieldSpecRequest("id", "STRING", true, null, null, false, false, null))));
        datasetService.replaceRecords("items", List.of(
                Map.of("id", "a"), Map.of("id", "b"), Map.of("id", "c")));

        ExecutorService pool = Executors.newFixedThreadPool(TASK_COUNT);
        for (int i = 0; i < TASK_COUNT; i++) {
            pool.submit(() -> taskService.submit("items"));
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        taskService.awaitIdle(10_000);

        var snapshot = metricsService.aggregate("items", 0, System.currentTimeMillis() + 1, null);
        assertEquals(TASK_COUNT, snapshot.taskCount());
        assertEquals(3L * TASK_COUNT, snapshot.recordCount());
        assertEquals(3L * TASK_COUNT, snapshot.passedCount());

        taskService.shutdown();
    }
}
