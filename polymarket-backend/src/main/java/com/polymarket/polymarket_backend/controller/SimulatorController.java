package com.polymarket.polymarket_backend.controller;

import com.polymarket.polymarket_backend.dto.ClosedPositionDTO;
import com.polymarket.polymarket_backend.dto.OpenPositionRequest;
import com.polymarket.polymarket_backend.dto.PortfolioValueDTO;
import com.polymarket.polymarket_backend.dto.PositionDTO;
import com.polymarket.polymarket_backend.dto.SimulatorStateDTO;
import com.polymarket.polymarket_backend.model.entity.PerformanceSnapshot;
import com.polymarket.polymarket_backend.service.PriceCacheService;
import com.polymarket.polymarket_backend.service.SimulatorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final SimulatorService simulatorService;
    private final PriceCacheService priceCacheService;

    public SimulatorController(SimulatorService simulatorService,
                               PriceCacheService priceCacheService) {
        this.simulatorService = simulatorService;
        this.priceCacheService = priceCacheService;
    }

    @PostMapping("/start")
    public SimulatorStateDTO startSession() {
        return simulatorService.startSession();
    }

    @PostMapping("/stop")
    public SimulatorStateDTO stopSession() {
        simulatorService.stopSession();
        try {
            return simulatorService.getState();
        } catch (IllegalStateException e) {
            return new SimulatorStateDTO();
        }
    }

    @GetMapping("/state")
    public SimulatorStateDTO getState() {
        return simulatorService.getState();
    }

    @PostMapping("/positions")
    public ResponseEntity<PositionDTO> openPosition(@Valid @RequestBody OpenPositionRequest request) {
        PositionDTO position = simulatorService.openPosition(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(position);
    }

    @GetMapping("/positions")
    public List<PositionDTO> getOpenPositions() {
        return simulatorService.getState().getPositions();
    }

    @DeleteMapping("/positions/{id}")
    public ClosedPositionDTO closePosition(@PathVariable String id) {
        return simulatorService.closePosition(id);
    }

    @GetMapping("/performance")
    public List<PerformanceSnapshot> getPerformance(@RequestParam(required = false) String range) {
        return simulatorService.getPerformanceHistory(range);
    }

    @GetMapping("/portfolio-value")
    public PortfolioValueDTO getPortfolioValue() {
        return simulatorService.getPortfolioValue();
    }

    @GetMapping("/cache")
    public Map<String, Double> getPriceCache() {
        return priceCacheService.getCacheContents();
    }

    @PostMapping("/cache/{marketId}/refresh")
    public Map<String, Double> refreshMarketPrice(@PathVariable String marketId) {
        priceCacheService.refreshPrice(marketId);
        return priceCacheService.getCacheContents();
    }
}
