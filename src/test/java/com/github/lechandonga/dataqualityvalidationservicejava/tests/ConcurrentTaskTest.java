package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.TaskStatus;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationMetric;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationTask;
import com.github.lechandonga.dataqualityvalidationservicejava.service.AlertProperties;
import com.github.lechandonga.dataqualityvalidationservicejava.service.AlertService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.DatasetService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.InMemoryStore;
import com.github.lechandonga.dataqualityvalidationservicejava.service.MetricService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.TaskService;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessArea;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessAreaLogger;
import com.github.lechandonga.dataqualityvalidationservicejava.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.field;
import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：任务并发提交——同一数据集的多个并发任务结果互不污染、指标逐条累积不丢失。
 */
@BusinessArea("并发任务提交与结果隔离")
@ExtendWith(BusinessAreaLogger.class)
class ConcurrentTaskTest {

    private ExecutorService pool;

    private TaskService newTaskService(InMemoryStore store, Clock clock, ExecutorService workers) {
        DatasetService datasetService = new DatasetService(store, clock);
        MetricService metricService = new MetricService(store);
        AlertService alertService = new AlertService(store, clock, new AlertProperties());
        return new TaskService(store, datasetService, metricService, alertService,
                new ValidationEngine(), workers, clock);
    }

    private com.github.lechandonga.dataqualityvalidationservicejava.model.Dataset setupDataset(
            DatasetService datasets, String id) {
        datasets.registerDataset(id);
        FieldConstraint code = field("code", FieldType.STRING);
        code.setRequired(true);
        code.setUnique(true);
        FieldConstraint age = field("age", FieldType.INTEGER);
        age.setMin(0.0);
        age.setMax(100.0);
        Contract contract = com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures
                .contract(code, age);
        datasets.registerContract(id, contract);
        datasets.loadRecords(id, List.of(
                record("r1", "code", "A", "age", 10),
                record("r2", "code", "B", "age", 20),
                record("r3", "code", "C", "age", 999),
                record("r4", "code", "A", "age", -1)));
        return datasets.getDataset(id);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("同一数据集并发提交 32 个任务：全部完成，结果完全一致，指标 32 条不丢失")
    void concurrentTasksDoNotPolluteEachOther() throws Exception {
        InMemoryStore store = new InMemoryStore();
        Clock clock = new MutableClock(5_000_000L);
        pool = Executors.newFixedThreadPool(8);
        DatasetService datasets = new DatasetService(store, clock);
        TaskService tasks = newTaskService(store, clock, pool);
        setupDataset(datasets, "ds-concurrent");

        int total = 32;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService submitters = Executors.newFixedThreadPool(total);
        Set<String> taskIds = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < total; i++) {
            submitters.submit(() -> {
                try {
                    start.await();
                    taskIds.add(tasks.submit("ds-concurrent", null).getTaskId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        submitters.shutdown();
        assertTrue(submitters.awaitTermination(10, TimeUnit.SECONDS));

        List<ValidationTask> completed = waitForCompletion(tasks, taskIds, total);

        assertEquals(total, completed.size());
        ValidationTask first = completed.get(0);
        for (ValidationTask task : completed) {
            assertEquals(TaskStatus.COMPLETED, task.getStatus());
            assertEquals(4, task.getResult().getRecordCount());
            assertEquals(first.getResult().getViolations().size(), task.getResult().getViolations().size());
            assertEquals(first.getResult().getRuleErrorCount(), task.getResult().getRuleErrorCount());
            assertEquals(first.getResult().getPassedRecordCount(), task.getResult().getPassedRecordCount());
        }
        List<ValidationMetric> metrics = new MetricService(store).history("ds-concurrent");
        assertEquals(total, metrics.size(), "并发完成的任务指标必须逐条累积，不能互相覆盖");
        assertEquals(total, metrics.stream().map(ValidationMetric::getTaskId).collect(java.util.stream.Collectors.toSet()).size(),
                "每条指标必须对应不同任务");
    }

    @Test
    @DisplayName("任务执行期间数据集被重新写入，已提交任务仍基于快照得出原结论")
    void snapshotSurvivesDatasetMutation() throws Exception {
        InMemoryStore store = new InMemoryStore();
        Clock clock = new MutableClock(5_000_000L);
        pool = Executors.newFixedThreadPool(4);
        DatasetService datasets = new DatasetService(store, clock);
        TaskService tasks = newTaskService(store, clock, pool);
        setupDataset(datasets, "ds-mutate");

        ValidationTask task = tasks.submit("ds-mutate", null);
        datasets.loadRecords("ds-mutate", List.of(
                record("x1", "code", "Z", "age", 50)));

        ValidationTask done = waitForCompletion(tasks, Set.of(task.getTaskId()), 1).get(0);
        assertEquals(4, done.getResult().getRecordCount(), "任务必须使用提交时的 4 条快照，而非新数据 1 条");
        assertEquals(4, done.getRecordSnapshot().size());
    }

    private List<ValidationTask> waitForCompletion(TaskService tasks, Set<String> ids, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            List<ValidationTask> done = ids.stream().map(tasks::getTask)
                    .filter(t -> t.getStatus() != TaskStatus.PENDING)
                    .toList();
            if (done.size() == expected) {
                return done;
            }
            Thread.sleep(20);
        }
        List<ValidationTask> done = ids.stream().map(tasks::getTask)
                .filter(t -> t.getStatus() != TaskStatus.PENDING).toList();
        assertEquals(expected, done.size(), "存在未完成任务");
        return done;
    }
}
