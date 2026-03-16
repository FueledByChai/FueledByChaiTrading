# Drift Common API

This module is the scaffolded commons integration for Drift. It follows the shared exchange conventions used by the library for REST APIs, websocket APIs, provider registration, and connection lifecycle management.

## Main Entry Points

- REST interface: `commons/drift-common-api/src/main/java/com/fueledbychai/drift/common/api/IDriftRestApi.java`
- websocket interface: `commons/drift-common-api/src/main/java/com/fueledbychai/drift/common/api/IDriftWebSocketApi.java`
- REST implementation: `commons/drift-common-api/src/main/java/com/fueledbychai/drift/common/api/DriftRestApi.java`
- websocket implementation: `commons/drift-common-api/src/main/java/com/fueledbychai/drift/common/api/DriftWebSocketApi.java`

## What This Scaffold Includes

- provider registrations for `ServiceLoader`
- placeholder REST and websocket implementations
- example entry points under `commons/drift-common-api/src/main/java/com/fueledbychai/drift/common/api/example`
- test skeletons under `commons/drift-common-api/src/test/java/com/fueledbychai/drift/common/api`

## Required Follow-Up

Before considering this module complete:

1. Replace placeholder REST methods with normalized exchange-specific implementations.
2. Replace the websocket bootstrap methods with real stream subscriptions and reconnect behavior.
3. Update the ticker registry and exchange capabilities with real exchange behavior.
4. Expand the generated tests to cover real parsing, reconnects, and shutdown paths.

## Shared Conventions

- `docs/commons-api-conventions.md`
- `scripts/README-new-exchange.md`
