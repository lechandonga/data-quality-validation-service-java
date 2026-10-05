package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationResult;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Violation;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessArea;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessAreaLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.contract;
import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.field;
import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：校验执行——五类约束全部生效；一条记录同时命中多个问题必须全部报出且原因可区分。
 */
@BusinessArea("校验执行与多问题归因")
@ExtendWith(BusinessAreaLogger.class)
class MultiIssueValidationTest {

    private final long now = 1_000_000_000_000L;
    private final ValidationEngine engine = new ValidationEngine();

    private Contract fullContract() {
        FieldConstraint id = field("id", FieldType.STRING);
        id.setRequired(true);
        id.setUnique(true);
        FieldConstraint age = field("age", FieldType.INTEGER);
        age.setRequired(true);
        age.setMin(0.0);
        age.setMax(120.0);
        FieldConstraint nickname = field("nickname", FieldType.STRING);
        nickname.setMin(2.0);
        nickname.setMax(8.0);
        FieldConstraint eventTime = field("eventTime", FieldType.TIMESTAMP);
        eventTime.setMaxAgeMillis(60_000L);
        return contract(id, age, nickname, eventTime);
    }

    @Test
    @DisplayName("同一记录同时命中 必填/类型/区间/时效 四类问题，全部报出且类型可区分")
    void oneRecordHitsMultipleIssues() {
        Record bad = record("r1",
                "age", "not-a-number",
                "nickname", "A",
                "eventTime", now - 120_000L);

        ValidationResult result = engine.validate(List.of(bad), fullContract(), now);

        List<Violation> r1 = result.getViolations().stream()
                .filter(v -> "r1".equals(v.getRecordId())).toList();
        assertTrue(r1.stream().anyMatch(v -> v.getConstraint() == ConstraintType.REQUIRED), "缺 id 应报必填");
        assertTrue(r1.stream().anyMatch(v -> v.getConstraint() == ConstraintType.TYPE), "age 字符串应报类型");
        assertTrue(r1.stream().anyMatch(v -> v.getConstraint() == ConstraintType.RANGE), "昵称长度 1 应报区间");
        assertTrue(r1.stream().anyMatch(v -> v.getConstraint() == ConstraintType.FRESHNESS), "旧时间应报时效");
        assertEquals(4, r1.size(), "四类问题必须全部报出，实际：" + r1);
        assertEquals(0, result.getPassedRecordCount());
        assertEquals(1, result.getViolatedRecordCount());
        assertFalse(result.isClean());
    }

    @Test
    @DisplayName("唯一性冲突在两条记录上都报 UNIQUENESS，且不影响其他规则继续执行")
    void uniquenessReportedOnBothRows() {
        Record a = record("r1", "id", "dup", "age", 30,
                "nickname", "alice", "eventTime", now);
        Record b = record("r2", "id", "dup", "age", 999,
                "nickname", "b", "eventTime", now);

        ValidationResult result = engine.validate(List.of(a, b), fullContract(), now);

        List<Violation> uniqueViolations = result.getViolations().stream()
                .filter(v -> v.getConstraint() == ConstraintType.UNIQUENESS).toList();
        assertEquals(2, uniqueViolations.size());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "r2".equals(v.getRecordId()) && v.getConstraint() == ConstraintType.RANGE));
    }

    @Test
    @DisplayName("干净数据全部通过，无误报")
    void cleanDataPasses() {
        Record a = record("r1", "id", "a", "age", 30, "nickname", "ali", "eventTime", now);
        Record b = record("r2", "id", "b", "age", 40, "nickname", "bob", "eventTime", now - 30_000L);

        ValidationResult result = engine.validate(List.of(a, b), fullContract(), now);

        assertTrue(result.isClean());
        assertEquals(2, result.getPassedRecordCount());
    }

    @Test
    @DisplayName("类型错误与区间错误根因不重复报：类型无法解释时只报 TYPE")
    void noDoubleReportForTypeRootCause() {
        Record bad = record("r1", "id", "x", "age", "abc", "nickname", "nick", "eventTime", now);
        ValidationResult result = engine.validate(List.of(bad), fullContract(), now);

        List<Violation> ageViolations = result.getViolations().stream()
                .filter(v -> "age".equals(v.getFieldName())).toList();
        assertEquals(1, ageViolations.size());
        assertEquals(ConstraintType.TYPE, ageViolations.get(0).getConstraint());
    }
}
