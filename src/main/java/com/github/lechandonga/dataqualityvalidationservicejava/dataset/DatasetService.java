package com.github.lechandonga.dataqualityvalidationservicejava.dataset;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.ErrorCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationException;
import com.github.lechandonga.dataqualityvalidationservicejava.store.DatasetEntity;
import com.github.lechandonga.dataqualityvalidationservicejava.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存数据集写入。整批替换并深拷贝，外部引用变更不会污染任务快照。
 */
@Service
public class DatasetService {

    private final InMemoryStore store;

    public DatasetService(InMemoryStore store) {
        this.store = store;
    }

    public int replaceRecords(String datasetId, List<Map<String, Object>> records) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new ValidationException(ErrorCode.MALFORMED_DATASET,
                    "datasetId must not be blank");
        }
        if (records == null) {
            throw new ValidationException(ErrorCode.MALFORMED_DATASET,
                    "records must not be null (send an empty list to clear)");
        }
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i) == null) {
                throw new ValidationException(ErrorCode.MALFORMED_DATASET,
                        "records[" + i + "] must not be null");
            }
        }
        DatasetEntity dataset = store.getOrCreateDataset(datasetId.trim());
        dataset.lock().lock();
        try {
            dataset.replaceRecords(records);
            return records.size();
        } finally {
            dataset.lock().unlock();
        }
    }

    public List<Map<String, Object>> getRecords(String datasetId) {
        DatasetEntity dataset = store.dataset(datasetId);
        if (dataset == null) {
            throw new ValidationException(ErrorCode.DATASET_NOT_FOUND,
                    "dataset '" + datasetId + "' not found");
        }
        return dataset.currentRecords().stream()
                .<Map<String, Object>>map(LinkedHashMap::new)
                .toList();
    }
}
