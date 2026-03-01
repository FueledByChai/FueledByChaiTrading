# Binance Futures Common API

This module provides the Binance USD(S)-margined futures commons integration. It exposes a shared REST API, a shared websocket API, provider registrations, and ticker-registry wiring for `Exchange.BINANCE_FUTURES`.

## Main Entry Points

- REST interface: `commons/binance-futures-common-api/src/main/java/com/fueledbychai/binancefutures/common/api/IBinanceFuturesRestApi.java`
- websocket interface: `commons/binance-futures-common-api/src/main/java/com/fueledbychai/binancefutures/common/api/IBinanceFuturesWebSocketApi.java`
- REST implementation: `commons/binance-futures-common-api/src/main/java/com/fueledbychai/binancefutures/common/api/BinanceFuturesRestApi.java`
- websocket implementation: `commons/binance-futures-common-api/src/main/java/com/fueledbychai/binancefutures/common/api/BinanceFuturesWebSocketApi.java`

## Main Capabilities

- REST support for:
  - `exchangeInfo` instrument discovery
  - `time` server-time lookup
- Shared websocket subscriptions for:
  - `bookTicker`
  - partial depth (`depth5|10|20@100ms`)
  - aggregate trades (`aggTrade`)
  - mark price / funding (`markPrice@1s`)
- Provider registrations for `ServiceLoader`
- Unit tests covering JSON parsing, provider discovery, configuration, and quote translation

## Recommended Usage

- REST factory:
  - `ExchangeRestApiFactory.getApi(Exchange.BINANCE_FUTURES, IBinanceFuturesRestApi.class)`
- Websocket factory:
  - `ExchangeWebSocketApiFactory.getApi(Exchange.BINANCE_FUTURES, IBinanceFuturesWebSocketApi.class)`

## Notes

- The current implementation targets Binance USD(S)-margined perpetuals (`InstrumentType.PERPETUAL_FUTURES`).
- Symbol normalization accepts `BTC/USDT`, `BTCUSDT`, and bare `BTC` and resolves them to the Binance `BTCUSDT` perpetual contract by default.
- This module is intentionally separate from the spot-specific `binance-spot-*` artifacts.

## Shared Conventions

- `docs/commons-api-conventions.md`
- `scripts/README-new-exchange.md`
