package com.github.lechandonga.dataqualityvalidationservicejava.domain;

import java.util.List;

/**
 * 不可变契约版本。发布后只增不改，checksum 用于识别重复注册与历史复现。
 */
public record Contract(
        String datasetId,
        int version,
        List<FieldSpec> fields,
        String checksum,
        long createdAtMillis
) {
    public Contract {
        fields = List.copyOf(fields);
    }

    public FieldSpec field(String name) {
        return fields.stream()
                .filter(spec -> spec.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
