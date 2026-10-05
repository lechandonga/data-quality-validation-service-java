package com.github.lechandonga.dataqualityvalidationservicejava.engine.rules;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleOutput;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationRule;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 唯一性规则：需要整批预处理统计每个唯一字段的值频次。
 * 值为 null 或字段缺失不参与唯一性判定（由必填规则负责）。
 */
public class UniqueRule implements ValidationRule {

    private final Map<String, Map<Object, Integer>> frequencies = new HashMap<>();

    @Override
    public ConstraintType constraintType() {
        return ConstraintType.UNIQUENESS;
    }

    @Override
    public boolean appliesTo(FieldConstraint constraint) {
        return constraint.isUnique();
    }

    @Override
    public void prepare(List<Record> batch, List<FieldConstraint> constraints) {
        frequencies.clear();
        for (FieldConstraint constraint : constraints) {
            if (!appliesTo(constraint)) {
                continue;
            }
            Map<Object, Integer> counter = new HashMap<>();
            for (Record record : batch) {
                Object value = record.getFields().get(constraint.getName());
                if (value != null) {
                    counter.merge(value, 1, Integer::sum);
                }
            }
            frequencies.put(constraint.getName(), counter);
        }
    }

    @Override
    public void check(Record record, FieldConstraint constraint, RuleOutput output, long nowMillis) {
        Object value = record.getFields().get(constraint.getName());
        if (value == null) {
            return;
        }
        int count = frequencies.getOrDefault(constraint.getName(), Map.of()).getOrDefault(value, 0);
        if (count > 1) {
            output.violation(record.getRecordId(), constraint.getName(), ConstraintType.UNIQUENESS,
                    "字段值在数据集中不唯一（出现 " + count + " 次）", value);
        }
    }
}
