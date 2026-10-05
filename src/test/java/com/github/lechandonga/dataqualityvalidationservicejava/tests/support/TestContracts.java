package com.github.lechandonga.dataqualityvalidationservicejava.tests.support;

import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractService;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.FieldSpecRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldSpec;
import com.github.lechandonga.dataqualityvalidationservicejava.store.InMemoryStore;

import java.time.Clock;
import java.util.List;

/**
 * 测试夹具：直接构造引擎用契约，以及通过正式注册链路构造契约。
 */
public final class TestContracts {

    private TestContracts() {
    }

    public static Contract contractWith(List<FieldSpec> fields) {
        return new Contract("ds", 1, fields,
                ContractService.checksum(fields), System.currentTimeMillis());
    }

    public static FieldSpec toSpec(FieldSpecRequest request) {
        return new FieldSpec(
                request.name(),
                com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldType
                        .valueOf(request.type()),
                Boolean.TRUE.equals(request.required()),
                request.min(), request.max(),
                Boolean.TRUE.equals(request.unique()),
                Boolean.TRUE.equals(request.freshnessCheck()),
                Boolean.TRUE.equals(request.freshnessCheck()) ? request.maxAgeMillis() : null);
    }

    public static ContractService contractService(InMemoryStore store, Clock clock) {
        return new ContractService(store, clock);
    }

    public static ContractRequest request(String datasetId, FieldSpecRequest... fields) {
        return new ContractRequest(datasetId, List.of(fields));
    }
}
