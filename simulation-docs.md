# Simulation Engine

The simulation engine lets users trade Polymarket positions with virtual USDC. It has three tiers: a REST API, a service layer with business logic, and scheduled background jobs for price tracking and snapshots.

---

## Lifecycle

### Start (`POST /api/simulator/start`)
- Deletes all existing positions, performance snapshots, and the active session
- Creates a new `SimulatorSession` with:
  - `enabled = true`
  - `balance = 100,000`
  - `usedBalance = 0`
- Records an initial `PerformanceSnapshot` (unthrottled)

### Stop (`POST /api/simulator/stop`)
- Deletes all positions, snapshots, and the session
- Returns an empty/disabled state

### State (`GET /api/simulator/state`)
- Returns the current session with all open positions and closed position history

---

## Trading

### Open Position (`POST /api/simulator/positions`)
Requires: `marketId`, `side` (YES/NO), `amount`, `entryPrice`, `outcome`
- Validates `amount <= balance` (else 400)
- Generates ID: `pos_<timestamp>_<random4>`
- Calculates shares: `floor(amount / entryPrice)`
- Deducts `amount` from balance, adds to `usedBalance`
- Records a throttled snapshot

### Close Position (`DELETE /api/simulator/positions/{id}`)
- Resolves current price from cache → entryPrice → 0.5
- `currentValue = shares × currentPrice`
- `pnl = currentValue - amount`
- Adds `currentValue` to balance, subtracts `amount` from `usedBalance`
- Marks position as closed with `closePrice`, `closeValue`, `pnl`, `closeTime`
- Records a throttled snapshot

### Portfolio Value (`GET /api/simulator/portfolio-value`)
- `openPositionsValue = Σ(openPosition.shares × currentPrice)`
- `portfolioValue = balance + openPositionsValue`
- `totalPnl = portfolioValue - 100,000`

---

## Performance Snapshots

Snapshots record total portfolio value at a point in time. They are recorded on:
- Session start (unthrottled)
- Every open/close position (throttled to 1 per 10s)
- Every 30 seconds via scheduled job (`SchedulerService.autoSnapshot()`)

### Throttle
If `lastSnapshotTime` is set and less than 10 seconds ago, the snapshot is skipped. The threshold is configurable via `simulator.snapshot-throttle-ms`.

### History (`GET /api/simulator/performance?range=`)
All snapshots ordered by timestamp ascending. Optional `range` parameter filters to a time window:

| Range | Window |
|-------|--------|
| `1M` | Last 30 days |
| `3M` | Last 90 days |
| `6M` | Last 180 days |
| `1Y` | Last 365 days |
| `2Y` | Last 730 days |

---

## Price Cache

The `PriceCacheService` maintains a `ConcurrentHashMap<String, Double>` mapping `marketId:outcomeIndex` to price.

- `YES` → outcome index `"1"`, `NO` → outcome index `"0"`
- Every 30 seconds, all unique `marketId`s from open positions are fetched from PolyRouter and the cache is refreshed
- Individual markets can be refreshed with `refreshPrice(marketId)`
- Failed fetches are silently skipped to avoid cascading failures

---

## Configuration (`simulator.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `simulator.default-balance` | 100000 | Starting virtual balance |
| `simulator.price-poll-interval-ms` | 30000 | Price cache refresh interval |
| `simulator.snapshot-throttle-ms` | 10000 | Minimum time between snapshots |

---

## Error Handling

| Scenario | HTTP Status | Exception |
|----------|-------------|-----------|
| No active session | 400 | `IllegalStateException` |
| Insufficient balance | 400 | `IllegalArgumentException` |
| Invalid request body | 400 | `MethodArgumentNotValidException` |
| Position not found | 404 | `NoSuchElementException` |
| Position already closed | 409 | `IllegalStateException` |

All error responses return: `{ error, message, timestamp }`

---

## Architecture Overview

```
SimulatorController (REST API)
        │
        ▼
SimulatorService (Business Logic, @Transactional)
        │
        ├──► SimulatorSessionRepository
        ├──► PositionRepository
        ├──► PerformanceSnapshotRepository
        └──► PriceCacheService ──► PolyRouterMarketService

SchedulerService (@Scheduled)
        │
        └──► SimulatorService.recordSnapshot()  (every 30s)

PriceCacheService (@Scheduled)
        │
        └──► refreshTrackedPrices()  (every 30s)
```

The `PriceCacheService` is used by `SimulatorService` to resolve live prices for PnL calculations. If a price is not yet cached, it falls back to `entryPrice`, then to `0.5`.
