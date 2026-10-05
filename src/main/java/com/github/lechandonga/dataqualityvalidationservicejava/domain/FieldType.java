package com.github.lechandonga.dataqualityvalidationservicejava.domain;

import java.util.Optional;

/**
 * 契约支持的字段类型。
 */
public enum FieldType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    TIMESTAMP;

    public static Optional<FieldType> fromName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
