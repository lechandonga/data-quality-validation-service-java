package com.github.lechandonga.dataqualityvalidationservicejava.web.dto;

import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

import java.util.List;

/**
 * 数据接入请求体。
 */
public class LoadRecordsRequest {
    private List<Record> records;

    public List<Record> getRecords() {
        return records;
    }

    public void setRecords(List<Record> records) {
        this.records = records;
    }
}
