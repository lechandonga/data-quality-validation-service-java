package com.github.lechandonga.dataqualityvalidationservicejava.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条待校验数据记录。{@code recordId} 为调用方提供的行标识，
 * 用于在结果中定位问题行；字段值以松散类型承载（String/Long/Double/Boolean）。
 */
public class Record {
    private String recordId;
    private Map<String, Object> fields = new LinkedHashMap<>();

    public Record() {
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }
}
