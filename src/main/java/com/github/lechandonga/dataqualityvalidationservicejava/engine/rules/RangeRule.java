package com.github.lechandonga.dataqualityvalidationservicejava.engine.rules;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleOutput;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.TypeCoercion;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationRule;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

/**
 * 取值区间规则：数值型检查数值闭区间，字符串型检查长度闭区间。
 * 类型不正确时跳过（由 {@link TypeRule} 报 TYPE），避免同一根因重复报错。
 */
public class RangeRule implements ValidationRule {

    @Override
    public ConstraintType constraintType() {
        return ConstraintType.RANGE;
    }

    @Override
    public boolean appliesTo(FieldConstraint constraint) {
        return constraint.getMin() != null || constraint.getMax() != null;
    }

    @Override
    public void check(Record record, FieldConstraint constraint, RuleOutput output, long nowMillis) {
        Object raw = record.getFields().get(constraint.getName());
        if (raw == null) {
            return;
        }
        if (constraint.getType() == FieldType.STRING) {
            if (!(raw instanceof String)) {
                return;
            }
            int length = ((String) raw).length();
            if (outOfRange(length, constraint)) {
                output.violation(record.getRecordId(), constraint.getName(), ConstraintType.RANGE,
                        String.format("字符串长度 %d 超出允许区间 [%s, %s]",
                                length, bound(constraint.getMin()), bound(constraint.getMax())), raw);
            }
            return;
        }
        Double number;
        try {
            number = (Double) TypeCoercion.coerce(raw, FieldType.DECIMAL);
        } catch (TypeCoercion.CoercionException e) {
            return;
        }
        if (outOfRange(number, constraint)) {
            output.violation(record.getRecordId(), constraint.getName(), ConstraintType.RANGE,
                    String.format("数值 %s 超出允许区间 [%s, %s]",
                            number, bound(constraint.getMin()), bound(constraint.getMax())), number);
        }
    }

    private static boolean outOfRange(double value, FieldConstraint constraint) {
        return (constraint.getMin() != null && value < constraint.getMin())
                || (constraint.getMax() != null && value > constraint.getMax());
    }

    private static String bound(Double bound) {
        return bound == null ? "*" : bound.toString();
    }
}
