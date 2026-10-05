package com.github.lechandonga.dataqualityvalidationservicejava.support;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleOutput;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationRule;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

import java.util.List;

/**
 * 超时注入规则：检查时长超过引擎允许的单规则时限。
 */
public class SlowRule implements ValidationRule {

    private final long sleepMillis;

    public SlowRule(long sleepMillis) {
        this.sleepMillis = sleepMillis;
    }

    @Override
    public ConstraintType constraintType() {
        return ConstraintType.RANGE;
    }

    @Override
    public boolean appliesTo(FieldConstraint constraint) {
        return constraint.getMin() != null;
    }

    @Override
    public void check(Record record, FieldConstraint constraint, RuleOutput output, long nowMillis) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
