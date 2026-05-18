package com.polymarket.polymarket_backend.service;

import com.polymarket.polymarket_backend.dto.AlertDTO;
import com.polymarket.polymarket_backend.dto.NotificationMessage;
import com.polymarket.polymarket_backend.enums.AlertType;
import com.polymarket.polymarket_backend.model.entity.Alert;
import com.polymarket.polymarket_backend.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final PriceCacheService priceCacheService;
    private final NotificationWebSocketHandler webSocketHandler;

    public AlertService(AlertRepository alertRepository,
                        PriceCacheService priceCacheService,
                        NotificationWebSocketHandler webSocketHandler) {
        this.alertRepository = alertRepository;
        this.priceCacheService = priceCacheService;
        this.webSocketHandler = webSocketHandler;
    }

    public AlertDTO createAlert(AlertDTO dto) {
        Alert alert = new Alert();
        alert.setUserId(dto.getUserId());
        alert.setMarketId(dto.getMarketId());
        alert.setMarketQuestion(dto.getMarketQuestion());
        alert.setType(dto.getType());
        alert.setCondition(dto.getCondition() != null ? dto.getCondition() : "ABOVE");
        alert.setTargetPrice(dto.getTargetPrice());
        alert.setTargetPercent(dto.getTargetPercent());
        alert.setActive(true);
        alert.setTriggered(false);
        alert.setCreatedAt(Instant.now());

        if (dto.getType() == AlertType.PERCENTAGE_CHANGE) {
            Double currentPrice = priceCacheService.getPrice(dto.getMarketId(), "YES");
            if (currentPrice == null) currentPrice = 0.5;
            alert.setReferencePrice(currentPrice);
        }

        alert = alertRepository.save(alert);
        return AlertDTO.fromEntity(alert);
    }

    @Transactional(readOnly = true)
    public List<AlertDTO> getUserAlerts(Long userId) {
        return alertRepository.findByUserId(userId).stream()
                .map(AlertDTO::fromEntity)
                .toList();
    }

    public AlertDTO updateAlert(Long id, AlertDTO dto) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Alert not found: " + id));
        if (dto.getTargetPrice() != null) alert.setTargetPrice(dto.getTargetPrice());
        if (dto.getTargetPercent() != null) alert.setTargetPercent(dto.getTargetPercent());
        if (dto.getCondition() != null) alert.setCondition(dto.getCondition());
        if (dto.getMarketQuestion() != null) alert.setMarketQuestion(dto.getMarketQuestion());
        alert.setActive(dto.isActive());
        alert.setTriggered(false);
        alert = alertRepository.save(alert);
        return AlertDTO.fromEntity(alert);
    }

    public void deleteAlert(Long id) {
        alertRepository.deleteById(id);
    }

    public void checkAlerts() {
        List<Alert> activeAlerts = alertRepository.findByActiveTrueAndTriggeredFalse();
        if (activeAlerts.isEmpty()) return;

        log.debug("Checking {} active alerts", activeAlerts.size());

        for (Alert alert : activeAlerts) {
            boolean shouldTrigger = false;
            String title = null;
            String body = null;

            Double currentPrice = priceCacheService.getPrice(alert.getMarketId(), "YES");
            if (currentPrice == null) {
                if (alert.getType() == AlertType.MARKET_CLOSE) {
                    shouldTrigger = true;
                    title = "Mercado cerrado";
                    body = "El mercado \"" + alert.getMarketQuestion() + "\" ya no está disponible.";
                }
                continue;
            }

            switch (alert.getType()) {
                case PRICE_TARGET -> {
                    if (alert.getTargetPrice() == null) continue;
                    boolean conditionMet = "ABOVE".equalsIgnoreCase(alert.getCondition())
                            ? currentPrice >= alert.getTargetPrice()
                            : currentPrice <= alert.getTargetPrice();
                    if (conditionMet) {
                        shouldTrigger = true;
                        title = "Precio objetivo alcanzado";
                        body = alert.getMarketQuestion() + " ha llegado a " + String.format("%.1f", currentPrice * 100) + "%";
                    }
                }
                case PERCENTAGE_CHANGE -> {
                    if (alert.getReferencePrice() == null || alert.getReferencePrice() == 0) continue;
                    double change = Math.abs((currentPrice - alert.getReferencePrice()) / alert.getReferencePrice() * 100);
                    if (alert.getTargetPercent() != null && change >= alert.getTargetPercent()) {
                        shouldTrigger = true;
                        title = "Cambio de precio significativo";
                        body = alert.getMarketQuestion() + " ha cambiado un " + String.format("%.1f", change) + "%";
                    }
                }
                case MARKET_CLOSE -> {
                }
                case PREDICTION_FULFILLED -> {
                }
            }

            if (shouldTrigger) {
                alert.setTriggered(true);
                alertRepository.save(alert);

                NotificationMessage notification = new NotificationMessage("ALERT_TRIGGERED", title, body);
                notification.setMarketId(alert.getMarketId());
                notification.setMarketQuestion(alert.getMarketQuestion());
                notification.setAlertType(alert.getType());
                notification.setCurrentPrice(currentPrice);

                webSocketHandler.sendToUser(alert.getUserId(), notification);
                log.info("Alert triggered: userId={}, marketId={}, type={}", alert.getUserId(), alert.getMarketId(), alert.getType());
            }
        }
    }
}
