package com.polymarket.polymarket_backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.polymarket.polymarket_backend.model.PolyRouterMarket;
import com.polymarket.polymarket_backend.model.PriceDetail;
import com.polymarket.polymarket_backend.repository.PositionRepository;
import com.polymarket.polymarket_backend.service.PolyRouterMarketService;
import com.polymarket.polymarket_backend.service.PriceCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class PriceCacheServiceTest {

    @Mock private PositionRepository positionRepository;
    @Mock private PolyRouterMarketService polyRouterMarketService;

    private PriceCacheService priceCacheService;

    @BeforeEach
    void setUp() {
        priceCacheService = new PriceCacheService(positionRepository, polyRouterMarketService);
    }

    @Test
    void getPrice_returnsNullWhenNotCached() {
        Double price = priceCacheService.getPrice("m1", "YES");
        assertNull(price);
    }

    @Test
    void refreshPrice_populatesCache() {
        PolyRouterMarket market = new PolyRouterMarket();
        PriceDetail yesPrice = new PriceDetail();
        yesPrice.setPrice(0.65);
        PriceDetail noPrice = new PriceDetail();
        noPrice.setPrice(0.35);
        market.setCurrentPrices(Map.of("0", noPrice, "1", yesPrice));

        when(polyRouterMarketService.getMarketById("m1")).thenReturn(Mono.just(market));

        priceCacheService.refreshPrice("m1");

        assertEquals(0.65, priceCacheService.getPrice("m1", "YES"));
        assertEquals(0.35, priceCacheService.getPrice("m1", "NO"));
    }
}
