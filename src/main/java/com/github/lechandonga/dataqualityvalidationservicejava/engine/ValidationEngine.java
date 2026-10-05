package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.rules.FreshnessRule;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.rules.RangeRule;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.rules.RequiredRule;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.rules.TypeRule;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.rules.UniqueRule;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;
import com.github.lechandonga.dataqualityvalidationservicejava.model.RuleErrorType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 校验引擎：按固定顺序编排规则，收集全部违规，
 * 并把规则内部抛出的任何异常归一化为 {@link RuleErrorType}。
 *
 * <p>每次校验都会通过 {@code ruleSetFactory} 创建全新的规则实例，
 * 因此多个任务并发执行时规则内部状态（如唯一性统计表）互不污染。
 */
public class ValidationEngine {

    /** 单条规则的默认超时时间。 */
    public static final long DEFAULT_RULE_TIMEOUT_MILLIS = 1000L;

    private final Supplier<List<ValidationRule>> ruleSetFactory;
    private final ExecutorService timeoutExecutor;
    private final long ruleTimeoutMillis;

    public ValidationEngine() {
        this(ValidationEngine::defaultRules,
                Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "rule-timeout-watchdog");
                    t.setDaemon(true);
                    return t;
                }),
                DEFAULT_RULE_TIMEOUT_MILLIS);
    }

    public ValidationEngine(Supplier<List<ValidationRule>> ruleSetFactory,
                            ExecutorService timeoutExecutor,
                            long ruleTimeoutMillis) {
        this.ruleSetFactory = ruleSetFactory;
        this.timeoutExecutor = timeoutExecutor;
        this.ruleTimeoutMillis = ruleTimeoutMillis;
    }

    public static List<ValidationRule> defaultRules() {
        List<ValidationRule> rules = new ArrayList<>();
        rules.add(new TypeRule());
        rules.add(new RequiredRule());
        rules.add(new RangeRule());
        rules.add(new UniqueRule());
        rules.add(new FreshnessRule());
        return rules;
    }

    public ValidationResult validate(List<Record> records, Contract contract, long nowMillis) {
        List<ValidationRule> rules = ruleSetFactory.get();
        List<FieldConstraint> constraints = contract.getFields();
        RuleOutput output = new RuleOutput();

        for (ValidationRule rule : rules) {
            List<FieldConstraint> applicable = constraints.stream()
                    .filter(rule::appliesTo)
                    .toList();
            if (applicable.isEmpty()) {
                continue;
            }
            runGuarded(null, "(prepare)", rule, () -> rule.prepare(records, constraints), output);
            for (Record record : records) {
                for (FieldConstraint constraint : applicable) {
                    runGuarded(record.getRecordId(), constraint.getName(), rule,
                            () -> rule.check(record, constraint, output, nowMillis), output);
                }
            }
        }

        return assemble(records, contract, output);
    }

    @FunctionalInterface
    private interface GuardedAction {
        void run() throws Exception;
    }

    private void runGuarded(String recordId, String fieldName, ValidationRule rule,
                            GuardedAction action, RuleOutput output) {
        Future<?> future = timeoutExecutor.submit(() -> {
            action.run();
            return null;
        });
        try {
            future.get(ruleTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            output.error(recordId, fieldName, RuleErrorType.TIMEOUT_ERROR,
                    safeMessage(rule) + "执行超时（>" + ruleTimeoutMillis + " ms），未计为通过");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.error(recordId, fieldName, RuleErrorType.RULE_EXECUTION_ERROR,
                    safeMessage(rule) + "被中断，未计为通过");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            output.error(recordId, fieldName, classify(cause),
                    safeMessage(rule) + "执行异常并已被归一化，未计为通过");
        } catch (Throwable t) {
            output.error(recordId, fieldName, classify(t),
                    safeMessage(rule) + "执行异常并已归一化，未计为通过");
        }
    }

    /**
     * 异常归一化：对外只暴露错误类型，不泄露原始异常类名、堆栈或底层信息。
     */
    private static RuleErrorType classify(Throwable t) {
        if (t instanceof TypeCoercion.CoercionException) {
            return RuleErrorType.TYPE_COERCION_ERROR;
        }
        Throwable cause = t.getCause();
        if (cause instanceof TypeCoercion.CoercionException) {
            return RuleErrorType.TYPE_COERCION_ERROR;
        }
        return RuleErrorType.RULE_EXECUTION_ERROR;
    }

    private static String safeMessage(ValidationRule rule) {
        return "规则[" + rule.constraintType() + "]";
    }

    private static ValidationResult assemble(List<Record> records, Contract contract, RuleOutput output) {
        ValidationResult result = new ValidationResult();
        result.setContractVersion(contract.getVersion());
        result.setRecordCount(records.size());
        result.getViolations().addAll(output.getViolations());
        result.getRuleErrors().addAll(output.getErrors());

        Set<String> problemRecords = new HashSet<>();
        output.getViolations().forEach(v -> problemRecords.add(v.getRecordId()));
        output.getErrors().stream()
                .filter(e -> e.getRecordId() != null)
                .forEach(e -> problemRecords.add(e.getRecordId()));
        result.setViolatedRecordCount((int) records.stream()
                .filter(r -> output.getViolations().stream().anyMatch(v -> r.getRecordId().equals(v.getRecordId())))
                .count());
        result.setPassedRecordCount((int) records.stream()
                .map(Record::getRecordId)
                .filter(id -> !problemRecords.contains(id))
                .count());
        return result;
    }
}
