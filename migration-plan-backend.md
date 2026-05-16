# Polymarket Trading Simulator — Backend Migration Plan

## Overview

Add a full simulation engine to the existing Spring Boot backend (Java 21, PostgreSQL, Maven). The frontend is out of scope and will be developed separately. The backend already has market proxy endpoints (`/api/markets`, `/api/polyrouter/markets`), JPA `User` entity, and PolyRouter integration via WebClient.

**Architecture decisions:**
- **Single session:** SimulatorSession table is a singleton. `start()` deletes and recreates. No session tokens/routing.
- **Standard outcome mapping:** Binary market `current_prices` key `"0"` = NO, `"1"` = YES.
- **Snapshot throttle global:** All snapshot paths (start, open, close, 30s poll) check `lastSnapshotTime < 10s` and skip.

---

## Phase 1: Domain Model & Persistence Layer

### New package structure

```
src/main/java/com/polymarket/polymarket_backend/
├── enums/
│   └── PositionSide.java                    ★ NEW
├── model/
│   └── entity/                              ★ NEW
│       ├── SimulatorSession.java
│       ├── Position.java
│       └── PerformanceSnapshot.java
├── dto/                                     ★ NEW
│   ├── SimulatorStateDTO.java
│   ├── OpenPositionRequest.java
│   ├── PositionDTO.java
│   ├── ClosedPositionDTO.java
│   ├── PortfolioValueDTO.java
│   └── NormalizedMarketDTO.java
├── repository/
│   ├── SimulatorSessionRepository.java      ★ NEW
│   ├── PositionRepository.java              ★ NEW
│   └── PerformanceSnapshotRepository.java   ★ NEW
```

### JPA Entities

**SimulatorSession** — table `simulator_sessions`

| Field | Type | Notes |
|-------|------|-------|
| `id` | `UUID` | PK, auto-generated |
| `enabled` | `boolean` | |
| `balance` | `double` | Available virtual cash, starts at 100,000 |
| `usedBalance` | `double` | Sum of open position amounts |
| `createdAt` | `Instant` | |
| `lastSnapshotTime` | `Long` | Nullable, Unix ms — throttle guard |

**Position** — table `simulator_positions`

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | PK, format: `pos_<timestamp>_<random4>` |
| `marketId` | `String` | |
| `marketQuestion` | `String` | |
| `side` | `String` | `YES` or `NO` |
| `amount` | `double` | Investment in USDC |
| `entryPrice` | `double` | 0.0 – 1.0 |
| `shares` | `int` | `floor(amount / entryPrice)` |
| `outcome` | `String` | e.g. "Yes" |
| `timestamp` | `long` | Unix ms |
| `closed` | `boolean` | Soft-delete flag |
| `closePrice` | `Double` | Nullable |
| `closeValue` | `Double` | Nullable |
| `pnl` | `Double` | Nullable |
| `closeTime` | `Long` | Nullable, Unix ms |

**PerformanceSnapshot** — table `simulator_performance`

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | PK, auto-generated |
| `value` | `double` | Total portfolio value |
| `timestamp` | `long` | Unix ms |

**Indexes:** `simulator_positions(session_id, closed)`, `simulator_performance(session_id, timestamp)`

### Enums

**PositionSide.java:**
```java
public enum PositionSide {
    YES, NO
}
```
Jackson-annotated for case-insensitive deserialization.

### Repositories

**SimulatorSessionRepository** extends `JpaRepository<SimulatorSession, UUID>`:
- `Optional<SimulatorSession> findFirstByEnabledTrue()`

**PositionRepository** extends `JpaRepository<Position, String>`:
- `List<Position> findByClosedFalse()`
- `List<Position> findByClosedTrueOrderByCloseTimeDesc()`
- `Optional<Position> findByIdAndClosedFalse(String id)`
- `void deleteAll()`

**PerformanceSnapshotRepository** extends `JpaRepository<PerformanceSnapshot, Long>`:
- `List<PerformanceSnapshot> findAllByOrderByTimestampAsc()`
- `void deleteAll()`

### DTOs

| DTO | Fields |
|-----|--------|
| `OpenPositionRequest` | `marketId`, `marketQuestion`, `side`, `amount`, `entryPrice`, `outcome` |
| `SimulatorStateDTO` | `enabled`, `balance`, `usedBalance`, `List<PositionDTO> positions`, `List<ClosedPositionDTO> history`, `List<PerformanceSnapshot> performanceHistory` |
| `PositionDTO` | All Position fields + `currentPrice` (live) |
| `ClosedPositionDTO` | Position + `closePrice`, `closeValue`, `pnl`, `closeTime` |
| `PortfolioValueDTO` | `portfolioValue`, `totalPnl`, `openPositionsValue`, `balance` |
| `NormalizedMarketDTO` | Per spec §10: `id`, `conditionId`, `question`, `image`, `volume24hr`, `volume`, `liquidity`, `endDate`, `events`, `status`, `_outcomes`, `_prices`, `_metadata`, `_spread` |

---

## Phase 2: Service Layer — Simulator Engine

```java
@Service
@Transactional
public class SimulatorService {

    public SimulatorStateDTO startSession();
    public void stopSession();
    public SimulatorStateDTO getState();

    public PositionDTO openPosition(OpenPositionRequest req);
    public ClosedPositionDTO closePosition(String positionId);

    public PortfolioValueDTO getPortfolioValue();
    public List<PerformanceSnapshot> getPerformanceHistory(String range);
    public void recordSnapshot();
}
```

### Business rules

**startSession():**
- Delete all existing positions, snapshots, and session
- Create new session: `enabled=true`, `balance=100_000`, `usedBalance=0`
- Record initial snapshot immediately (no throttle check on start)

**stopSession():**
- Find active session or return silently
- Delete all positions, snapshots, and session
- Reset to defaults

**openPosition(OpenPositionRequest):**
- Find active session or throw `IllegalStateException`
- Validate `amount <= balance`, else throw `IllegalArgumentException`
- Generate `id = "pos_" + System.currentTimeMillis() + "_" + randomAlphanumeric(4)`
- `shares = (int) Math.floor(amount / entryPrice)`
- `balance -= amount; usedBalance += amount`
- Save position, update session, recordSnapshot()

**closePosition(String positionId):**
- Find open position by id → 404 if not found, 409 if closed
- Resolve current price: cache → entryPrice → 0.5
- `currentValue = shares * currentPrice`
- `pnl = currentValue - amount`
- `balance += currentValue; usedBalance -= amount`
- Mark closed, recordSnapshot()

**getPortfolioValue():**
- `portfolioValue = balance + Σ(openPosition.shares × currentPrice)`
- `totalPnl = portfolioValue - 100000`
- `openPositionsValue = portfolioValue - balance`

**recordSnapshot():**
- If `lastSnapshotTime` is set AND (now - lastSnapshotTime) < 10000ms → skip
- Otherwise compute portfolio value, save snapshot, update `lastSnapshotTime`

**getPerformanceHistory(range):**
- Fetch all snapshots ordered by timestamp
- If range provided, filter by cutoff epoch:
  - `1M` = −30d, `3M` = −90d, `6M` = −180d, `1Y` = −365d, `2Y` = −730d

### Error handling

| Scenario | Exception | HTTP Status |
|----------|-----------|-------------|
| Insufficient balance | `IllegalArgumentException` | 400 |
| Position not found | `NoSuchElementException` | 404 |
| Already closed | `IllegalStateException` | 409 |
| No active session | `IllegalStateException` | 400 |
| Invalid body | `MethodArgumentNotValidException` | 400 |

---

## Phase 3: Price Cache & Polling

### PriceCacheService

```java
@Service
public class PriceCacheService {
    private final ConcurrentHashMap<String, Double> priceCache = new ConcurrentHashMap<>();

    @Nullable
    public Double getPrice(String marketId, String side);
    // Cache key: marketId + ":" + side
    // Side → outcome index: YES→"1", NO→"0"

    @Scheduled(fixedRate = 30000)
    public void refreshTrackedPrices();

    public void refreshPrice(String marketId);
}
```

**refreshTrackedPrices()**: Queries `PositionRepository.findByClosedFalse()`, collects unique `marketId`s, fetches each from PolyRouter via `PolyRouterMarketService.getMarketById(id)`, extracts outcome price from `currentPrices` map, stores in cache.

### Scheduling

- `@EnableScheduling` + `@EnableTransactionManagement` on `PolymarketBackendApplication`
- Price refresh: `@Scheduled(fixedRate = 30000)`
- Auto snapshot: another `@Scheduled(fixedRate = 30000)` method calls `SimulatorService.recordSnapshot()` (throttle ensures no duplicate within 10s)

---

## Phase 4: REST Controllers

### SimulatorController

`@RestController @RequestMapping("/api/simulator")`

| Method | Path | Body/Params | Response |
|--------|------|-------------|----------|
| `POST` | `/start` | — | `SimulatorStateDTO` |
| `POST` | `/stop` | — | `SimulatorStateDTO` |
| `GET` | `/state` | — | `SimulatorStateDTO` |
| `POST` | `/positions` | `@Valid OpenPositionRequest` | `PositionDTO` (201) |
| `GET` | `/positions` | — | `List<PositionDTO>` |
| `DELETE` | `/positions/{id}` | — | `ClosedPositionDTO` |
| `GET` | `/performance` | `?range=` | `List<PerformanceSnapshot>` |
| `GET` | `/portfolio-value` | — | `PortfolioValueDTO` |

### MarketProxyController

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/api/markets` | Normalize to `NormalizedMarketDTO`, support `?category=` |
| `GET` | `/api/markets/{id}` | Single market from PolyRouter |
| `GET` | `/api/markets/{id}/price` | Current price for a market |
| `GET` | `/api/markets/{id}/trades` | Trade history proxy |

### GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class) → 400
    @ExceptionHandler(NoSuchElementException.class) → 404
    @ExceptionHandler(IllegalStateException.class) → 409
}
```
Response body: `{ error: string, message: string, timestamp: long }`

### CORS Config

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
```

---

## Phase 5: Configuration & Cleanup

### API key → environment variable

In `PolyRouterMarketService.java`, inject `@Value("${polyrouter.api-key}")` and read from `POLYROUTER_API_KEY` env var via `application.properties`:
```properties
polyrouter.api-key=${POLYROUTER_API_KEY}
```

### SimulatorProperties (`@ConfigurationProperties`)

```properties
simulator.default-balance=100000
simulator.price-poll-interval-ms=30000
simulator.snapshot-throttle-ms=10000
```

### `.env.example`

```
DB_URL=jdbc:postgresql://localhost:5432/polymarket
DB_USERNAME=postgres
DB_PASSWORD=postgres
POLYROUTER_API_KEY=<your-key-here>
```

---

## Phase 6: Testing

### SimulatorServiceTest (`@ExtendWith(MockitoExtension.class)`)

| Test | What it verifies |
|------|-----------------|
| `start_createsSessionWith100kBalance` | enabled=true, balance=100000 |
| `start_resetsExistingSession` | Old data deleted |
| `openPosition_deductsBalance` | balance decreases by amount |
| `openPosition_calculatesShares` | shares = floor(amount/entryPrice) |
| `openPosition_throwsOnInsufficientBalance` | 400 when amount > balance |
| `closePosition_calculatesPnL` | pnl = shares*closePrice - amount |
| `closePosition_restoresBalance` | balance += shares*closePrice |
| `closePosition_throwsIfNotFound` | 404 |
| `closePosition_throwsIfAlreadyClosed` | 409 |
| `portfolioValue_computesCorrectly` | balance + Σ(shares × price) |
| `snapshot_respectsThrottle` | No duplicate within 10s |
| `getPerformanceHistory_filtersByRange` | 1M = last 30 days only |

### SimulatorControllerTest (`@WebMvcTest`)

Test each endpoint's HTTP status code and response body structure.

### PriceCacheServiceTest

Mock `PolyRouterMarketService`, verify cache population and retrieval.

### Existing tests

`./mvnw test` — ensure `PolymarketBackendApplicationTests` and `UserRepositoryTest` still pass.

---

## Summary

| Phase | Deliverables | Est. |
|-------|-------------|------|
| 1 | Entities, repos, enums, DTOs | 3h |
| 2 | SimulatorService business logic | 5h |
| 3 | PriceCacheService + scheduled polling | 2h |
| 4 | Controllers + ExceptionHandler + CORS | 3h |
| 5 | Config cleanup | 1h |
| 6 | Tests | 4h |
| **Total** | | **~18h** |
