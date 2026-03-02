# Binance Futures Common API

This module provides the Binance derivatives commons integration for `Exchange.BINANCE_FUTURES`. It exposes a shared REST API, a shared websocket API, provider registrations, and ticker-registry wiring for Binance USD(S)-margined perpetuals plus Binance options.

## Main Entry Points

- REST interface: `commons/binance-futures-common-api/src/main/java/com/fueledbychai/binancefutures/common/api/IBinanceFuturesRestApi.java`
- websocket interface: `commons/binance-futures-common-api/src/main/java/com/fueledbychai/binancefutures/common/api/IBinanceFuturesWebSocketApi.java`
- REST implementation: `commons/binance-futures-common-api/src/main/java/com/fueledbychai/binancefutures/common/api/BinanceFuturesRestApi.java`
- websocket implementation: `commons/binance-futures-common-api/src/main/java/com/fueledbychai/binancefutures/common/api/BinanceFuturesWebSocketApi.java`

## Main Capabilities

- REST support for:
  - `/fapi/v1/exchangeInfo` perpetual instrument discovery
  - `/eapi/v1/exchangeInfo` options instrument discovery
  - `time` server-time lookup
- Shared websocket subscriptions for:
  - perpetual `bookTicker`
  - shared ticker streams (`@ticker`) for perpetuals and options
  - partial depth (`depth5|10|20@100ms`)
  - aggregate trades (`aggTrade`) for perpetuals and `trade` for options
  - mark price / funding (`markPrice@1s`) for perpetuals
- Provider registrations for `ServiceLoader`
- Unit tests covering JSON parsing, provider discovery, configuration, and quote translation

## Recommended Usage

- REST factory:
  - `ExchangeRestApiFactory.getApi(Exchange.BINANCE_FUTURES, IBinanceFuturesRestApi.class)`
- Websocket factory:
  - `ExchangeWebSocketApiFactory.getApi(Exchange.BINANCE_FUTURES, IBinanceFuturesWebSocketApi.class)`

## Notes

- Supported instrument types are `InstrumentType.PERPETUAL_FUTURES` and `InstrumentType.OPTION` on `Exchange.BINANCE_FUTURES`.
- Perpetual symbol normalization accepts `BTC/USDT`, `BTCUSDT`, and bare `BTC` and resolves them to the Binance `BTCUSDT` perpetual contract by default.
- Options symbols should use the exchange symbol format (for example `BTC-260627-50000-C`).
- This module is intentionally separate from the spot-specific `binance-spot-*` artifacts.

## Shared Conventions

- `docs/commons-api-conventions.md`
- `scripts/README-new-exchange.md`
