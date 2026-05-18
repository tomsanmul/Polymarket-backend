package com.polymarket.polymarket_backend.service;

import com.polymarket.polymarket_backend.model.PolyRouterMarket;
import com.polymarket.polymarket_backend.model.PriceDetail;
import com.polymarket.polymarket_backend.repository.PositionRepository;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PriceCacheService {

    private static final Logger log = LoggerFactory.getLogger(PriceCacheService.class);
    public static final String DUMMY_MARKET_ID = "dummy_market";

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
                .filter(id -> !DUMMY_MARKET_ID.equals(id))
                .toList();

        if (!marketIds.isEmpty()) {
            log.info("Refreshing prices for {} market(s): {}", marketIds.size(), marketIds);

            try {
                List<PolyRouterMarket> activeMarkets = polyRouterMarketService.getActiveMarkets().collectList().block();
                if (activeMarkets != null) {
                    for (String marketId : marketIds) {
                        boolean found = false;
                        for (PolyRouterMarket m : activeMarkets) {
                            if (marketId.equals(m.getId()) || marketId.equals(m.getPlatformId())) {
                                if (m.getCurrentPrices() != null && !m.getCurrentPrices().isEmpty()) {
                                    cachePrices(marketId, m.getCurrentPrices());
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) {
                            refreshPrice(marketId);
                        }
                    }
                } else {
                    for (String marketId : marketIds) {
                        refreshPrice(marketId);
                    }
                }
            } catch (Exception e) {
                log.warn("Batch price fetch failed, falling back to individual lookups: {}", e.getMessage());
                for (String marketId : marketIds) {
                    refreshPrice(marketId);
                }
            }
        }

        refreshDummyMarket();
    }

    public void refreshDummyMarket() {
        double yesPrice = ThreadLocalRandom.current().nextDouble(0.01, 0.99);
        double noPrice = Math.round((1.0 - yesPrice) * 1000.0) / 1000.0;
        yesPrice = Math.round(yesPrice * 1000.0) / 1000.0;
        String yesKey = DUMMY_MARKET_ID + ":1";
        String noKey = DUMMY_MARKET_ID + ":0";
        Double oldYes = priceCache.put(yesKey, yesPrice);
        Double oldNo = priceCache.put(noKey, noPrice);
        log.info("DUMMY market={} yes old={} new={} no old={} new={}",
                DUMMY_MARKET_ID,
                oldYes != null ? String.format("%.3f", oldYes) : "null",
                String.format("%.3f", yesPrice),
                oldNo != null ? String.format("%.3f", oldNo) : "null",
                String.format("%.3f", noPrice));
    }

    public void refreshPrice(String marketId) {
        if (DUMMY_MARKET_ID.equals(marketId)) {
            refreshDummyMarket();
            return;
        }
        try {
            PolyRouterMarket market = polyRouterMarketService.getMarketById(marketId).block();
            if (market != null && market.getCurrentPrices() != null && !market.getCurrentPrices().isEmpty()) {
                cachePrices(marketId, market.getCurrentPrices());
                return;
            }

            log.warn("Single-market lookup returned no prices for {}; trying list endpoint fallback", marketId);
            List<PolyRouterMarket> activeMarkets = polyRouterMarketService.getActiveMarkets().collectList().block();
            if (activeMarkets != null) {
                for (PolyRouterMarket m : activeMarkets) {
                    if (marketId.equals(m.getId()) || marketId.equals(m.getPlatformId())) {
                        if (m.getCurrentPrices() != null && !m.getCurrentPrices().isEmpty()) {
                            cachePrices(marketId, m.getCurrentPrices());
                            log.info("Found prices via list endpoint fallback for market {} (matched {})",
                                    marketId, marketId.equals(m.getId()) ? "id" : "platformId");
                            return;
                        }
                    }
                }
                log.warn("Market {} not found in active markets list ({} total)", marketId, activeMarkets.size());
            }
        } catch (Exception e) {
            log.warn("Failed to refresh price for market {}: {}", marketId, e.getMessage());
        }
    }

    public Map<String, Double> getCacheContents() {
        return Map.copyOf(priceCache);
    }

    private void cachePrices(String marketId, Map<String, PriceDetail> prices) {
        for (Map.Entry<String, PriceDetail> entry : prices.entrySet()) {
            String outcomeKey = entry.getKey().toLowerCase();
            String canonicalIndex = switch (outcomeKey) {
                case "yes" -> "1";
                case "no" -> "0";
                default -> outcomeKey;
            };
            double newPrice = entry.getValue().getPrice();
            String k = marketId + ":" + canonicalIndex;
            Double oldPrice = priceCache.put(k, newPrice);
            log.info("Price {} market={} outcomeKey={} index={} old={} new={}",
                    oldPrice != null ? "UPDATE" : "CACHE",
                    marketId, entry.getKey(), canonicalIndex,
                    oldPrice != null ? String.format("%.4f", oldPrice) : "null",
                    String.format("%.4f", newPrice));
        }
        log.info("Refreshed {} prices for market {}", prices.size(), marketId);
    }

    private static String cacheKey(String marketId, String side) {
        String outcomeIndex = "YES".equalsIgnoreCase(side) ? "1" : "0";
        return marketId + ":" + outcomeIndex;
    }
}
