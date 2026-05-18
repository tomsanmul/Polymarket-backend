package com.polymarket.polymarket_backend.service;

import com.polymarket.polymarket_backend.model.PolyRouterMarket;
import com.polymarket.polymarket_backend.model.PriceDetail;
import com.polymarket.polymarket_backend.repository.PositionRepository;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PriceCacheService {

    private static final Logger log = LoggerFactory.getLogger(PriceCacheService.class);

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

        if (marketIds.isEmpty()) {
            log.debug("No open positions to refresh prices for");
            return;
        }

        log.info("Refreshing prices for {} market(s): {}", marketIds.size(), marketIds);
        for (String marketId : marketIds) {
            refreshPrice(marketId);
        }
    }

    public void refreshPrice(String marketId) {
        try {
            PolyRouterMarket market = polyRouterMarketService.getMarketById(marketId).block();
            if (market == null) {
                log.warn("PolyRouter returned null for market {}", marketId);
                return;
            }
            if (market.getCurrentPrices() == null || market.getCurrentPrices().isEmpty()) {
                log.warn("PolyRouter returned no currentPrices for market {}", marketId);
                return;
            }
            for (Map.Entry<String, PriceDetail> entry : market.getCurrentPrices().entrySet()) {
                String outcomeIndex = entry.getKey();
                double price = entry.getValue().getPrice();
                String cacheKey = marketId + ":" + outcomeIndex;
                priceCache.put(cacheKey, price);
                log.debug("Cached price for {} = {}", cacheKey, price);
            }
            log.info("Refreshed {} prices for market {}", market.getCurrentPrices().size(), marketId);
        } catch (Exception e) {
            log.warn("Failed to refresh price for market {}: {}", marketId, e.getMessage());
        }
    }

    public Map<String, Double> getCacheContents() {
        return Map.copyOf(priceCache);
    }

    private static String cacheKey(String marketId, String side) {
        String outcomeIndex = "YES".equalsIgnoreCase(side) ? "1" : "0";
        return marketId + ":" + outcomeIndex;
    }
}
