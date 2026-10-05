package com.github.lechandonga.dataqualityvalidationservicejava.engine.rules;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleOutput;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.TypeCoercion;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationRule;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

import java.util.Map;

/**
 * 字段类型规则：值无法归一化到契约声明类型时报 TYPE 违规。
 */
public class TypeRule implements ValidationRule {

    @Override
    public ConstraintType constraintType() {
        return ConstraintType.TYPE;
    }

    @Override
    public boolean appliesTo(FieldConstraint constraint) {
        return constraint.getType() != null;
    }

    @Override
    public void check(Record record, FieldConstraint constraint, RuleOutput output, long nowMillis) {
        Map<String, Object> values = record.getFields();
        if (!values.containsKey(constraint.getName())) {
            return;
        }
        Object raw = values.get(constraint.getName());
        if (raw == null) {
            return;
        }
        try {
            TypeCoercion.coerce(raw, constraint.getType());
        } catch (TypeCoercion.CoercionException e) {
            output.violation(record.getRecordId(), constraint.getName(), ConstraintType.TYPE,
                    "字段类型不匹配：" + e.getMessage(), describe(raw));
        }
    }

    private static String describe(Object raw) {
        return raw + " (" + raw.getClass().getSimpleName() + ")";
    }

}
