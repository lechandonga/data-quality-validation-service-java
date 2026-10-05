package com.github.lechandonga.dataqualityvalidationservicejava.engine.rules;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleOutput;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationRule;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

/**
 * 必填规则：字段缺失或为 null 报 REQUIRED 违规。
 */
public class RequiredRule implements ValidationRule {

    @Override
    public ConstraintType constraintType() {
        return ConstraintType.REQUIRED;
    }

    @Override
    public boolean appliesTo(FieldConstraint constraint) {
        return constraint.isRequired();
    }

    @Override
    public void check(Record record, FieldConstraint constraint, RuleOutput output, long nowMillis) {
        Object value = record.getFields().get(constraint.getName());
        boolean missing = !record.getFields().containsKey(constraint.getName())
                || value == null
                || (value instanceof String s && s.isEmpty());
        if (missing) {
            output.violation(record.getRecordId(), constraint.getName(), ConstraintType.REQUIRED,
                    "必填字段缺失或为空", null);
        }
    }
}
