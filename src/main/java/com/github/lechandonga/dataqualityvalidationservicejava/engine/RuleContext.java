package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldSpec;

import java.util.Map;

/**
 * 规则求值上下文。duplicateIndex 为唯一性规则提供整批数据视图。
 */
public record RuleContext(
        Contract contract,
        FieldSpec field,
        Map<String, Object> record,
        DuplicateIndex duplicateIndex,
        long evaluatedAtMillis
) {
}
