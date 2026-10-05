package com.github.lechandonga.dataqualityvalidationservicejava.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 可手动推进的固定时钟，用于确定性地测试时效与时间窗口告警。
 */
public class MutableClock extends Clock {
    private long millis;

    public MutableClock(long millis) {
        this.millis = millis;
    }

    public void setMillis(long millis) {
        this.millis = millis;
    }

    public void advanceMillis(long delta) {
        this.millis += delta;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis);
    }

    @Override
    public long millis() {
        return millis;
    }
}
