package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按字段预先统计值出现次数，唯一性规则据此一次建表、逐条查询。
 */
public class DuplicateIndex {

    private final Map<String, Map<String, Integer>> countsByField = new HashMap<>();

    public DuplicateIndex(List<String> uniqueFields, List<Map<String, Object>> records) {
        Map<String, Map<String, Integer>> counters = new HashMap<>();
        uniqueFields.forEach(field -> counters.put(field, new HashMap<>()));
        for (Map<String, Object> record : records) {
            for (String field : uniqueFields) {
                Object value = record.get(field);
                if (value != null && !(value instanceof String text && text.isBlank())) {
                    counters.get(field).merge(String.valueOf(value), 1, Integer::sum);
                }
            }
        }
        countsByField.putAll(counters);
    }

    public boolean isDuplicate(String field, Object value) {
        Map<String, Integer> counts = countsByField.get(field);
        if (counts == null || value == null) {
            return false;
        }
        return counts.getOrDefault(String.valueOf(value), 0) > 1;
    }
}
