package com.github.lechandonga.dataqualityvalidationservicejava.support;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.RuleOutput;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationRule;
import com.github.lechandonga.dataqualityvalidationservicejava.engine.rules.UniqueRule;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

import java.util.List;

/**
 * 故障注入规则：伪装成唯一性规则，prepare/check 时抛出底层异常，
 * 模拟规则内部错误（异常信息含敏感内容，验证不被透传）。
 */
public class SabotageRule implements ValidationRule {

    private final UniqueRule delegate = new UniqueRule();

    @Override
    public ConstraintType constraintType() {
        return ConstraintType.UNIQUENESS;
    }

    @Override
    public boolean appliesTo(FieldConstraint constraint) {
        return delegate.appliesTo(constraint);
    }

    @Override
    public void prepare(List<Record> batch, List<FieldConstraint> constraints) {
        throw new IllegalStateException("simulated internal failure; dbPassword=S3CR3T; /var/data/npe.stack");
    }

    @Override
    public void check(Record record, FieldConstraint constraint, RuleOutput output, long nowMillis) {
        throw new ArithmeticException("simulated rule crash; token=AKIA-LEAK; divide by zero");
    }
}
