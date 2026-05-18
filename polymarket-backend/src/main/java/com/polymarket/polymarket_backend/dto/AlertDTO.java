package com.polymarket.polymarket_backend.dto;

import com.polymarket.polymarket_backend.enums.AlertType;
import com.polymarket.polymarket_backend.model.entity.Alert;
import java.time.Instant;

public class AlertDTO {

    private Long id;
    private Long userId;
    private String marketId;
    private String marketQuestion;
    private AlertType type;
    private String condition;
    private Double targetPrice;
    private Double targetPercent;
    private Double referencePrice;
    private boolean triggered;
    private boolean active;
    private Instant createdAt;

    public AlertDTO() {
    }

    public static AlertDTO fromEntity(Alert a) {
        AlertDTO dto = new AlertDTO();
        dto.setId(a.getId());
        dto.setUserId(a.getUserId());
        dto.setMarketId(a.getMarketId());
        dto.setMarketQuestion(a.getMarketQuestion());
        dto.setType(a.getType());
        dto.setCondition(a.getCondition());
        dto.setTargetPrice(a.getTargetPrice());
        dto.setTargetPercent(a.getTargetPercent());
        dto.setReferencePrice(a.getReferencePrice());
        dto.setTriggered(a.isTriggered());
        dto.setActive(a.isActive());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public AlertType getType() {
        return type;
    }

    public void setType(AlertType type) {
        this.type = type;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public Double getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(Double targetPrice) {
        this.targetPrice = targetPrice;
    }

    public Double getTargetPercent() {
        return targetPercent;
    }

    public void setTargetPercent(Double targetPercent) {
        this.targetPercent = targetPercent;
    }

    public Double getReferencePrice() {
        return referencePrice;
    }

    public void setReferencePrice(Double referencePrice) {
        this.referencePrice = referencePrice;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
