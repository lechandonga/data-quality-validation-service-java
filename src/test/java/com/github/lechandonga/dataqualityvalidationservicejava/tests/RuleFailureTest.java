package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;
import com.github.lechandonga.dataqualityvalidationservicejava.model.RuleError;
import com.github.lechandonga.dataqualityvalidationservicejava.model.RuleErrorType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationResult;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessArea;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessAreaLogger;
import com.github.lechandonga.dataqualityvalidationservicejava.support.SabotageRule;
import com.github.lechandonga.dataqualityvalidationservicejava.support.SlowRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.Executors;

import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.contract;
import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.field;
import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：规则异常容错——单条规则失败不中断整批、不当成通过，
 * 异常归一化为可区分错误类型且不泄露原始异常内容。
 */
@BusinessArea("规则异常归一化与容错")
@ExtendWith(BusinessAreaLogger.class)
class RuleFailureTest {

    private Contract contractWithUniqueAndRange() {
        FieldConstraint id = field("id", FieldType.STRING);
        id.setRequired(true);
        id.setUnique(true);
        FieldConstraint age = field("age", FieldType.INTEGER);
        age.setMin(0.0);
        age.setMax(120.0);
        return contract(id, age);
    }

    @Test
    @DisplayName("规则抛异常被归一化为 RULE_EXECUTION_ERROR，其他规则仍继续执行")
    void ruleExceptionIsNormalizedAndBatchContinues() {
        ValidationEngine engine = new ValidationEngine(
                () -> List.of(
                        new com.github.lechandonga.dataqualityvalidationservicejava.engine.rules.TypeRule(),
                        new SabotageRule(),
                        new com.github.lechandonga.dataqualityvalidationservicejava.engine.rules.RangeRule()),
                Executors.newCachedThreadPool(), 1000);
        Record missingIdAndBadAge = record("r1", "age", 999);

        ValidationResult result = engine.validate(
                List.of(missingIdAndBadAge), contractWithUniqueAndRange(), 0L);

        List<RuleError> errors = result.getRuleErrors();
        assertFalse(errors.isEmpty(), "规则异常必须作为错误记录");
        assertTrue(errors.stream().allMatch(e -> e.getErrorType() == RuleErrorType.RULE_EXECUTION_ERROR));
        assertTrue(errors.stream().allMatch(e -> !e.getMessage().contains("S3CR3T")
                        && !e.getMessage().contains("AKIA")),
                "归一化消息不得泄露原始异常中的敏感内容");
        assertTrue(result.getViolations().stream()
                        .anyMatch(v -> "r1".equals(v.getRecordId())),
                "区间/必填等其他规则仍应继续执行并报出问题");
        assertEquals(0, result.getPassedRecordCount(), "规则报错的记录不得计为通过");
    }

    @Test
    @DisplayName("规则超时被归一化为 TIMEOUT_ERROR 且不影响任务完成")
    void timeoutRuleNormalized() {
        ValidationEngine engine = new ValidationEngine(
                () -> List.of(new SlowRule(400)),
                Executors.newCachedThreadPool(), 100);
        Record record = record("r1", "id", "x", "age", 30);

        ValidationResult result = engine.validate(List.of(record), contractWithUniqueAndRange(), 0L);

        assertTrue(result.getRuleErrors().stream()
                .anyMatch(e -> e.getErrorType() == RuleErrorType.TIMEOUT_ERROR));
        assertEquals(0, result.getPassedRecordCount());
    }

    @Test
    @DisplayName("规则内部状态异常不污染下一次校验（全新规则实例）")
    void freshRuleStatePerRun() {
        ValidationEngine engine = new ValidationEngine();
        Record r1 = record("r1", "id", "dup", "age", 10);
        Record r2 = record("r2", "id", "dup", "age", 20);
        Record r3 = record("r3", "id", "only-once", "age", 30);

        ValidationResult first = engine.validate(List.of(r1, r2), contractWithUniqueAndRange(), 0L);
        ValidationResult second = engine.validate(List.of(r3), contractWithUniqueAndRange(), 0L);

        assertEquals(2, first.getViolations().stream()
                .filter(v -> v.getConstraint() == ConstraintType.UNIQUENESS).count());
        assertTrue(second.isClean(), "第二次校验不应看到第一次批次的唯一性统计");
    }
}
