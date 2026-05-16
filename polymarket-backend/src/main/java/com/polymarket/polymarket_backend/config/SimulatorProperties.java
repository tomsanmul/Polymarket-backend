package com.polymarket.polymarket_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private double defaultBalance = 100000;
    private long pricePollIntervalMs = 30000;
    private long snapshotThrottleMs = 10000;

    public double getDefaultBalance() {
        return defaultBalance;
    }

    public void setDefaultBalance(double defaultBalance) {
        this.defaultBalance = defaultBalance;
    }

    public long getPricePollIntervalMs() {
        return pricePollIntervalMs;
    }

    public void setPricePollIntervalMs(long pricePollIntervalMs) {
        this.pricePollIntervalMs = pricePollIntervalMs;
    }

    public long getSnapshotThrottleMs() {
        return snapshotThrottleMs;
    }

    public void setSnapshotThrottleMs(long snapshotThrottleMs) {
        this.snapshotThrottleMs = snapshotThrottleMs;
    }
}
