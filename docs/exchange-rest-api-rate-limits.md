# Exchange REST API Rate Limits

Reference guide for REST API rate limits across supported crypto exchanges. Focused on public market data endpoints (order book, BBO) used for L1 snapshot polling.

> Last updated: 2026-03-25

---

## Quick Comparison

| Exchange | Effective BBO/Orderbook Rate | Rate Model | Rate Limit Headers | Exceeded Response |
|---|---|---|---|---|
| **Paradex** | ~25 req/sec | Flat count per IP | Yes (`x-ratelimit-*`) | HTTP 429 |
| **Hyperliquid** | ~10 req/sec | Weight-based per IP | No | HTTP 429 |
| **Aster** | ~20 req/sec | Weight-based per IP | Yes (`X-MBX-USED-WEIGHT-*`) | HTTP 429, then 418 (ban) |
| **Drift** | ~30 req/sec (estimated) | Undocumented | No | HTTP 429 |
| **Lighter** | ~1 req/sec (standard) | Flat / weight-based | No | HTTP 429 |

---

## Paradex

**Base URL:** `https://api.paradex.trade/v1`

### Public Endpoints

| Scope | Limit |
|---|---|
| All public endpoints (default) | 1,500 requests/minute per IP (~25 req/sec) |
| `POST /onboarding` | 600 requests/minute per IP |
| `POST /auth` | 600 requests/minute per IP |

The `/bbo/{market}` and `/orderbook/{market}` endpoints fall under the default 1,500 req/min limit.

### Private Endpoints

| Scope | Limit |
|---|---|
| `POST/DELETE/PUT /orders` | 800 req/sec, 17,250 req/min per account |
| `GET /*` (private) | 120 req/sec, 600 req/min per account |

Private endpoints are also subject to the 1,500 req/min IP-level cap across all accounts.

### Algorithm

Token bucket with continuous refill. At 1,500 req/min the refill rate is ~25 tokens/second (one every 40ms). Bursts are allowed up to bucket capacity.

### Response Headers

- `x-ratelimit-limit` -- max requests allowed in window
- `x-ratelimit-remaining` -- requests remaining
- `x-ratelimit-reset` -- window reset time
- `x-ratelimit-window` -- window duration

### Notes

- No tiered/VIP rate limits documented
- Batch order requests count as 1 rate limit unit regardless of batch size (up to 50 orders)
- WebSocket BBO feed recommended over REST polling for real-time prices

### Sources

- https://docs.paradex.trade/api/general-information/rate-limits/api
- https://docs.paradex.trade/api/general-information/rate-limits/common-questions

---

## Hyperliquid

**Base URL:** `https://api.hyperliquid.xyz/info` (POST)

### Weight-Based System

Global limit: **1,200 weight per minute per IP**, shared across all REST requests.

### Endpoint Weights

| Endpoint | Weight |
|---|---|
| `l2Book`, `allMids`, `clearinghouseState`, `orderStatus`, `exchangeStatus` | **2** |
| Exchange API (place/cancel order) | 1 + floor(batch_length / 40) |
| `userRole` | 60 |
| Explorer API | 40 |
| All other info requests (`candleSnapshot`, `openOrders`, `userFills`, `meta`, etc.) | **20** |

### Variable Weight Endpoints

Some endpoints add weight based on response size:
- `recentTrades`, `historicalOrders`, `userFills`, `fundingHistory`, etc.: +1 weight per 20 items returned
- `candleSnapshot`: +1 weight per 60 items returned

### Order Book Specifically

- Request: `{"type": "l2Book", "coin": "BTC"}`
- Weight: **2**
- Returns up to 20 levels per side
- At weight 2: up to ~600 requests/minute (~10 req/sec) if no other calls consume weight

### Address-Based Limits (Orders Only)

- 1 request per 1 USDC traded cumulatively
- Initial buffer: 10,000 requests for new accounts
- When rate-limited: 1 request per 10 seconds
- Check status via `userRateLimit` info endpoint

### Notes

- No rate limit headers returned in responses
- HTTP 429 when exceeded

### Sources

- https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/rate-limits-and-user-limits
- https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint

---

## Lighter (ZK Lighter)

**Base URL:** `https://mainnet.zklighter.elliot.ai/api/v1`

### Account Tiers

| Tier | Limit |
|---|---|
| Standard | 60 requests per rolling minute (flat count) |
| Premium | 24,000 weighted requests per rolling minute |

### Endpoint Weights (Premium Tier)

| Endpoint | Weight |
|---|---|
| `sendTx`, `sendTxBatch`, `nextNonce` | 6 |
| `publicPools`, `txFromL1TxHash` | 50 |
| `accountInactiveOrders`, `deposit/latest` | 100 |
| `apikeys` | 150 |
| `transferFeeInfo` | 500 |
| `trades`, `recentTrades` | 600 |
| `changeAccountTier`, `tokens/*`, `referral/*`, etc. | 3,000 |
| `tokens/create` | 23,000 |
| **All other endpoints (including `orderBookOrders`)** | **300** |

### Order Book Specifically

- Endpoint: `GET /api/v1/orderBookOrders?market_id={id}&limit={n}`
- Max limit parameter: 250
- Weight: 300
- Standard tier: 60 req/min (~1 req/sec)
- Premium tier: 24,000 / 300 = ~80 req/min (~1.3 req/sec)

### Cooldown After Hitting Limits

- Firewall-based cooldown: 60 seconds (static)
- API server-based cooldown: `weight / (totalWeight / 60)` seconds

### Premium sendTx Limits (by Staked LIT)

| Staked LIT | sendTx/sendTxBatch per minute |
|---|---|
| 0 | 4,000 |
| 1,000 | 5,000 |
| 10,000 | 7,000 |
| 100,000 | 12,000 |
| 500,000 | 40,000 |

### Notes

- No rate limit headers documented
- HTTP 429 when exceeded
- Lighter is the most restrictive exchange for polling

### Sources

- https://apidocs.lighter.xyz/docs/rate-limits
- https://apidocs.lighter.xyz/docs/orderbookorders

---

## Drift

**DLOB Base URL:** `https://dlob.drift.trade`
**Data API Base URL:** `https://data.api.drift.trade`

### Rate Limits

Drift does **not publicly document specific rate limit numbers**. The official docs state:

> "Users are restricted to a certain number of requests per minute. The exact limit may vary depending on the endpoint and overall system load."

### Best Available Estimates

- The open-source DLOB server's `.env.example` contains `RATE_LIMIT_CALLS_PER_SECOND=30`
- Rate limiting is enforced at infrastructure level (AWS CloudFront + Envoy proxy), not application level
- The actual production limit may differ from the default config

### Order Book Specifically

- Endpoint: `GET /l2?marketName={name}&marketType={type}`
- Also available: `/batchL2` for fetching multiple markets in one request
- Responses are served from Redis cache (pre-computed snapshots)

### Caching

> "Responses may be cached for a short period, typically ranging from a few seconds to a few minutes, depending on the endpoint and the nature of the data."

### Notes

- No rate limit headers returned
- HTTP 429 when exceeded; exponential backoff recommended
- No tiered rate limits documented
- Prefer `/batchL2` over individual `/l2` calls for multiple markets
- WebSocket endpoint (`wss://dlob.drift.trade/ws`) available for streaming L2/L3 updates

### Sources

- https://drift-labs.github.io/v2-teacher/#rate-limiting
- https://github.com/drift-labs/dlob-server (`.env.example`)
- https://docs.drift.trade/developers

---

## Aster

**Futures Base URL:** `https://fapi.asterdex.com/fapi/v1`
**Spot Base URL:** `https://api.asterdex.com/api/v1`

### Global Limits

| API | Weight Limit | Order Limit |
|---|---|---|
| Futures | 2,400 weight/minute per IP | 1,200 orders/minute |
| Spot | 1,200 weight/minute per IP | 100 orders/minute (+ 300/10-sec burst) |

### Order Book (`/depth`) Weights

| `limit` Parameter | Weight |
|---|---|
| 5, 10, 20, 50 | **2** |
| 100 | **5** |
| 500 | **10** |
| 1000 | **20** |

At limit=50 (weight 2) on futures: up to 1,200 order book requests/minute (~20 req/sec).

### Other Common Endpoint Weights

| Endpoint | Weight |
|---|---|
| `/ping`, `/time`, `/exchangeInfo` | 1 |
| `/trades` | 1 |
| `/klines` | 1-10 (varies by limit) |
| `/premiumIndex` (mark price) | 1 |
| `/ticker/bookTicker` | 1 (single symbol), 2 (all symbols) |
| `/ticker/24hr` | 1 (single symbol), 40 (all symbols) |
| `POST /order` | 1 |
| `POST /batchOrders` | 5 |
| Open orders (all symbols) | 40 |

### Response Headers

- `X-MBX-USED-WEIGHT-(intervalNum)(intervalLetter)` -- e.g., `X-MBX-USED-WEIGHT-1m`
- `X-MBX-ORDER-COUNT-(intervalNum)(intervalLetter)`

### Rate Limit Escalation

| HTTP Code | Meaning |
|---|---|
| 429 | Rate limit violated -- back off immediately |
| 418 | IP auto-banned for ignoring 429s (2 min to 3 days) |
| 403 | WAF limit exceeded |

### Dynamic Limits

Query current limits via `/fapi/v1/exchangeInfo`:
```json
{
  "rateLimits": [
    { "rateLimitType": "REQUEST_WEIGHT", "interval": "MINUTE", "intervalNum": 1, "limit": 2400 },
    { "rateLimitType": "ORDERS", "interval": "MINUTE", "intervalNum": 1, "limit": 1200 }
  ]
}
```

### Notes

- Aster uses Binance-compatible API format
- IP bans escalate from 2 minutes up to 3 days for repeated violations
- Monitor `X-MBX-USED-WEIGHT-1m` header to proactively avoid 429s

### Sources

- https://docs.asterdex.com/product/aster-perpetuals/api/api-documentation
- https://github.com/asterdex/api-docs/blob/master/aster-finance-futures-api.md
- https://github.com/asterdex/api-docs/blob/master/aster-finance-spot-api.md
