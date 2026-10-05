package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationResult;
import com.github.lechandonga.dataqualityvalidationservicejava.service.DatasetService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.InMemoryStore;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessArea;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessAreaLogger;
import com.github.lechandonga.dataqualityvalidationservicejava.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.contract;
import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.field;
import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 业务：契约版本化——契约更新生成新版本且不可变，历史版本仍可取用，
 * 用历史版本重放同一份数据可复现历史结论。
 */
@BusinessArea("契约版本更新与历史结论复现")
@ExtendWith(BusinessAreaLogger.class)
class ContractVersioningTest {

    @Test
    @DisplayName("契约更新产生新版本；按历史版本重放同一数据得到当时结论")
    void historicalResultsAreReproducible() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000L);
        DatasetService datasets = new DatasetService(store, clock);
        datasets.registerDataset("ds-v");

        FieldConstraint ageV1 = field("age", FieldType.INTEGER);
        ageV1.setMin(0.0);
        ageV1.setMax(100.0);
        Contract v1 = datasets.registerContract("ds-v", contract(ageV1));
        assertEquals(1, v1.getVersion());

        List<Record> data = List.of(record("r1", "age", 150));
        datasets.loadRecords("ds-v", data);

        ValidationEngine engine = new ValidationEngine();
        ValidationResult resultUnderV1 = engine.validate(data,
                InMemoryStore.snapshotContract(datasets.getContractVersion("ds-v", 1)), 10_000L);
        assertEquals(1, resultUnderV1.getViolations().size());
        assertEquals(1, resultUnderV1.getContractVersion());

        clock.advanceMillis(500);
        FieldConstraint ageV2 = field("age", FieldType.INTEGER);
        ageV2.setMin(0.0);
        ageV2.setMax(200.0);
        Contract v2 = datasets.registerContract("ds-v", contract(ageV2));
        assertEquals(2, v2.getVersion());

        ValidationResult resultUnderV2 = engine.validate(data,
                InMemoryStore.snapshotContract(datasets.getContractVersion("ds-v", 2)), 10_000L);
        assertEquals(0, resultUnderV2.getViolations().size(), "新契约放宽区间后同数据应通过");
        assertEquals(2, resultUnderV2.getContractVersion());

        ValidationResult replayV1 = engine.validate(data,
                InMemoryStore.snapshotContract(datasets.getContractVersion("ds-v", 1)), 10_000L);
        assertEquals(resultUnderV1.getViolations().size(), replayV1.getViolations().size(),
                "契约升级后用 v1 重放必须复现历史结论");
        assertEquals(1, replayV1.getContractVersion());
        assertNotEquals(resultUnderV1.isClean(), resultUnderV2.isClean());
    }

    @Test
    @DisplayName("注册非法的新版本契约被拒绝，当前版本不受影响")
    void invalidNewVersionDoesNotMutateCurrent() {
        InMemoryStore store = new InMemoryStore();
        DatasetService datasets = new DatasetService(store, new MutableClock(1L));
        datasets.registerDataset("ds-v2");
        datasets.registerContract("ds-v2", contract(field("age", FieldType.INTEGER)));

        FieldConstraint bad = field("age", FieldType.INTEGER);
        bad.setMin(99.0);
        bad.setMax(1.0);
        assertThrows(Exception.class, () -> datasets.registerContract("ds-v2", contract(bad)));

        assertEquals(1, datasets.getContractVersion("ds-v2", null).getVersion(),
                "非法注册被拒绝后不应产生新版本");
    }
}
