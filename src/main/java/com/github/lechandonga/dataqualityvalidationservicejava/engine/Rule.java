package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationIssue;

import java.util.Optional;

/**
 * 字段级规则扩展点。实现方抛出的任何异常都会被引擎捕获并归一化。
 */
public interface Rule {

    String name();

    /**
     * 该规则是否适用于此字段（例如区间规则仅适用于数值字段）。
     */
    boolean appliesTo(RuleContext context);

    /**
     * 返回空表示通过；返回问题表示命中。
     */
    Optional<ValidationIssue> evaluate(RuleContext context);
}
