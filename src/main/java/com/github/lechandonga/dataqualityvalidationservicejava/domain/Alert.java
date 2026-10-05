package com.github.lechandonga.dataqualityvalidationservicejava.domain;

import java.util.Map;

/**
 * 一条告警。dedupeKey 相同且处于抑制窗口内的重复触发只累加 suppressedCount。
 */
public record Alert(
        String id,
        String datasetId,
        AlertType type,
        String dedupeKey,
        long createdAtMillis,
        long lastTriggeredAtMillis,
        int suppressedCount,
        boolean active,
        Map<String, Object> evidence
) {
    public Alert {
        evidence = Map.copyOf(evidence);
    }

    public Alert withSuppressed(long triggeredAtMillis) {
        return new Alert(id, datasetId, type, dedupeKey, createdAtMillis, triggeredAtMillis,
                suppressedCount + 1, active, evidence);
    }

    public Alert deactivated(long atMillis) {
        return new Alert(id, datasetId, type, dedupeKey, createdAtMillis, atMillis,
                suppressedCount, false, evidence);
    }
}
