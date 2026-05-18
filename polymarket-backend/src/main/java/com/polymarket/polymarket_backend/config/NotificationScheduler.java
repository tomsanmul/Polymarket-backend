package com.polymarket.polymarket_backend.config;

import com.polymarket.polymarket_backend.service.AlertService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private final AlertService alertService;

    public NotificationScheduler(AlertService alertService) {
        this.alertService = alertService;
    }

    @Scheduled(fixedRate = 30000)
    public void checkAlerts() {
        alertService.checkAlerts();
    }
}
