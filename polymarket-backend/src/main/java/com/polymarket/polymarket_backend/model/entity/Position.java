package com.polymarket.polymarket_backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "simulator_positions")
public class Position {

    @Id
    private String id;

    @Column(nullable = false)
    private String marketId;

    @Column(length = 1000)
    private String marketQuestion;

    @Column(nullable = false)
    private String side;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private double entryPrice;

    @Column(nullable = false)
    private int shares;

    private String outcome;

    @Column(nullable = false)
    private long timestamp;

    private boolean closed;

    private Double closePrice;

    private Double closeValue;

    private Double pnl;

    private Long closeTime;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private SimulatorSession session;

    public Position() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMarketId() {
        return marketId;
    }

    public void setMarketId(String marketId) {
        this.marketId = marketId;
    }

    public String getMarketQuestion() {
        return marketQuestion;
    }

    public void setMarketQuestion(String marketQuestion) {
        this.marketQuestion = marketQuestion;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(double entryPrice) {
        this.entryPrice = entryPrice;
    }

    public int getShares() {
        return shares;
    }

    public void setShares(int shares) {
        this.shares = shares;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public Double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(Double closePrice) {
        this.closePrice = closePrice;
    }

    public Double getCloseValue() {
        return closeValue;
    }

    public void setCloseValue(Double closeValue) {
        this.closeValue = closeValue;
    }

    public Double getPnl() {
        return pnl;
    }

    public void setPnl(Double pnl) {
        this.pnl = pnl;
    }

    public Long getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(Long closeTime) {
        this.closeTime = closeTime;
    }

    public SimulatorSession getSession() {
        return session;
    }

    public void setSession(SimulatorSession session) {
        this.session = session;
    }
}
