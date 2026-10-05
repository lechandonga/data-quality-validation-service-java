package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 单字段约束类型，决定校验结果中问题原因的分类。
 */
public enum ConstraintType {
    /** 类型不匹配 */
    TYPE,
    /** 必填但缺失 */
    REQUIRED,
    /** 取值不在允许区间 */
    RANGE,
    /** 全数据集内不唯一 */
    UNIQUENESS,
    /** 业务时间超过时效阈值 */
    FRESHNESS
}
