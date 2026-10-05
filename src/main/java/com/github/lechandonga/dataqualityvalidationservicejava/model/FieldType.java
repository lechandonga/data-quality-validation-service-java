package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 契约支持的字段数据类型。
 */
public enum FieldType {
    /** 字符串 */
    STRING,
    /** 整数（long） */
    INTEGER,
    /** 小数（double） */
    DECIMAL,
    /** 布尔 */
    BOOLEAN,
    /** 时间戳（epoch 毫秒数，或 ISO-8601 字符串） */
    TIMESTAMP
}
