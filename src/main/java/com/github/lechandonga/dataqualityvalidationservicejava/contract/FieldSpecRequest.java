package com.github.lechandonga.dataqualityvalidationservicejava.contract;

/**
 * 注册契约时的单个字段请求。
 */
public record FieldSpecRequest(
        String name,
        String type,
        Boolean required,
        Double min,
        Double max,
        Boolean unique,
        Boolean freshnessCheck,
        Long maxAgeMillis
) {
}
