package com.github.lechandonga.dataqualityvalidationservicejava.service;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.exception.ServiceException;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Dataset;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ErrorCode;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;
import com.github.lechandonga.dataqualityvalidationservicejava.model.TaskStatus;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationResult;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationTask;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * 校验任务服务：提交时冻结契约版本与数据快照，异步执行；
 * 同一数据集的并发任务各自持有独立快照与独立规则实例，结果互不污染。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final InMemoryStore store;
    private final DatasetService datasetService;
    private final MetricService metricService;
    private final AlertService alertService;
    private final ValidationEngine engine;
    private final ExecutorService executor;
    private final Clock clock;

    public TaskService(InMemoryStore store, DatasetService datasetService, MetricService metricService,
                       AlertService alertService, ValidationEngine engine,
                       ExecutorService taskExecutor, Clock clock) {
        this.store = store;
        this.datasetService = datasetService;
        this.metricService = metricService;
        this.alertService = alertService;
        this.engine = engine;
        this.executor = taskExecutor;
        this.clock = clock;
    }

    public ValidationTask submit(String datasetId, Integer contractVersion) {
        Dataset dataset = datasetService.getDataset(datasetId);
        Contract contract;
        List<Record> snapshot;
        synchronized (dataset) {
            contract = datasetService.getContractVersion(datasetId, contractVersion);
            if (dataset.getRecords().isEmpty()) {
                throw new ServiceException(ErrorCode.INVALID_ARGUMENT,
                        "数据集没有可校验的数据：" + datasetId);
            }
            snapshot = InMemoryStore.snapshotRecords(dataset.getRecords());
        }
        String taskId = "task-" + UUID.randomUUID();
        ValidationTask task = new ValidationTask(taskId, datasetId, contract.getVersion(), clock.millis(), snapshot);
        store.tasks().put(taskId, task);
        executor.submit(() -> run(task, InMemoryStore.snapshotContract(contract)));
        log.info("提交校验任务 taskId={} dataset={} contractVersion={} records={}",
                taskId, datasetId, contract.getVersion(), snapshot.size());
        return task;
    }

    private void run(ValidationTask task, Contract contractSnapshot) {
        try {
            ValidationResult result = engine.validate(task.getRecordSnapshot(), contractSnapshot, clock.millis());
            task.setResult(result);
            task.setCompletedAtMillis(clock.millis());
            task.setStatus(TaskStatus.COMPLETED);
            metricService.record(task.getTaskId(), task.getDatasetId(), task.getCompletedAtMillis(), result);
            alertService.evaluate(task.getDatasetId());
            log.info("校验任务完成 taskId={} records={} violations={} ruleErrors={}",
                    task.getTaskId(), result.getRecordCount(),
                    result.getViolations().size(), result.getRuleErrorCount());
        } catch (Throwable t) {
            task.setStatus(TaskStatus.FAILED);
            task.setCompletedAtMillis(clock.millis());
            task.setFailureMessage("任务执行失败，错误已归一化处理，请检查服务日志");
            log.error("校验任务发生不可恢复错误 taskId={}", task.getTaskId(), t);
        }
    }

    public ValidationTask getTask(String taskId) {
        ValidationTask task = store.tasks().get(taskId);
        if (task == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "任务不存在：" + taskId);
        }
        return task;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
