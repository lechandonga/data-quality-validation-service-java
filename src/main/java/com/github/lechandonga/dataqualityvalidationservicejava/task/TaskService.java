package com.github.lechandonga.dataqualityvalidationservicejava.task;

import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractService;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ErrorCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.IssueCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.RecordReport;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationException;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationReport;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationTask;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.metrics.AlertService;
import com.github.lechandonga.dataqualityvalidationservicejava.metrics.MetricsService;
import com.github.lechandonga.dataqualityvalidationservicejava.store.DatasetEntity;
import com.github.lechandonga.dataqualityvalidationservicejava.store.InMemoryStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 任务提交与执行：提交瞬间固化契约版本 + 记录快照（数据集锁内），
 * 之后异步执行；同一数据集并发任务互不污染。完成后累积指标并评估告警。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final InMemoryStore store;
    private final ContractService contractService;
    private final ValidationEngine engine;
    private final MetricsService metricsService;
    private final AlertService alertService;

    @Value("${dqvs.alert.default-window-millis:300000}")
    private long defaultWindowMillis = 300_000L;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "dqvs-validation-worker");
        thread.setDaemon(true);
        return thread;
    });

    public TaskService(InMemoryStore store, ContractService contractService,
                       ValidationEngine engine, MetricsService metricsService,
                       AlertService alertService) {
        this.store = store;
        this.contractService = contractService;
        this.engine = engine;
        this.metricsService = metricsService;
        this.alertService = alertService;
    }

    public ValidationTask submit(String datasetId) {
        DatasetEntity dataset = store.dataset(datasetId);
        if (dataset == null) {
            throw new ValidationException(ErrorCode.DATASET_NOT_FOUND,
                    "dataset '" + datasetId + "' not found");
        }
        dataset.lock().lock();
        ValidationTask task;
        try {
            Contract contract = dataset.currentContract();
            if (contract == null) {
                throw new ValidationException(ErrorCode.CONTRACT_NOT_FOUND,
                        "cannot submit task: no contract registered for dataset '"
                                + datasetId + "'");
            }
            task = new ValidationTask(UUID.randomUUID().toString(), datasetId, contract,
                    dataset.snapshotRecords(), System.currentTimeMillis());
            task.markRunning();
            store.putTask(task);
        } finally {
            dataset.lock().unlock();
        }

        executor.submit(() -> runTask(task));
        return task;
    }

    private void runTask(ValidationTask task) {
        try {
            Contract contract = contractService.requireVersion(task.datasetId(),
                    task.contractVersion());
            ValidationReport report = engine.validate(task.taskId(), contract,
                    task.recordSnapshot());
            task.markCompleted(report);
            metricsService.append(report);
            alertService.evaluate(task.datasetId(), defaultWindowMillis);
        } catch (RuntimeException ex) {
            log.error("unexpected failure running task {}", task.taskId(), ex);
        }
    }

    public ValidationTask requireTask(String taskId) {
        return store.task(taskId)
                .orElseThrow(() -> new ValidationException(ErrorCode.TASK_NOT_FOUND,
                        "task '" + taskId + "' not found"));
    }

    /**
     * 用任务提交时的契约版本与记录快照重新执行，比对结论摘要。
     */
    public ReplayResult replay(String taskId) {
        ValidationTask task = requireTask(taskId);
        if (task.report() == null) {
            throw new ValidationException(ErrorCode.BAD_REQUEST_BODY,
                    "task '" + taskId + "' is not completed yet");
        }
        Contract contract = contractService.requireVersion(task.datasetId(),
                task.contractVersion());
        if (!contract.checksum().equals(task.contractChecksum())) {
            throw new ValidationException(ErrorCode.INTERNAL_ERROR,
                    "historical contract checksum drift detected for task '" + taskId + "'");
        }
        ValidationReport replayed = engine.validate(task.taskId(), contract,
                task.recordSnapshot());
        String originalDigest = digest(task.report());
        String replayedDigest = digest(replayed);
        return new ReplayResult(taskId, contract.version(), contract.checksum(),
                originalDigest, replayedDigest, originalDigest.equals(replayedDigest));
    }

    /**
     * 仅对与时间、任务 ID 无关的问题集合做摘要，保证重放可比较。
     */
    static String digest(ValidationReport report) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (RecordReport recordReport : report.records()) {
                recordReport.issues().stream()
                        .map(issue -> String.join("|",
                                recordReport.recordId(),
                                String.valueOf(issue.ruleName()),
                                String.valueOf(issue.field()),
                                issue.code().name(),
                                issue.code() == IssueCode.RULE_EVALUATION_ERROR
                                        ? String.valueOf(issue.errorType()) : ""))
                        .sorted()
                        .forEach(line -> {
                            digest.update(line.getBytes(StandardCharsets.UTF_8));
                            digest.update((byte) '\n');
                        });
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("digest failure", ex);
        }
    }

    public void awaitCompletion(String taskId, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (requireTask(taskId).state().name().equals("COMPLETED")) {
                return;
            }
            Thread.sleep(10);
        }
        throw new IllegalStateException("task " + taskId + " did not complete in time");
    }

    public void awaitIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            boolean allDone = store.allTasks().stream()
                    .allMatch(task -> task.state().name().equals("COMPLETED"));
            if (allDone) {
                return;
            }
            Thread.sleep(10);
        }
        throw new IllegalStateException("tasks did not all complete in time");
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
