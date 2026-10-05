package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldSpec;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.IssueCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.RecordReport;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationIssue;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 校验引擎核心行为：
 * 1) 一条记录命中多个问题时全部保留、原因可区分；
 * 2) 单条规则异常不中断其它规则与整批记录，且失败不算通过；
 * 3) 规则异常归一化为 RuleErrorType，原始异常不外透。
 */
@Component
public class ValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(ValidationEngine.class);

    private final List<Rule> rules;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ValidationEngine(Clock clock) {
        this.clock = clock;
        this.rules = List.of(
                BuiltInRules.REQUIRED,
                BuiltInRules.FIELD_TYPE,
                BuiltInRules.VALUE_RANGE,
                BuiltInRules.UNIQUENESS,
                BuiltInRules.FRESHNESS);
    }

    public ValidationEngine(Clock clock, List<Rule> extraRules) {
        this.clock = clock;
        List<Rule> all = new ArrayList<>(List.of(
                BuiltInRules.REQUIRED,
                BuiltInRules.FIELD_TYPE,
                BuiltInRules.VALUE_RANGE,
                BuiltInRules.UNIQUENESS,
                BuiltInRules.FRESHNESS));
        all.addAll(extraRules);
        this.rules = List.copyOf(all);
    }

    public ValidationReport validate(String taskId, Contract contract,
                                     List<Map<String, Object>> records) {
        long evaluatedAt = clock.millis();
        List<String> uniqueFields = contract.fields().stream()
                .filter(FieldSpec::unique)
                .map(FieldSpec::name)
                .toList();
        DuplicateIndex duplicateIndex = new DuplicateIndex(uniqueFields, records);

        List<RecordReport> reports = new ArrayList<>(records.size());
        for (int index = 0; index < records.size(); index++) {
            Map<String, Object> record = records.get(index);
            reports.add(validateRecord(contract, record, duplicateIndex, evaluatedAt, index));
        }
        return new ValidationReport(taskId, contract.datasetId(), contract.version(),
                contract.checksum(), clock.millis(), reports);
    }

    private RecordReport validateRecord(Contract contract, Map<String, Object> record,
                                        DuplicateIndex duplicateIndex, long evaluatedAt, int index) {
        String recordId = resolveRecordId(record, index);
        List<ValidationIssue> issues = new ArrayList<>();

        for (FieldSpec field : contract.fields()) {
            Object rawValue = record.get(field.name());

            Boolean requiredPassed = null;
            Boolean typePassed = null;
            for (Rule rule : rules) {
                RuleContext context = new RuleContext(contract, field, record,
                        duplicateIndex, evaluatedAt);
                if (!safeApplies(rule, context, field, issues)) {
                    continue;
                }
                if (requiresRequiredPass(rule) && Boolean.FALSE.equals(requiredPassed)) {
                    continue;
                }
                if (requiresTypePass(rule) && Boolean.FALSE.equals(typePassed)) {
                    continue;
                }
                try {
                    rule.evaluate(context).ifPresent(issues::add);
                    if (rule == BuiltInRules.REQUIRED) {
                        requiredPassed = !BuiltInRules.isMissing(rawValue);
                    }
                    if (rule == BuiltInRules.FIELD_TYPE) {
                        typePassed = rawValue == null || matchesType(field.type(), rawValue);
                    }
                } catch (RuntimeException ex) {
                    RuleErrorType type = ExceptionNormalizer.normalize(ex);
                    log.warn("rule '{}' threw on field '{}' of record '{}': {}: {}",
                            rule.name(), field.name(), recordId, type, ex.toString(), ex);
                    issues.add(ValidationIssue.ruleError(rule.name(), field.name(), type,
                            ExceptionNormalizer.safeMessage(type, rule.name(), field.name())));
                }
            }
        }
        return new RecordReport(recordId, issues);
    }

    private boolean safeApplies(Rule rule, RuleContext context, FieldSpec field,
                                List<ValidationIssue> issues) {
        try {
            return rule.appliesTo(context);
        } catch (RuntimeException ex) {
            RuleErrorType type = ExceptionNormalizer.normalize(ex);
            log.warn("rule '{}' applicability check threw on field '{}': {}: {}",
                    rule.name(), field.name(), type, ex.toString(), ex);
            issues.add(ValidationIssue.ruleError(rule.name(), field.name(), type,
                    ExceptionNormalizer.safeMessage(type, rule.name(), field.name())));
            return false;
        }
    }

    private boolean requiresRequiredPass(Rule rule) {
        return rule != BuiltInRules.REQUIRED;
    }

    private boolean requiresTypePass(Rule rule) {
        return rule == BuiltInRules.VALUE_RANGE
                || rule == BuiltInRules.FRESHNESS
                || rule == BuiltInRules.UNIQUENESS;
    }

    private boolean matchesType(FieldType type, Object value) {
        if (value == null) {
            return true;
        }
        return switch (type) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case DECIMAL -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case TIMESTAMP -> value instanceof Number
                    || (value instanceof String text && BuiltInRules.toEpochMillis(text) != null);
        };
    }

    private String resolveRecordId(Map<String, Object> record, int index) {
        Object id = record.get("id");
        if (id != null && !String.valueOf(id).isBlank()) {
            return String.valueOf(id);
        }
        return "record#" + index;
    }
}
