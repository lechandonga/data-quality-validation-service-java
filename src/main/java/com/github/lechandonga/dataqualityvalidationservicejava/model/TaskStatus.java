package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 校验任务生命周期状态。
 */
public enum TaskStatus {
    /** 已提交，等待/正在执行 */
    PENDING,
    /** 执行完成（可能包含违规与规则错误，但任务本身执行完毕） */
    COMPLETED,
    /** 任务执行过程中发生不可恢复的系统错误 */
    FAILED
}
