package com.github.lechandonga.dataqualityvalidationservicejava.task;

/**
 * 历史结论重放结果。identical=true 表示与时间、任务 ID 无关的问题摘要完全一致。
 */
public record ReplayResult(
        String taskId,
        int contractVersion,
        String contractChecksum,
        String originalDigest,
        String replayedDigest,
        boolean identical
) {
}
