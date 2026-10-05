package com.github.lechandonga.dataqualityvalidationservicejava.domain;

/**
 * 任务生命周期。单条规则失败不会导致任务失败，批次跑完即为 COMPLETED。
 */
public enum TaskState {
    PENDING,
    RUNNING,
    COMPLETED
}
