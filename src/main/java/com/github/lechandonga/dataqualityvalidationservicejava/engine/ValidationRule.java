package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import com.github.lechandonga.dataqualityvalidationservicejava.model.ConstraintType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

import java.util.List;

/**
 * 单条校验规则。实现类只关注本约束的判定；
 * 抛异常由引擎统一兜底归一化，规则内部不需要 try/catch。
 */
public interface ValidationRule {

    /** 规则对应的约束类型，用于错误归因。 */
    ConstraintType constraintType();

    /** 该规则是否适用于给定字段约束。 */
    boolean appliesTo(FieldConstraint constraint);

    /** 整批数据开始前的预处理（如唯一性统计），默认无操作。 */
    default void prepare(List<Record> batch, List<FieldConstraint> constraints) {
    }

    /** 检查单条记录的单个字段，结论写入 {@code output}。 */
    void check(Record record, FieldConstraint constraint, RuleOutput output, long nowMillis);
}
