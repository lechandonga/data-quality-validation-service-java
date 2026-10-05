package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 告警状态。持续异常期间通过 FIRING 去重收敛，恢复后转为 RESOLVED。
 */
public enum AlertStatus {
    /** 首次触发 */
    FIRING,
    /** 仍在异常窗口内，重复抖动被抑制 */
    SUPPRESSED,
    /** 指标恢复正常，对应活动告警关闭 */
    RESOLVED
}
