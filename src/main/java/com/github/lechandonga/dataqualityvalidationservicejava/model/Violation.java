package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 一条记录命中的单个契约违规。同一条记录可同时产生多条 Violation，
 * 每条通过 {@link ConstraintType} 明确区分原因。
 */
public class Violation {
    private String recordId;
    private String fieldName;
    private ConstraintType constraint;
    private String reason;
    private Object actualValue;

    public Violation() {
    }

    public Violation(String recordId, String fieldName, ConstraintType constraint, String reason, Object actualValue) {
        this.recordId = recordId;
        this.fieldName = fieldName;
        this.constraint = constraint;
        this.reason = reason;
        this.actualValue = actualValue;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public ConstraintType getConstraint() {
        return constraint;
    }

    public String getReason() {
        return reason;
    }

    public Object getActualValue() {
        return actualValue;
    }
}
