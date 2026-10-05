package com.github.lechandonga.dataqualityvalidationservicejava.service;

import com.github.lechandonga.dataqualityvalidationservicejava.exception.ServiceException;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Dataset;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ErrorCode;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * 数据集与契约服务：数据集注册、数据接入（内存模拟）、
 * 契约注册与版本化更新。契约注册前强制合法性校验。
 */
@Service
public class DatasetService {

    private final InMemoryStore store;
    private final Clock clock;

    public DatasetService(InMemoryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Dataset registerDataset(String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_ARGUMENT, "datasetId 不能为空");
        }
        Dataset created = new Dataset(datasetId);
        Dataset existing = store.datasets().putIfAbsent(datasetId, created);
        if (existing != null) {
            throw new ServiceException(ErrorCode.INVALID_ARGUMENT, "数据集已存在：" + datasetId);
        }
        return created;
    }

    public Dataset getDataset(String datasetId) {
        Dataset dataset = store.datasets().get(datasetId);
        if (dataset == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "数据集不存在：" + datasetId);
        }
        return dataset;
    }

    /**
     * 接入一批数据（模拟接入方写入）。整批替换当前数据，返回写入条数。
     */
    public int loadRecords(String datasetId, List<Record> records) {
        Dataset dataset = getDataset(datasetId);
        if (records == null || records.isEmpty()) {
            throw new ServiceException(ErrorCode.INVALID_RECORD, "数据批次不能为空");
        }
        for (int i = 0; i < records.size(); i++) {
            Record record = records.get(i);
            if (record == null || record.getRecordId() == null || record.getRecordId().isBlank()) {
                throw new ServiceException(ErrorCode.INVALID_RECORD,
                        "第 " + (i + 1) + " 条记录缺少 recordId，无法定位问题行");
            }
        }
        synchronized (dataset) {
            dataset.getRecords().clear();
            for (Record record : records) {
                dataset.getRecords().add(InMemoryStore.snapshotRecord(record));
            }
            return dataset.getRecords().size();
        }
    }

    /**
     * 注册或更新契约：通过合法性校验后生成新版本快照并追加到版本历史。
     */
    public Contract registerContract(String datasetId, Contract proposed) {
        Dataset dataset = getDataset(datasetId);
        ContractValidator.validate(proposed);
        synchronized (dataset) {
            int nextVersion = dataset.getContractHistory().size() + 1;
            Contract snapshot = InMemoryStore.snapshotContract(proposed);
            snapshot.setVersion(nextVersion);
            snapshot.setCreatedAtMillis(clock.millis());
            dataset.getContractHistory().add(snapshot);
            return snapshot;
        }
    }

    public Contract getContractVersion(String datasetId, Integer version) {
        Dataset dataset = getDataset(datasetId);
        synchronized (dataset) {
            if (version == null) {
                Contract current = dataset.currentContract();
                if (current == null) {
                    throw new ServiceException(ErrorCode.NOT_FOUND,
                            "数据集尚未注册契约：" + datasetId);
                }
                return current;
            }
            return dataset.getContractHistory().stream()
                    .filter(c -> c.getVersion() == version)
                    .findFirst()
                    .orElseThrow(() -> new ServiceException(ErrorCode.NOT_FOUND,
                            "契约版本不存在：dataset=" + datasetId + ", version=" + version));
        }
    }
}
