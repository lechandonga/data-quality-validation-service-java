package com.github.lechandonga.dataqualityvalidationservicejava.support;

import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Record;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试对象构造夹具。
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static FieldConstraint field(String name, FieldType type) {
        FieldConstraint c = new FieldConstraint();
        c.setName(name);
        c.setType(type);
        return c;
    }

    public static Contract contract(FieldConstraint... fields) {
        Contract contract = new Contract();
        contract.setFields(new java.util.ArrayList<>(List.of(fields)));
        return contract;
    }

    public static Record record(String id, Map<String, Object> values) {
        Record record = new Record();
        record.setRecordId(id);
        record.setFields(new LinkedHashMap<>(values));
        return record;
    }

    public static Record record(String id, Object... kv) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            values.put((String) kv[i], kv[i + 1]);
        }
        return record(id, values);
    }
}
