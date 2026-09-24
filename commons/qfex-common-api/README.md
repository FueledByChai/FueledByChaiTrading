# QFEX Common API

The QFEX integration: REST client, market data and trade WebSocket streams, HMAC signing, and the ticker registry. The broker lives in `implementations/broker-api/qfex-broker-api-impl` and the quote engine in `implementations/market-data-api/qfex-market-data-impl`.

## Configuration

Each key is read from the system property first, then from an environment variable with the key upper-cased and dots replaced by underscores.

| Key | Default | Notes |
|---|---|---|
| `qfex.environment` | `prod` | `prod` uses qfex.com; anything else uses qfex.io (UAT) |
| `qfex.api.public.key` / `qfex.api.secret.key` | — | Required for trading (`qfex_pub_...`, `qfex_secret_...`) |
| `qfex.account.id` | — | Optional subaccount UUID |
| `qfex.cancel.on.disconnect` | `false` | If true, the venue cancels **all** of the account's orders (including stops and orders from other connections) when the trade socket drops |
| `qfex.order.timeout.ms` | `5000` | How long to wait for a reply to an order request |
| `qfex.rest.url`, `qfex.mds.url`, `qfex.trade.url` | per environment | Overrides |

## Protocol notes

- **Auth.** HMAC-SHA256 of `nonce + ":" + unixSeconds`, sent as `x-qfex-*` headers on REST or as an `auth` message on the trade socket. The body is not signed.
- **Order entry is WebSocket only.** Replies carry no request id, so `QfexTradeStream.request` matches them by `client_order_id`. For replies that omit it, as stop orders can, it matches by the order's symbol, side, type, price and quantity. Errors are matched through the `incoming_message` the venue echoes back.
- **Order types.**
  - Post-only is the `ALO` order type, not a flag.
  - Stops are `STOP_LOSS`: the price is a trigger checked against the mark price, and the stop fires as a market order. Cancel it with `cancel_stop_order` using the order id returned when it was placed.
  - `reduce_only` is a separate boolean, so it combines with post-only.
- **Modify** returns a *new* order id, requires `reduce_only`, and fails on partially filled orders (`CANNOT_MODIFY_PARTIAL_FILL`).
- **Refdata has no minimum-notional field.** Orders below the venue's minimum are rejected with `REJECTED_LESS_THAN_MIN_NOTIONAL`.
- **Candles** come back newest first; `IQfexRestApi.getCandles` re-orders them oldest first. Candles during market-closed hours are flat.
- **Trade `side`** arrives upper-case on the live feed, even though some docs show it lower-case.

Both streams reconnect with exponential backoff and recycle a socket that has gone silent. Market data subscriptions are replayed after a reconnect, and the trade socket re-authenticates and resubscribes.
