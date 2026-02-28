# Lighter Common API

This module is the reference implementation for a crypto exchange integration that exposes both REST and websocket APIs through the shared `commons` conventions.

## Main Entry Points

- REST interface: `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/ILighterRestApi.java`
- websocket interface: `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/ILighterWebSocketApi.java`
- REST implementation: `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/LighterRestApi.java`
- websocket implementation: `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/LighterWebSocketApi.java`

## Recommended Usage

Prefer the shared factories:

- `ExchangeRestApiFactory.getApi(Exchange.LIGHTER, ILighterRestApi.class)`
- `ExchangeWebSocketApiFactory.getApi(Exchange.LIGHTER, ILighterWebSocketApi.class)`

The providers are registered through `ServiceLoader`, so callers should not need to instantiate provider classes directly.

## WebSocket Behavior

`LighterWebSocketApi` manages connection lifecycle for all supported streams:

- one managed client per logical channel
- automatic reconnect after remote close or websocket error
- exponential reconnect backoff
- shared connection throttling across channel sockets and the tx socket
- auth refresh on reconnect for private subscriptions that use short-lived auth
- explicit shutdown through `disconnectAll()`

The tx websocket is maintained separately from market-data and account-subscription channels. Call `connectTxWebSocket()` only when you want to keep that order-entry connection warm between requests.

## Examples

Example entry points live under:

- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/example/LighterRestApiExample.java`
- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/example/LighterMarketStatsWebSocketExample.java`
- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/example/LighterOrderBookWebSocketExample.java`
- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/example/LighterTradesWebSocketExample.java`
- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/example/LighterAccountAllTradesWebSocketExample.java`
- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/example/LighterOrderManagementWebSocketExample.java`

## Extension Reference

When adding support for a new exchange, use this module as the implementation reference and follow:

- `docs/commons-api-conventions.md`
- `scripts/README-new-exchange.md`
