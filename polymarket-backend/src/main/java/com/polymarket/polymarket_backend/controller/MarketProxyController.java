package com.polymarket.polymarket_backend.controller;

import com.polymarket.polymarket_backend.dto.NormalizedMarketDTO;
import com.polymarket.polymarket_backend.model.PolyRouterMarket;
import com.polymarket.polymarket_backend.model.PriceDetail;
import com.polymarket.polymarket_backend.service.PolyRouterMarketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/markets")
public class MarketProxyController {

    private final PolyRouterMarketService polyRouterMarketService;

    public MarketProxyController(PolyRouterMarketService polyRouterMarketService) {
        this.polyRouterMarketService = polyRouterMarketService;
    }

    @GetMapping
    public Flux<NormalizedMarketDTO> getAllMarkets(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status) {
        Flux<PolyRouterMarket> markets;
        if (query != null) {
            markets = polyRouterMarketService.getMarketsByQuery(query, status);
        } else if (category != null) {
            markets = polyRouterMarketService.getMarketsByCategory(category);
        } else {
            markets = polyRouterMarketService.getAllMarkets();
        }
        return markets.map(this::toNormalized);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<NormalizedMarketDTO>> getMarketById(@PathVariable String id) {
        return polyRouterMarketService.getMarketById(id)
                .map(m -> ResponseEntity.ok(toNormalized(m)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/price")
    public Mono<ResponseEntity<Map<String, Double>>> getMarketPrice(@PathVariable String id) {
        return polyRouterMarketService.getMarketById(id)
                .map(this::toPriceResponse)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private ResponseEntity<Map<String, Double>> toPriceResponse(PolyRouterMarket market) {
        if (market.getCurrentPrices() == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Double> prices = market.getCurrentPrices().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getPrice()));
        return ResponseEntity.ok(prices);
    }

    @GetMapping("/{id}/trades")
    public Mono<ResponseEntity<String>> getMarketTrades(@PathVariable String id) {
        return polyRouterMarketService.getMarketById(id)
                .map(m -> ResponseEntity.ok("{\"message\":\"Trade history proxy - not yet implemented\"}"))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private NormalizedMarketDTO toNormalized(PolyRouterMarket market) {
        NormalizedMarketDTO dto = new NormalizedMarketDTO();
        dto.setId(market.getId());
        dto.setQuestion(market.getTitle());
        dto.setVolume(market.getVolumeTotal() != null ? market.getVolumeTotal() : 0.0);
        dto.setVolume24hr(market.getVolume24h() != null ? market.getVolume24h() : 0.0);
        dto.setLiquidity(market.getLiquidityScore() != null ? market.getLiquidityScore() : 0.0);
        dto.setImage(market.getImageUrl());
        dto.setEndDate(market.getTradingEnd());
        dto.setStatus(market.getStatus() != null ? market.getStatus().name().toLowerCase() : null);

        if (market.getCurrentPrices() != null) {
            List<String> prices = new ArrayList<>();
            List<String> outcomes = new ArrayList<>();
            for (Map.Entry<String, PriceDetail> entry : market.getCurrentPrices().entrySet()) {
                prices.add(String.valueOf(entry.getValue().getPrice()));
                outcomes.add(entry.getKey());
            }
            dto.setPrices(prices);
            dto.setOutcomes(outcomes);
        } else {
            dto.setPrices(Collections.emptyList());
            dto.setOutcomes(Collections.emptyList());
        }

        return dto;
    }
}
