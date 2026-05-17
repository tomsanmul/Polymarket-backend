# Polymarket Trading Simulator — User Guide

## What is it?

A paper-trading environment for Polymarket prediction markets. You start with **$100,000 in virtual USDC** and can open and close positions using real market data from PolyRouter. No real money involved — just practice, experiment, and track your performance.

---

## Quick Start

### Start a session

```
POST /api/simulator/start
```

This resets everything: wipes your previous session, gives you a fresh $100,000 balance, and records your starting portfolio value.

### Check your status

```
GET /api/simulator/state
```

Returns your balance, open positions, and trading history.

---

## Trading

### Opening a position

```
POST /api/simulator/positions
{
  "marketId": "0x123...",
  "side": "YES",
  "amount": 500,
  "entryPrice": 0.45,
  "outcome": "Yes"
}
```

| Field | What it means |
|-------|---------------|
| `marketId` | The Polymarket market ID you want to trade |
| `side` | `YES` or `NO` — which outcome you're betting on |
| `amount` | How much virtual USDC to invest |
| `entryPrice` | The price per share when you entered (0.0 – 1.0) |
| `outcome` | Display name for the outcome (e.g. "Yes", "No") |

**How shares are calculated:**
```
shares = floor(amount / entryPrice)
```

Example: investing $500 at $0.45/share gives you 1,111 shares.

If the position value rises, you profit. If it falls, you take a loss.

### Closing a position

```
DELETE /api/simulator/positions/{positionId}
```

When you close, the engine looks up the current market price, calculates your PnL, and adds the proceeds back to your balance:

```
currentValue = shares × currentPrice
pnl = currentValue - amount
```

**Example:** You bought 1,111 shares at $0.45 ($500). If the current price is $0.60:
- `currentValue = 1,111 × 0.60 = $666.60`
- `pnl = 666.60 - 500 = +$166.60`

Your balance increases by $666.60.

### Portfolio value

```
GET /api/simulator/portfolio-value
```

Shows a snapshot of your overall performance:
- **portfolioValue** — cash balance + current value of all open positions
- **totalPnl** — portfolio value compared to your starting $100,000
- **openPositionsValue** — what your open positions are currently worth
- **balance** — available cash (not tied up in positions)

---

## History & Performance

### View past trades

```
GET /api/simulator/state
```

The `history` field contains all your closed positions with their final PnL.

### Performance chart data

```
GET /api/simulator/performance?range=1M
```

Returns a series of data points showing how your portfolio value changed over time. Each point has a `value` and a `timestamp`.

Optional range filters: `1M`, `3M`, `6M`, `1Y`, `2Y`

---

## Markets

### Browse markets

```
GET /api/markets
```

Returns a normalized list of markets from PolyRouter with current prices, volume, and liquidity. Add `?category=sports` to filter by category.

### Check a market's price

```
GET /api/markets/{marketId}/price
```

Shows the current YES/NO prices for a specific market.

---

## What affects your PnL?

Your position PnL depends on the **current market price** of the outcome you bought. The simulator tries to use live prices from PolyRouter, but if that data isn't available it falls back to your entry price (breaking even) or 0.5.

In short: if the market moves in your favor after you buy, you profit. If it moves against you, you lose.

---

## Limits & Rules

- **One active session at a time.** Starting a new session erases the old one.
- **You cannot spend more than your available balance.**
- **Positions close at the current market price**, which updates every 30 seconds.
- **Performance snapshots** are recorded every time you trade, and automatically every 30 seconds — but never more than once per 10 seconds.
- **No leverage, no margin, no fees.** This is simple spot trading with virtual money.
