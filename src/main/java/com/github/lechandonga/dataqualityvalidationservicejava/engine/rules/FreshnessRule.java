package com.github.lechandonga.dataqualityvalidationservicejava.engine.rules;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleOutput;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.TypeCoercion;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationRule;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

/**
 * 时效规则：TIMESTAMP 字段的业务时间距校验时刻超过 maxAgeMillis 即报 FRESHNESS 违规。
 */
public class FreshnessRule implements ValidationRule {

    @Override
    public ConstraintType constraintType() {
        return ConstraintType.FRESHNESS;
    }

    @Override
    public boolean appliesTo(FieldConstraint constraint) {
        return constraint.getMaxAgeMillis() != null;
    }

    @Override
    public void check(Record record, FieldConstraint constraint, RuleOutput output, long nowMillis) {
        Object raw = record.getFields().get(constraint.getName());
        if (raw == null) {
            return;
        }
        Long timestamp;
        try {
            timestamp = (Long) TypeCoercion.coerce(raw,
                    com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType.TIMESTAMP);
        } catch (TypeCoercion.CoercionException e) {
            return;
        }
        long age = nowMillis - timestamp;
        if (age > constraint.getMaxAgeMillis()) {
            output.violation(record.getRecordId(), constraint.getName(), ConstraintType.FRESHNESS,
                    String.format("数据已过期：延迟 %d ms，超过阈值 %d ms", age, constraint.getMaxAgeMillis()), raw);
        }
    }
}
