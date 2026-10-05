package com.github.lechandonga.dataqualityvalidationservicejava.store;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationTask;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 全内存存储。仅用于演示与确定性测试，重启即清空。
 */
@org.springframework.stereotype.Component
public class InMemoryStore {

    private final ConcurrentMap<String, DatasetEntity> datasets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ValidationTask> tasks = new ConcurrentHashMap<>();

    public DatasetEntity dataset(String datasetId) {
        return datasets.get(datasetId);
    }

    public DatasetEntity getOrCreateDataset(String datasetId) {
        return datasets.computeIfAbsent(datasetId, key -> new DatasetEntity());
    }

    public void putTask(ValidationTask task) {
        tasks.put(task.taskId(), task);
    }

    public Optional<ValidationTask> task(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    public java.util.Collection<ValidationTask> allTasks() {
        return tasks.values();
    }
}
