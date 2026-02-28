# Paradex Common API

This module provides the Paradex commons integration. It currently exposes a factory-registered REST API and a set of websocket clients and processors for account, order, and fill streams.

## Main Entry Points

- REST interface: `commons/paradex-common-api/src/main/java/com/fueledbychai/paradex/common/api/IParadexRestApi.java`
- REST implementation: `commons/paradex-common-api/src/main/java/com/fueledbychai/paradex/common/api/ParadexRestApi.java`
- REST provider: `commons/paradex-common-api/src/main/java/com/fueledbychai/paradex/common/api/ParadexRestApiProvider.java`
- Ticker registry: `commons/paradex-common-api/src/main/java/com/fueledbychai/paradex/common/ParadexTickerRegistry.java`

## Recommended Usage

Prefer the shared REST factory:

- `ExchangeRestApiFactory.getApi(Exchange.PARADEX, IParadexRestApi.class)`

The older `ParadexApiFactory` remains in the module, but the shared factory is the preferred entry point.

## WebSocket Status

This module includes websocket builders, clients, and processors:

- `commons/paradex-common-api/src/main/java/com/fueledbychai/paradex/common/api/ws/ParadexWSClientBuilder.java`
- `commons/paradex-common-api/src/main/java/com/fueledbychai/paradex/common/api/ws/ParadexWebSocketClient.java`
- processors under `commons/paradex-common-api/src/main/java/com/fueledbychai/paradex/common/api/ws/`

It does not currently expose a shared `ExchangeWebSocketApiProvider`-based websocket API interface. Websocket support here is still stream-specific and helper-based.

## Additional Docs

- Resilient instrument lookup notes: `commons/paradex-common-api/RESILIENT_LOOKUP_README.md`

## Extension Notes

When modernizing or extending this module:

1. Keep REST usage aligned with `docs/commons-api-conventions.md`.
2. If a shared websocket API is added later, prefer a single public interface plus `ExchangeWebSocketApiProvider`.
3. Keep exchange-specific signing and auth flows centralized in the common API layer.

## Shared Conventions

- `docs/commons-api-conventions.md`
- `scripts/README-new-exchange.md`
