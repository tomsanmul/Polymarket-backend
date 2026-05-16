package com.polymarket.polymarket_backend.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulator_sessions")
public class SimulatorSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private boolean enabled;

    private double balance;

    private double usedBalance;

    private Instant createdAt;

    private Long lastSnapshotTime;

    public SimulatorSession() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getUsedBalance() {
        return usedBalance;
    }

    public void setUsedBalance(double usedBalance) {
        this.usedBalance = usedBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getLastSnapshotTime() {
        return lastSnapshotTime;
    }

    public void setLastSnapshotTime(Long lastSnapshotTime) {
        this.lastSnapshotTime = lastSnapshotTime;
    }
}
