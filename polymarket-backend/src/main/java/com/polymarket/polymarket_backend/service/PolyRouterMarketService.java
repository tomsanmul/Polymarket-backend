package com.polymarket.polymarket_backend.service;

import com.polymarket.polymarket_backend.model.MarketListResponse;
import com.polymarket.polymarket_backend.model.PolyRouterMarket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Collections;

@Service
public class PolyRouterMarketService {

    private final WebClient webClient;

    public PolyRouterMarketService(WebClient.Builder webClientBuilder,
                                   @Value("${polyrouter.api-key:}") String apiKey) {
        this.webClient = webClientBuilder
                .baseUrl("https://api-v2.polyrouter.io")
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    private static final String PLATFORM_PARAM = "&platform=polymarket";

    public Flux<PolyRouterMarket> getAllMarkets() {
        return webClient.get()
                .uri("/markets?limit=50" + PLATFORM_PARAM)
                .retrieve()
                .bodyToMono(MarketListResponse.class)
                .flatMapMany(response -> Flux.fromIterable(
                        response.getMarkets() != null ? response.getMarkets() : Collections.emptyList()));
    }

    public Mono<PolyRouterMarket> getMarketById(String id) {
        return webClient.get()
                .uri("/markets/{id}", id)
                .retrieve()
                .bodyToMono(PolyRouterMarket.class);
    }

    public Flux<PolyRouterMarket> getMarketsByCategory(String category) {
        return webClient.get()
                .uri("/markets?category={category}&limit=50" + PLATFORM_PARAM, category)
                .retrieve()
                .bodyToMono(MarketListResponse.class)
                .flatMapMany(response -> Flux.fromIterable(
                        response.getMarkets() != null ? response.getMarkets() : Collections.emptyList()));
    }

    public Flux<PolyRouterMarket> getMarketsByQuery(String query, String status) {
        if (status != null && !status.isEmpty()) {
            return webClient.get()
                    .uri("/markets?query={query}&status={status}&limit=50" + PLATFORM_PARAM, query, status)
                    .retrieve()
                    .bodyToMono(MarketListResponse.class)
                    .flatMapMany(response -> Flux.fromIterable(
                            response.getMarkets() != null ? response.getMarkets() : Collections.emptyList()));
        }
        return webClient.get()
                .uri("/markets?query={query}&limit=50" + PLATFORM_PARAM, query)
                .retrieve()
                .bodyToMono(MarketListResponse.class)
                .flatMapMany(response -> Flux.fromIterable(
                        response.getMarkets() != null ? response.getMarkets() : Collections.emptyList()));
    }

    public Flux<PolyRouterMarket> getActiveMarkets() {
        return webClient.get()
                .uri("/markets?active=true&closed=false&limit=50" + PLATFORM_PARAM)
                .retrieve()
                .bodyToMono(MarketListResponse.class)
                .flatMapMany(response -> Flux.fromIterable(
                        response.getMarkets() != null ? response.getMarkets() : Collections.emptyList()));
    }
}