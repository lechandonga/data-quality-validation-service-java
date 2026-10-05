package com.github.lechandonga.dataqualityvalidationservicejava.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 告警判定参数（可通过 application.properties 覆盖）：
 * <ul>
 *   <li>{code windowMillis}：当前窗口与基线窗口长度；</li>
 *   <li>ratio-threshold：当前窗口问题率绝对阈值；</li>
 *   <li>baseline-ratio：基线问题率，当前窗口高于“基线×倍数”且超过绝对阈值才告警；</li>
 *   <li>suppress-millis：同一数据集活动告警的去重抑制窗口；</li>
 *   <li>baseline-multiplier：相对基线的放大倍数。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "dq.alert")
public class AlertProperties {
    private long windowMillis = 60_000L;
    private double ratioThreshold = 0.5d;
    private double baselineRatio = 0.1d;
    private long suppressMillis = 300_000L;
    private double baselineMultiplier = 2.0d;

    public long getWindowMillis() {
        return windowMillis;
    }

    public void setWindowMillis(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public double getRatioThreshold() {
        return ratioThreshold;
    }

    public void setRatioThreshold(double ratioThreshold) {
        this.ratioThreshold = ratioThreshold;
    }

    public double getBaselineRatio() {
        return baselineRatio;
    }

    public void setBaselineRatio(double baselineRatio) {
        this.baselineRatio = baselineRatio;
    }

    public long getSuppressMillis() {
        return suppressMillis;
    }

    public void setSuppressMillis(long suppressMillis) {
        this.suppressMillis = suppressMillis;
    }

    public double getBaselineMultiplier() {
        return baselineMultiplier;
    }

    public void setBaselineMultiplier(double baselineMultiplier) {
        this.baselineMultiplier = baselineMultiplier;
    }
}
