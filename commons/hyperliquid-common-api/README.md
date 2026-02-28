# Hyperliquid Common API

This module provides the Hyperliquid commons integration. It is the closest existing module to the newer shared pattern after Lighter, with both REST and websocket APIs registered through the shared factories.

## Main Entry Points

- REST interface: `commons/hyperliquid-common-api/src/main/java/com/fueledbychai/hyperliquid/ws/IHyperliquidRestApi.java`
- websocket interface: `commons/hyperliquid-common-api/src/main/java/com/fueledbychai/hyperliquid/ws/IHyperliquidWebsocketApi.java`
- REST implementation: `commons/hyperliquid-common-api/src/main/java/com/fueledbychai/hyperliquid/ws/HyperliquidRestApi.java`
- websocket implementation: `commons/hyperliquid-common-api/src/main/java/com/fueledbychai/hyperliquid/ws/HyperliquidWebsocketApi.java`

## Recommended Usage

Prefer the shared factories:

- `ExchangeRestApiFactory.getApi(Exchange.HYPERLIQUID, IHyperliquidRestApi.class)`
- `ExchangeWebSocketApiFactory.getApi(Exchange.HYPERLIQUID, IHyperliquidWebsocketApi.class)`

The older `HyperliquidApiFactory` remains in the module, but the shared factories are the preferred entry points.

## WebSocket Status

This module already includes a shared websocket API provider:

- `commons/hyperliquid-common-api/src/main/java/com/fueledbychai/hyperliquid/ws/HyperliquidWebSocketApiProvider.java`

The current websocket contract is narrower than the Lighter module. It is focused on explicit `connect()` and `submitOrders(...)` flows plus lower-level processors for account, order, and user-fill updates.

## Extension Notes

When extending this module:

1. Keep the shared factory/provider pattern intact.
2. Prefer adding lifecycle and subscription semantics through the public websocket interface instead of bypassing it with ad hoc helpers.
3. Keep signing and order-posting behavior centralized in the common API layer.

## Shared Conventions

- `docs/commons-api-conventions.md`
- `scripts/README-new-exchange.md`
