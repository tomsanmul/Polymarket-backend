package com.polymarket.polymarket_backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SchedulerService {

    private final SimulatorService simulatorService;

    public SchedulerService(SimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @Scheduled(fixedRate = 30000)
    public void autoSnapshot() {
        simulatorService.recordSnapshot();
    }
}
