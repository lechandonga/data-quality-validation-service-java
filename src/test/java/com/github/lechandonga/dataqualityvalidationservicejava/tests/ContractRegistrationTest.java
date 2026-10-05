package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractService;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.FieldSpecRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ErrorCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationException;
import com.github.lechandonga.dataqualityvalidationservicejava.store.InMemoryStore;
import com.github.lechandonga.dataqualityvalidationservicejava.tests.support.BusinessCoverageLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractRegistrationTest {

    @RegisterExtension
    static final BusinessCoverageLogger COVERAGE = new BusinessCoverageLogger();

    private ContractService service;

    @BeforeEach
    void setUp() {
        service = new ContractService(new InMemoryStore(), Clock.systemUTC());
    }

    private FieldSpecRequest idField() {
        return new FieldSpecRequest("id", "STRING", true, null, null, true, false, null);
    }

    @Test
    @DisplayName("合法契约发布 v1，字段固化且带 checksum")
    void validContractPublishesVersionOne() {
        Contract contract = service.register(new ContractRequest("orders", List.of(
                idField(),
                new FieldSpecRequest("amount", "DECIMAL", true, 0.0, 1000.0, false, false, null))));

        assertEquals(1, contract.version());
        assertEquals("orders", contract.datasetId());
        assertEquals(2, contract.fields().size());
        assertTrue(contract.checksum().matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("与当前版本完全相同的重复注册被显式拒绝，不静默接受")
    void identicalContractRejected() {
        ContractRequest request = new ContractRequest("orders", List.of(idField()));
        service.register(request);

        ValidationException ex = assertThrows(ValidationException.class,
        () -> service.register(request));
        assertEquals(ErrorCode.DUPLICATE_CONTRACT, ex.errorCode());
        assertTrue(ex.getMessage().contains("identical"));
    }

    @Test
    @DisplayName("契约变更生成新版本，历史版本保留且不可变")
    void changePublishesNewVersionAndKeepsHistory() {
        service.register(new ContractRequest("orders", List.of(idField())));
        Contract v2 = service.register(new ContractRequest("orders", List.of(
                idField(),
                new FieldSpecRequest("amount", "DECIMAL", false, null, null, false, false, null))));

        assertEquals(2, v2.version());
        assertEquals(1, service.requireVersion("orders", 1).version());
        assertEquals(1, service.requireVersion("orders", 1).fields().size());
        assertNotEquals(
                service.requireVersion("orders", 1).checksum(),
                service.requireVersion("orders", 2).checksum());
    }

    @Test
    @DisplayName("畸形契约一次性报出全部问题：空字段、重名、未知类型")
    void malformedContractCollectsAllReasons() {
        List<com.github.lechandonga.dataqualityvalidationservicejava.contract.FieldSpecRequest> fields =
                new java.util.ArrayList<>(List.of(
                        new FieldSpecRequest(" ", "WEIRD", false, null, null, false, false, null),
                        new FieldSpecRequest("dup", "STRING", false, null, null, false, false, null),
                        new FieldSpecRequest("dup", "NUMBER", false, null, null, false, false, null)));
        fields.add(null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.register(new ContractRequest("orders", fields)));

        assertEquals(ErrorCode.MALFORMED_CONTRACT, ex.errorCode());
        String joined = String.join(";", ex.details());
        assertTrue(joined.contains("at least one") || joined.contains("null")
                || joined.contains("blank"), joined);
        assertTrue(joined.contains("duplicate field name 'dup'"), joined);
        assertTrue(joined.contains("unsupported field type 'NUMBER'"), joined);
        assertTrue(ex.details().size() >= 3, () -> "expected all reasons, got " + ex.details());
    }

    @Test
    @DisplayName("自相矛盾约束全部拒绝：区间用错类型、min>max、时效配置矛盾")
    void contradictoryConstraintsRejected() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.register(new ContractRequest("orders", List.of(
                        new FieldSpecRequest("name", "STRING", false, 0.0, 9.0, false, false, null),
                        new FieldSpecRequest("amount", "DECIMAL", false, 100.0, 1.0,
                                false, false, null),
                        new FieldSpecRequest("eventTime", "TIMESTAMP", false, null, null,
                                false, true, null),
                        new FieldSpecRequest("createdAt", "TIMESTAMP", false, null, null,
                                false, false, 5000L)))));

        String joined = String.join(";", ex.details());
        assertTrue(joined.contains("only valid on INTEGER/DECIMAL"), joined);
        assertTrue(joined.contains("must not be greater than max"), joined);
        assertTrue(joined.contains("requires a positive maxAgeMillis"), joined);
        assertTrue(joined.contains("contradictory constraint"), joined);
    }
}
