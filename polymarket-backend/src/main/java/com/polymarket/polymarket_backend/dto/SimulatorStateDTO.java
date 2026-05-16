package com.polymarket.polymarket_backend.dto;

import java.util.List;

public class SimulatorStateDTO {

    private boolean enabled;
    private double balance;
    private double usedBalance;
    private List<PositionDTO> positions;
    private List<ClosedPositionDTO> history;

    public SimulatorStateDTO() {
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

    public List<PositionDTO> getPositions() {
        return positions;
    }

    public void setPositions(List<PositionDTO> positions) {
        this.positions = positions;
    }

    public List<ClosedPositionDTO> getHistory() {
        return history;
    }

    public void setHistory(List<ClosedPositionDTO> history) {
        this.history = history;
    }
}
