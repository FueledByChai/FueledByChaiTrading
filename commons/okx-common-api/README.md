# Okx Common API

Common REST and websocket integration for OKX public market data.

## Main Entry Points

- REST interface: `IOkxRestApi`
- websocket interface: `IOkxWebSocketApi`
- REST implementation: `OkxRestApi`
- websocket implementation: `OkxWebSocketApi`
- ticker registry: `OkxTickerRegistry`

## Supported Instrument Types

- `CRYPTO_SPOT`
- `PERPETUAL_FUTURES`
- `FUTURES`
- `OPTION`

## REST Coverage

`OkxRestApi` currently normalizes instrument metadata from:

- `GET /api/v5/public/instruments`

The API maps OKX `SPOT`, `SWAP`, `FUTURES`, and `OPTION` instruments into shared `InstrumentDescriptor` objects.

## Websocket Coverage

`OkxWebSocketApi` supports:

- `tickers`
- `books5`
- `trades`

Features include batched subscribe requests, reconnect backoff, and automatic resubscribe of requested channels.

## Configuration

System properties:

- `okx.environment` (`prod` or `test`)
- `okx.rest.url`
- `okx.ws.url`
- `okx.account.address`
- `okx.private.key`

Defaults:

- REST: `https://www.okx.com/api/v5`
- WS prod: `wss://ws.okx.com:8443/ws/v5/public`
- WS test: `wss://wspap.okx.com:8443/ws/v5/public`
