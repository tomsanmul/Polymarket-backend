package com.polymarket.polymarket_backend.service;

import com.polymarket.polymarket_backend.model.PolyRouterMarket;
import com.polymarket.polymarket_backend.model.PriceDetail;
import com.polymarket.polymarket_backend.repository.PositionRepository;
import jakarta.annotation.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PriceCacheService {

    private final ConcurrentHashMap<String, Double> priceCache = new ConcurrentHashMap<>();
    private final PositionRepository positionRepository;
    private final PolyRouterMarketService polyRouterMarketService;

    public PriceCacheService(PositionRepository positionRepository,
                             PolyRouterMarketService polyRouterMarketService) {
        this.positionRepository = positionRepository;
        this.polyRouterMarketService = polyRouterMarketService;
    }

    @Nullable
    public Double getPrice(String marketId, String side) {
        String key = cacheKey(marketId, side);
        return priceCache.get(key);
    }

    @Scheduled(fixedRate = 30000)
    public void refreshTrackedPrices() {
        var openPositions = positionRepository.findByClosedFalse();
        var marketIds = openPositions.stream()
                .map(p -> p.getMarketId())
                .distinct()
                .toList();

        for (String marketId : marketIds) {
            refreshPrice(marketId);
        }
    }

    public void refreshPrice(String marketId) {
        try {
            PolyRouterMarket market = polyRouterMarketService.getMarketById(marketId).block();
            if (market == null || market.getCurrentPrices() == null) {
                return;
            }
            for (Map.Entry<String, PriceDetail> entry : market.getCurrentPrices().entrySet()) {
                String outcomeIndex = entry.getKey();
                double price = entry.getValue().getPrice();
                priceCache.put(marketId + ":" + outcomeIndex, price);
            }
        } catch (Exception e) {
            // Silently skip failed fetches
        }
    }

    private static String cacheKey(String marketId, String side) {
        String outcomeIndex = "YES".equalsIgnoreCase(side) ? "1" : "0";
        return marketId + ":" + outcomeIndex;
    }
}
