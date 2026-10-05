package com.github.lechandonga.dataqualityvalidationservicejava.domain;

/**
 * 不可变字段契约。min/max 仅对数值字段有意义；
 * freshnessCheck/maxAgeMillis 仅对 TIMESTAMP 字段有意义。
 */
public record FieldSpec(
        String name,
        FieldType type,
        boolean required,
        Double min,
        Double max,
        boolean unique,
        boolean freshnessCheck,
        Long maxAgeMillis
) {
    public FieldSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name must not be blank");
        }
    }

    public boolean numeric() {
        return type == FieldType.INTEGER || type == FieldType.DECIMAL;
    }
}
