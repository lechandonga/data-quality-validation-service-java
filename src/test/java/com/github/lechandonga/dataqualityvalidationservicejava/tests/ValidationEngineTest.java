package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.IssueCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.RecordReport;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationIssue;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationReport;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.Rule;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleContext;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleErrorType;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.tests.support.BusinessCoverageLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.lechandonga.dataqualityvalidationservicejava.tests.support.TestContracts.contractWith;
import static com.github.lechandonga.dataqualityvalidationservicejava.tests.support.TestContracts.toSpec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationEngineTest {

    @RegisterExtension
    static final BusinessCoverageLogger COVERAGE = new BusinessCoverageLogger();

    private static final long NOW = Instant.parse("2026-10-05T06:00:00Z").toEpochMilli();

    private ValidationEngine engine() {
        return new ValidationEngine(Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
    }

    private static com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldSpec field(
            String name, String type, boolean required, Double min, Double max,
            boolean unique, boolean freshness, Long maxAge) {
        return toSpec(new com.github.lechandonga.dataqualityvalidationservicejava.contract
                .FieldSpecRequest(name, type, required, min, max, unique, freshness, maxAge));
    }

    @Test
    @DisplayName("一条记录同时命中多个问题时全部报出，原因码可区分")
    void multipleIssuesOnOneRecordAllReported() {
        Contract contract = contractWith(List.of(
                field("id", "STRING", true, null, null, true, false, null),
                field("amount", "DECIMAL", true, 0.0, 100.0, false, false, null),
                field("eventTime", "TIMESTAMP", true, null, null, false, true, 60_000L)));

        Map<String, Object> record = Map.of(
                "id", "dup-x",
                "amount", 999,
                "eventTime", Instant.ofEpochMilli(NOW - 7_200_000L).toString());
        List<Map<String, Object>> records = List.of(record, record);

        ValidationReport report = engine().validate("task-1", contract, records);
        RecordReport first = report.records().get(0);

        assertFalse(first.passed());
        List<IssueCode> codes = first.issues().stream().map(ValidationIssue::code).toList();
        assertTrue(codes.contains(IssueCode.VALUE_OUT_OF_RANGE), codes.toString());
        assertTrue(codes.contains(IssueCode.NOT_UNIQUE), codes.toString());
        assertTrue(codes.contains(IssueCode.FRESHNESS_VIOLATION), codes.toString());
        assertEquals(3, codes.stream().distinct().count());
        assertTrue(first.issues().stream().allMatch(issue -> issue.message() != null
                && !issue.message().isBlank() && issue.field() != null));
    }

    @Test
    @DisplayName("必填缺失只报 REQUIRED_MISSING，不制造误导性级联问题")
    void requiredMissingDoesNotCascade() {
        Contract contract = contractWith(List.of(
                field("id", "STRING", true, null, null, true, false, null),
                field("amount", "DECIMAL", true, 0.0, 100.0, false, false, null)));

        ValidationReport report = engine().validate("task-1", contract,
                List.of(Map.of("id", "a")));
        RecordReport recordReport = report.records().get(0);

        List<ValidationIssue> amountIssues = recordReport.issues().stream()
                .filter(issue -> issue.field().equals("amount"))
                .toList();
        assertEquals(1, amountIssues.size());
        assertEquals(IssueCode.REQUIRED_MISSING, amountIssues.get(0).code());
    }

    @Test
    @DisplayName("合法记录通过：ISO-8601 与 epoch 毫秒时间戳均接受")
    void validRecordsPassWithBothTimestampForms() {
        Contract contract = contractWith(List.of(
                field("eventTime", "TIMESTAMP", true, null, null, false, true, 60_000L),
                field("qty", "INTEGER", true, 1.0, 10.0, false, false, null)));

        ValidationReport report = engine().validate("task-1", contract, List.of(
                Map.of("eventTime", Instant.ofEpochMilli(NOW - 1_000L).toString(), "qty", 3),
                Map.of("eventTime", NOW - 2_000L, "qty", 5)));

        assertEquals(2, report.passedCount());
        assertEquals(0, report.failedCount());
    }

    @Test
    @DisplayName("类型不匹配被独立报出，数值字符串不会误通过区间规则")
    void typeMismatchReportedSeparately() {
        Contract contract = contractWith(List.of(
                field("amount", "DECIMAL", true, 0.0, 100.0, false, false, null)));

        ValidationReport report = engine().validate("task-1", contract,
                List.of(Map.of("amount", "not-a-number")));

        List<IssueCode> codes = report.records().get(0).issues().stream()
                .map(ValidationIssue::code).toList();
        assertEquals(List.of(IssueCode.FIELD_TYPE_MISMATCH), codes);
    }

    @Test
    @DisplayName("自定义规则抛 NPE：归一化为 RULE_DEFECT，批次不中断，失败不算通过，无堆栈外泄")
    void ruleNpeIsNormalizedAndBatchContinues() {
        Rule exploding = new Rule() {
            @Override
            public String name() {
                return "EXPLODING_RULE";
            }

            @Override
            public boolean appliesTo(RuleContext context) {
                return context.field().name().equals("boom");
            }

            @Override
            public Optional<ValidationIssue> evaluate(RuleContext context) {
                String boom = null;
                boom.length();
                return Optional.empty();
            }
        };
        Contract contract = contractWith(List.of(field("boom", "STRING", false,
                null, null, false, false, null)));
        ValidationEngine engine = new ValidationEngine(
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC), List.of(exploding));

        ValidationReport report = engine.validate("task-1", contract,
                List.of(Map.of("boom", "a"), Map.of("boom", "b")));

        assertEquals(0, report.passedCount());
        assertEquals(2, report.failedCount());
        assertEquals(2, report.ruleErrorCount());
        ValidationIssue issue = report.records().get(0).issues().get(0);
        assertEquals(IssueCode.RULE_EVALUATION_ERROR, issue.code());
        assertEquals(RuleErrorType.RULE_DEFECT, issue.errorType());
        assertNotNull(issue.message());
        assertFalse(issue.message().contains("NullPointerException"), issue.message());
    }

    @Test
    @DisplayName("不同原始异常归一化为可区分错误类型：Arithmetic vs NumberFormat")
    void distinctExceptionsMapToDistinctTypes() {
        Rule arithmetic = new Rule() {
            @Override
            public String name() {
                return "ARITHMETIC_RULE";
            }

            @Override
            public boolean appliesTo(RuleContext context) {
                return context.field().name().equals("a");
            }

            @Override
            public Optional<ValidationIssue> evaluate(RuleContext context) {
                int ignored = 1 / 0;
                return Optional.empty();
            }
        };
        Rule coercion = new Rule() {
            @Override
            public String name() {
                return "COERCION_RULE";
            }

            @Override
            public boolean appliesTo(RuleContext context) {
                return context.field().name().equals("b");
            }

            @Override
            public Optional<ValidationIssue> evaluate(RuleContext context) {
                Integer.parseInt("xyz");
                return Optional.empty();
            }
        };
        Contract contract = new Contract("ds", 1, List.of(
                field("a", "INTEGER", false, null, null, false, false, null),
                field("b", "STRING", false, null, null, false, false, null)),
                "checksum-x", NOW);
        ValidationEngine engine = new ValidationEngine(
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC),
                List.of(arithmetic, coercion));

        ValidationReport report = engine.validate("task-1", contract,
                List.of(Map.of("a", 1, "b", "x")));
        RecordReport recordReport = report.records().get(0);

        assertTrue(recordReport.issues().stream()
                .anyMatch(issue -> issue.errorType() == RuleErrorType.RULE_COMPUTATION_ERROR));
        assertTrue(recordReport.issues().stream()
                .anyMatch(issue -> issue.errorType() == RuleErrorType.RULE_DATA_COERCION_ERROR));
        assertFalse(recordReport.passed());
        assertEquals(1, report.failedCount());
    }
}
