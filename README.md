# Polymarket Trading Simulator — Backend

Backend for the [Polymarket](https://polymarket.com/) trading simulator,
the largest decentralized prediction market. This project acts as a
**Polymarket API proxy** and a **paper trading simulator**
with persistence, price caching, and historical querying.

---

## Tech Stack

| Layer         | Stack                                                                |
| ------------- | -------------------------------------------------------------------- |
| **Runtime**   | Java 21                                                              |
| **Framework** | Spring Boot 4.0.6 (WebMVC + WebFlux)                                 |
| **API**       | REST (`@RestController`) + GraphQL (`spring-graphql`)                |
| **HTTP**      | Reactive `WebClient` for consuming external APIs                     |
| **Database**  | PostgreSQL with [Neon](https://neon.tech) (production), H2 (tests)   |
| **ORM**       | Spring Data JPA + Hibernate + `ddl-auto=update`                      |
| **Build**     | Maven + Wrapper                                                      |
| **Config**    | Environment variables via `.env` + `spring-dotenv`                   |

---

## Architecture

```
Client (frontend)
      │
      ▼
 ┌────────────────────────────────────────────────────────┐
 │                  Spring Boot Backend                   │
 │                                                        │
 │  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐ │
 │  │ REST API     │  │ GraphQL API  │  │ CORS (Vite)   │ │
 │  │ /api/**      │  │ /graphql     │  │ localhost:5173│ │
 │  └──────┬───────┘  └──────┬───────┘  └───────────────┘ │
 │         │                 │                            │
 │  ┌──────▼─────────────────▼─────────────────────────┐  │
 │  │                   Services                       │  │
 │  │  ┌──────────────┐  ┌─────────────┐  ┌──────────┐ │  │
 │  │  │ MarketProxy  │  │ Simulator   │  │ Price    │ │  │
 │  │  │ Service      │  │ Service     │  │ Cache    │ │  │
 │  │  └──────┬───────┘  └──────┬──────┘  │ Service  │ │  │
 │  │         │                 │         └────┬─────┘ │  │
 │  │  ┌──────▼───────┐  ┌──────▼──────┐       │       │  │
 │  │  │ PolyRouter   │  │ JPA Repos   │◄──────┘       │  │
 │  │  │ MarketService│  │ (Postgres)  │               │  │
 │  │  └──────┬───────┘  └─────────────┘               │  │
 │  └─────────┼────────────────────────────────────────┘  │
 └────────────┼───────────────────────────────────────────┘
              │
     ┌────────▼─────────┐        ┌──────────────────┐
     │ PolyRouter API   │        │ Gamma API        │
     │ api-v2.polyrouter│        │ gamma-api.poly   │
     │ .io              │        │ .market          │
     └──────────────────┘        └──────────────────┘
```

### External Data Sources

| Endpoint                | Description                                                       |
| ----------------------- | ----------------------------------------------------------------- |
| **Gamma API**           | Official Polymarket API (`gamma-api.polymarket.com`)              |
| **PolyRouter API**      | Third-party aggregator (`api-v2.polyrouter.io`) — enriched data, prices, volumes, normalized liquidity |

---

## PolyRouter

**PolyRouter** is a third-party provider that aggregates and normalizes Polymarket data. The project integrates it as the primary data source:

- **`PolyRouterMarketService`**: WebClient client consuming `https://api-v2.polyrouter.io/markets`. Supports filtering by category, active markets, and ID lookup. Uses an API key via `X-API-Key` header.
- **`PolyRouterMarket`**: Normalized model including `current_prices` (bid/ask per outcome), `volume_24h`, `volume_total`, `liquidity_score`, `status`, `category`, etc.
- **`PolyRouterMarketController`** (`/api/polyrouter/markets`): Exposes raw PolyRouter endpoints.
- **`MarketProxyController`** (`/api/markets`): Normalization layer on top of PolyRouter that transforms data into `NormalizedMarketDTO` (unifies outcomes, prices, volume, and liquidity).

The unified `/api/markets` endpoint is the one the frontend should consume.

---

## Paper Trading Simulator

The project includes a simulated trading engine with a single session and PostgreSQL persistence.

### Simulator Endpoints

| Method | Route                            | Description                                   |
| ------ | ---------------------------      | -----------------------------------------     |
| POST   | `/api/simulator/start`           | Starts a new session (clears previous)        |
| POST   | `/api/simulator/stop`            | Stops and cleans up the session               |
| GET    | `/api/simulator/state`           | Current state (balance, positions, hist.)     |
| POST   | `/api/simulator/positions`       | Opens a position                              |
| GET    | `/api/simulator/positions`       | Lists open positions                          |
| DELETE | `/api/simulator/positions/{id}`  | Closes a position (with PnL)                  |
| GET    | `/api/simulator/performance`     | Performance history (`?range=1M/3M/6M/1Y/2Y`) |
| GET    | `/api/simulator/portfolio-value` | Current portfolio value + total PnL           |

### Business Rules

- **Initial balance**: 100,000 virtual USDC per session.
- **Shares calculation**: `shares = floor(amount / entryPrice)`.
- **Live price**: Resolved from `PriceCacheService` (cache refreshed every 30s against PolyRouter); falls back to entry price or 0.5.
- **Snapshots**: Recorded on start, open/close position, and every 30s via a scheduled task, with 10s throttle between snapshots.
- **Single session**: Only one active session at a time. `start()` performs a full reset.

---

## Markets API

### Normalized Proxy (main frontend endpoint)

| Method | Route                          | Description                           |
| ------ | ------------------------------ | ------------------------------------- |
| GET    | `/api/markets`                 | All markets (`?category=`)            |
| GET    | `/api/markets/{id}`            | Single market detail                  |
| GET    | `/api/markets/{id}/price`      | Current prices per outcome            |
| GET    | `/api/markets/{id}/trades`     | Trade history (placeholder)           |

### Gamma API (direct proxy)

| Method | Route                              | Description                     |
| ------ | ---------------------------------- | ------------------------------- |
| GET    | `/api/gamma/markets`               | All markets                     |
| GET    | `/api/gamma/markets/{id}`          | Market by ID                    |
| GET    | `/api/gamma/markets/category`      | Filter by category              |
| GET    | `/api/gamma/markets/active`        | Active markets                  |

### PolyRouter (direct proxy)

| Method | Route                                  | Description                     |
| ------ | -------------------------------------- | ------------------------------- |
| GET    | `/api/polyrouter/markets`              | All markets                     |
| GET    | `/api/polyrouter/markets/{id}`         | Market by ID                    |
| GET    | `/api/polyrouter/markets/category`     | Filter by category              |
| GET    | `/api/polyrouter/markets/active`       | Active markets                  |

### Users API

| Method | Route                                      | Description                 |
| ------ | ------------------------------------------ | --------------------------- |
| POST   | `/api/users`                               | Create user                 |
| GET    | `/api/users`                               | List users                  |
| GET    | `/api/users/{id}`                          | Get user by ID              |
| PUT    | `/api/users/{id}`                          | Update user                 |
| DELETE | `/api/users/{id}`                          | Delete user                 |
| POST   | `/api/users/{userId}/favorites/{marketId}` | Add favorite market         |
| DELETE | `/api/users/{userId}/favorites/{marketId}` | Remove favorite market      |

### GraphQL

The `/graphql` endpoint exposes a user schema (queries and mutations) with GraphiQL enabled at `/graphiql`.

```graphql
type User {
  id: ID!
  email: String!
  username: String!
  favoriteMarketIds: [String!]
}

type Query {
  users: [User!]!
  user(id: ID!): User
}

type Mutation {
  createUser(email: String!, password: String!, username: String!): User!
  updateUser(id: ID!, email: String!, password: String!, username: String!): User!
  deleteUser(id: ID!): Boolean!
  addFavoriteMarket(userId: ID!, marketId: String!): User!
  removeFavoriteMarket(userId: ID!, marketId: String!): User!
}
```

---

## Environment Variables

Create a `.env` file at the project root:

```env
DB_URL=jdbc:postgresql://localhost:5432/polymarket
DB_USERNAME=postgres
DB_PASSWORD=postgres
POLYROUTER_API_KEY=your-polyrouter-api-key
```

| Variable             | Description                               | Required |
| -------------------- | ----------------------------------------- | -------- |
| `DB_URL`             | PostgreSQL JDBC URL                       | Yes      |
| `DB_USERNAME`        | Database username                         | Yes      |
| `DB_PASSWORD`        | Database password                         | Yes      |
| `POLYROUTER_API_KEY` | PolyRouter API key (optional in dev)      | No*      |

> If no `POLYROUTER_API_KEY` is provided, the `X-API-Key` header is sent empty.

---

## Getting Started Locally

### Prerequisites

- **Java 21** (JDK)
- **Maven** (or use `./mvnw`)
- **PostgreSQL** (or a remote Neon database)

### 1. Clone

```bash
git clone <repo-url>
cd Polymarket-backend
```

### 2. Configure environment

```bash
cp .env.example .env
# Edit .env with your credentials
```

### 3. Create the database

```sql
CREATE DATABASE polymarket;
```

### 4. Run

```bash
cd polymarket-backend
./mvnw spring-boot:run
```

The application starts at `http://localhost:8080`.

### 5. Verify

```bash
curl http://localhost:8080/api/simulator/state
```

### Tests

```bash
./mvnw test
```

Tests use H2 in PostgreSQL mode so no external database is needed.

---

## Project Structure

```
polymarket-backend/
├── src/main/java/com/polymarket/polymarket_backend/
│   ├── PolymarketBackendApplication.java
│   ├── config/
│   │   ├── CorsConfig.java
│   │   └── SimulatorProperties.java
│   ├── controller/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── MarketProxyController.java      # /api/markets (normalized)
│   │   ├── PolymarketController.java       # /api/gamma/markets
│   │   ├── PolyRouterMarketController.java # /api/polyrouter/markets
│   │   ├── SimulatorController.java        # /api/simulator
│   │   ├── UserController.java             # /api/users
│   │   └── UserGraphQLController.java      # GraphQL
│   ├── dto/                                # Request/response DTOs
│   ├── enums/                              # PositionSide, Status, MarketType
│   ├── model/                              # JPA entities + external models
│   ├── repository/                         # Spring Data JPA repositories
│   └── service/                            # Business logic
│       ├── PolymarketService.java
│       ├── PolyRouterMarketService.java
│       ├── PriceCacheService.java
│       ├── SchedulerService.java
│       ├── SimulatorService.java
│       └── UserService.java
├── src/main/resources/
│   ├── application.properties
│   └── graphql/user.graphqls
└── pom.xml
```
