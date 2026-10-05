package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 单个字段的契约约束。
 *
 * <p>约束语义：
 * <ul>
 *   <li>{@code required}：字段必填，缺失或 null 记为 REQUIRED 问题；</li>
 *   <li>{@code unique}：该字段值在整个数据集中必须唯一；</li>
 *   <li>{@code min}/{@code max}：数值型字段的闭区间；字符串型时表示长度区间；</li>
 *   <li>{@code maxAgeMillis}：TIMESTAMP 字段距校验时刻允许的最大延迟（毫秒）。</li>
 * </ul>
 */
public class FieldConstraint {
    private String name;
    private FieldType type;
    private boolean required;
    private boolean unique;
    private Double min;
    private Double max;
    private Long maxAgeMillis;

    public FieldConstraint() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FieldType getType() {
        return type;
    }

    public void setType(FieldType type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public Long getMaxAgeMillis() {
        return maxAgeMillis;
    }

    public void setMaxAgeMillis(Long maxAgeMillis) {
        this.maxAgeMillis = maxAgeMillis;
    }
}
