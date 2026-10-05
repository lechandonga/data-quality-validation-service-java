package com.github.lechandonga.dataqualityvalidationservicejava.service;

import com.github.lechandonga.dataqualityvalidationservicejava.model.Alert;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Dataset;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationMetric;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内内存态存储：数据集/契约、任务、指标、告警全部保存在内存中，
 * 使用并发集合支撑并发提交。快照拷贝保证任务输入不可变、历史结论可复现。
 */
public class InMemoryStore {

    private final Map<String, Dataset> datasets = new ConcurrentHashMap<>();
    private final Map<String, ValidationTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<ValidationMetric>> metrics = new ConcurrentHashMap<>();
    private final Map<String, List<Alert>> alerts = new ConcurrentHashMap<>();

    public Map<String, Dataset> datasets() {
        return datasets;
    }

    public Map<String, ValidationTask> tasks() {
        return tasks;
    }

    public List<ValidationMetric> metricsOf(String datasetId) {
        return metrics.computeIfAbsent(datasetId, k -> new ArrayList<>());
    }

    public List<Alert> alertsOf(String datasetId) {
        return alerts.computeIfAbsent(datasetId, k -> new ArrayList<>());
    }

    // ---- 快照拷贝：契约与记录在跨版本/跨任务传递时深拷贝，杜绝外部或并发改写 ----

    public static Contract snapshotContract(Contract source) {
        Contract copy = new Contract();
        copy.setVersion(source.getVersion());
        copy.setCreatedAtMillis(source.getCreatedAtMillis());
        List<FieldConstraint> fields = new ArrayList<>();
        for (FieldConstraint fc : source.getFields()) {
            FieldConstraint c = new FieldConstraint();
            c.setName(fc.getName());
            c.setType(fc.getType());
            c.setRequired(fc.isRequired());
            c.setUnique(fc.isUnique());
            c.setMin(fc.getMin());
            c.setMax(fc.getMax());
            c.setMaxAgeMillis(fc.getMaxAgeMillis());
            fields.add(c);
        }
        copy.setFields(fields);
        return copy;
    }

    public static Record snapshotRecord(Record source) {
        Record copy = new Record();
        copy.setRecordId(source.getRecordId());
        copy.setFields(new java.util.LinkedHashMap<>(source.getFields()));
        return copy;
    }

    public static List<Record> snapshotRecords(List<Record> source) {
        List<Record> copy = new ArrayList<>(source.size());
        for (Record record : source) {
            copy.add(snapshotRecord(record));
        }
        return copy;
    }
}
