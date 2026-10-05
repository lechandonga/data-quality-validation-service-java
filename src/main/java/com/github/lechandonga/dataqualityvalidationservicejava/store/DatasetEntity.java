package com.github.lechandonga.dataqualityvalidationservicejava.store;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单个数据集的内存状态：当前数据 + 全部历史契约版本。
 * 锁内完成“发布契约 / 替换数据 + 快照”以保证任务提交快照一致。
 */
public class DatasetEntity {

    private final ReentrantLock lock = new ReentrantLock();
    private final List<Contract> contractVersions = new ArrayList<>();
    private final List<Map<String, Object>> records = new ArrayList<>();

    public ReentrantLock lock() {
        return lock;
    }

    public void addContract(Contract contract) {
        contractVersions.add(contract);
    }

    public Contract currentContract() {
        return contractVersions.isEmpty() ? null : contractVersions.get(contractVersions.size() - 1);
    }

    public Contract contractVersion(int version) {
        return contractVersions.stream()
                .filter(contract -> contract.version() == version)
                .findFirst()
                .orElse(null);
    }

    public List<Contract> allContracts() {
        return List.copyOf(contractVersions);
    }

    public void replaceRecords(List<Map<String, Object>> incoming) {
        records.clear();
        incoming.forEach(row -> records.add(new LinkedHashMap<>(row)));
    }

    /**
     * 返回深拷贝快照，任务执行期间数据集变更不会影响在途任务。
     */
    public List<Map<String, Object>> snapshotRecords() {
        List<Map<String, Object>> copy = new ArrayList<>(records.size());
        records.forEach(row -> copy.add(new LinkedHashMap<>(row)));
        return copy;
    }

    public List<Map<String, Object>> currentRecords() {
        List<Map<String, Object>> copy = new ArrayList<>(records.size());
        records.forEach(row -> copy.add(new LinkedHashMap<>(row)));
        return copy;
    }
}
