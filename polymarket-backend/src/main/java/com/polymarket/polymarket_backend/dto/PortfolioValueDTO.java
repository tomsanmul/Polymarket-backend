package com.polymarket.polymarket_backend.dto;

public class PortfolioValueDTO {

    private double portfolioValue;
    private double totalPnl;
    private double openPositionsValue;
    private double balance;

    public PortfolioValueDTO() {
    }

    public PortfolioValueDTO(double portfolioValue, double totalPnl, double openPositionsValue, double balance) {
        this.portfolioValue = portfolioValue;
        this.totalPnl = totalPnl;
        this.openPositionsValue = openPositionsValue;
        this.balance = balance;
    }

    public double getPortfolioValue() {
        return portfolioValue;
    }

    public void setPortfolioValue(double portfolioValue) {
        this.portfolioValue = portfolioValue;
    }

    public double getTotalPnl() {
        return totalPnl;
    }

    public void setTotalPnl(double totalPnl) {
        this.totalPnl = totalPnl;
    }

    public double getOpenPositionsValue() {
        return openPositionsValue;
    }

    public void setOpenPositionsValue(double openPositionsValue) {
        this.openPositionsValue = openPositionsValue;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
