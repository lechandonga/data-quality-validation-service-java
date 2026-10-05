package com.github.lechandonga.dataqualityvalidationservicejava.service;

import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationMetric;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 指标服务：把每次校验结果沉淀为按数据集累积的指标，并支持时间窗口查询。
 */
@Service
public class MetricService {

    private final InMemoryStore store;

    public MetricService(InMemoryStore store) {
        this.store = store;
    }

    public ValidationMetric record(String taskId, String datasetId, long timestampMillis, ValidationResult result) {
        ValidationMetric metric = new ValidationMetric();
        metric.setTaskId(taskId);
        metric.setDatasetId(datasetId);
        metric.setContractVersion(result.getContractVersion());
        metric.setTimestampMillis(timestampMillis);
        metric.setRecordCount(result.getRecordCount());
        metric.setPassedRecordCount(result.getPassedRecordCount());
        metric.setViolationCount(result.getViolations().size());
        metric.setRuleErrorCount(result.getRuleErrorCount());
        synchronized (store.metricsOf(datasetId)) {
            store.metricsOf(datasetId).add(metric);
        }
        return metric;
    }

    public List<ValidationMetric> history(String datasetId) {
        synchronized (store.metricsOf(datasetId)) {
            return List.copyOf(store.metricsOf(datasetId));
        }
    }

    /** 查询 (nowMillis - windowMillis, nowMillis] 时间窗口内的指标。 */
    public List<ValidationMetric> window(String datasetId, long nowMillis, long windowMillis) {
        synchronized (store.metricsOf(datasetId)) {
            return store.metricsOf(datasetId).stream()
                    .filter(m -> m.getTimestampMillis() > nowMillis - windowMillis
                            && m.getTimestampMillis() <= nowMillis)
                    .toList();
        }
    }

    /** 按记录数加权聚合窗口内的平均问题率；无指标时返回 0。 */
    public double aggregateProblemRatio(List<ValidationMetric> metrics) {
        int records = metrics.stream().mapToInt(ValidationMetric::getRecordCount).sum();
        if (records == 0) {
            return 0.0;
        }
        double weighted = metrics.stream()
                .mapToDouble(m -> m.getProblemRatio() * m.getRecordCount())
                .sum();
        return weighted / records;
    }
}
