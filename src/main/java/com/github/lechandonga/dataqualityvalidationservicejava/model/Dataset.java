package com.github.lechandonga.dataqualityvalidationservicejava.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存态数据集：接入方数据的模拟载体。
 * 每个数据集持有当前契约版本及全部历史版本。
 */
public class Dataset {
    private String datasetId;
    private final List<Contract> contractHistory = new ArrayList<>();
    private final List<Record> records = new ArrayList<>();

    public Dataset() {
    }

    public Dataset(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public List<Contract> getContractHistory() {
        return contractHistory;
    }

    public List<Record> getRecords() {
        return records;
    }

    public Contract currentContract() {
        return contractHistory.isEmpty() ? null : contractHistory.get(contractHistory.size() - 1);
    }
}
