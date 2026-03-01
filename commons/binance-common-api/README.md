# Binance Spot Common API

This module provides the Binance Spot commons integration. Its Maven artifact id is `binance-spot-common-api`, which keeps it distinct from the newer Binance futures module while preserving the existing package structure in this repository.

## Main Entry Points

- REST interface: `commons/binance-common-api/src/main/java/com/fueledbychai/binance/IBinanceRestApi.java`
- REST implementation: `commons/binance-common-api/src/main/java/com/fueledbychai/binance/BinanceRestApi.java`
- REST provider: `commons/binance-common-api/src/main/java/com/fueledbychai/binance/BinanceRestApiProvider.java`
- Ticker registry: `commons/binance-common-api/src/main/java/com/fueledbychai/binance/BinanceTickerRegistry.java`

## Artifact Naming

- Maven artifact: `com.fueledbychai:binance-spot-common-api`
- Exchange constant: `Exchange.BINANCE_SPOT`

## Recommended Usage

Prefer the shared REST factory:

- `ExchangeRestApiFactory.getApi(Exchange.BINANCE_SPOT, IBinanceRestApi.class)`

The older `BinanceApiFactory` remains in the module, but the shared factory is the preferred entry point.

## WebSocket Status

This module includes websocket helpers and processors:

- `commons/binance-common-api/src/main/java/com/fueledbychai/binance/ws/BinanceWebSocketClient.java`
- `commons/binance-common-api/src/main/java/com/fueledbychai/binance/ws/BinanceWebSocketClientBuilder.java`

It does not currently expose a shared `ExchangeWebSocketApiProvider`-based websocket API contract like the newer Lighter and Hyperliquid patterns. Websocket support here is still helper/client-level.

## Extension Notes

When modernizing or extending this module:

1. Keep REST usage aligned with `docs/commons-api-conventions.md`.
2. If a shared websocket API is added later, prefer a public interface plus `ExchangeWebSocketApiProvider`.
3. Preserve existing symbol normalization through the ticker registry.
4. Use the dedicated `binance-futures-*` modules for USD(S)-margined futures instead of extending spot-specific contracts.

## Shared Conventions

- `docs/commons-api-conventions.md`
- `scripts/README-new-exchange.md`
