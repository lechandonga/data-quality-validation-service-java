package com.github.lechandonga.dataqualityvalidationservicejava.model;

/**
 * 服务对外错误码。调用方只看到错误码与安全说明，不接触底层异常。
 */
public enum ErrorCode {
    /** 请求体无法解析 */
    MALFORMED_REQUEST,
    /** 数据集契约自相矛盾或非法 */
    INVALID_CONTRACT,
    /** 引用的数据集/契约/任务不存在 */
    NOT_FOUND,
    /** 请求参数非法 */
    INVALID_ARGUMENT,
    /** 提交的数据记录非法（结构层面，如缺少 recordId） */
    INVALID_RECORD,
    /** 任务执行内部错误 */
    TASK_EXECUTION_ERROR
}
