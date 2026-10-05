package com.github.lechandonga.dataqualityvalidationservicejava.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据集契约（某一不可变版本）。注册后内容不再被修改；
 * 契约更新会生成新版本，历史任务永久绑定其提交时的契约快照。
 */
public class Contract {
    private int version;
    private List<FieldConstraint> fields = new ArrayList<>();
    private long createdAtMillis;

    public Contract() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<FieldConstraint> getFields() {
        return fields;
    }

    public void setFields(List<FieldConstraint> fields) {
        this.fields = fields;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }
}
