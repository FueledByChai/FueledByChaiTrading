# Commons API Conventions

This document is the baseline contract for adding new exchange or data-provider support under `commons/` and for consuming those APIs from trading algorithms.

The goal is consistency:

- every exchange module should expose REST and websocket APIs the same way
- factory discovery should work the same way across exchanges
- connection lifecycle behavior should be predictable
- examples and docs should make the intended usage obvious to both humans and code-generation tools

## What Each Exchange Module Should Include

At a minimum, a new exchange module should ship with:

- public interfaces for the exchange REST and websocket APIs
- concrete implementations for those interfaces
- `ExchangeRestApiProvider` and `ExchangeWebSocketApiProvider` implementations
- `META-INF/services` registrations so `ServiceLoader` can discover the providers
- a module `README.md`
- example classes that show the supported REST and websocket workflows
- tests that cover success paths, input validation, and connection lifecycle behavior

Javadocs alone are not enough. The intended documentation stack is:

- Javadocs for public types and methods
- a module `README.md` for usage and extension notes
- this shared conventions document for cross-exchange rules
- executable examples for common workflows

## Standard Naming And Layout

Use these naming conventions for new modules:

- module: `commons/<exchange>-common-api`
- REST interface: `I<Exchange>RestApi`
- REST implementation: `<Exchange>RestApi`
- websocket interface: `I<Exchange>WebSocketApi`
- websocket implementation: `<Exchange>WebSocketApi`
- REST provider: `<Exchange>RestApiProvider`
- websocket provider: `<Exchange>WebSocketApiProvider`
- configuration holder: `<Exchange>Configuration`

The Lighter module is the current reference implementation:

- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/ILighterRestApi.java`
- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/ILighterWebSocketApi.java`
- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/LighterRestApi.java`
- `commons/lighter-common-api/src/main/java/com/fueledbychai/lighter/common/api/LighterWebSocketApi.java`

## Factory And Provider Conventions

The preferred entry points for consumers are:

- `com.fueledbychai.util.ExchangeRestApiFactory`
- `com.fueledbychai.util.ExchangeWebSocketApiFactory`

New exchange modules should integrate with those factories by implementing the provider interfaces and registering them with `ServiceLoader`.

Required resource files:

- `src/main/resources/META-INF/services/com.fueledbychai.util.ExchangeRestApiProvider`
- `src/main/resources/META-INF/services/com.fueledbychai.util.ExchangeWebSocketApiProvider`

Each file should contain the fully qualified provider class name for that module.

Provider rules:

- `getExchange()` must return the exact `Exchange` enum value for the module
- `getApiType()` must return the public interface, not the concrete class
- providers must not return `null`
- REST providers should clearly separate public, default, and private API behavior
- websocket providers should return a long-lived, reusable API instance

If you use `scripts/new-exchange.sh`, it creates the initial provider/factory wiring plus starter docs, examples, and test skeletons, but the exchange-specific REST parsing, websocket parsing, and normalization logic still need to be implemented.

## REST API Conventions

REST clients should follow these rules:

- keep a small, stable public interface that reflects the exchange contract
- keep the implementation behind that interface
- validate all public method inputs early and fail with clear exceptions
- use synchronous request/response semantics
- return normalized domain objects where possible instead of raw JSON
- keep public/private credential behavior explicit
- document whether a method uses cached auth, derives auth, or requires a caller-supplied token

Recommended REST shape:

- `getPublicApi()`: unauthenticated endpoints only
- `getApi()`: default API for the module; may delegate to public or private depending on configuration
- `getPrivateApi()`: authenticated API; must fail clearly if credentials are unavailable

When an exchange requires generated auth tokens, signatures, or account discovery:

- centralize that logic in the REST implementation
- keep token TTL and refresh behavior explicit and documented
- avoid spreading auth generation logic across unrelated callers

## WebSocket API Conventions

Websocket clients should behave like long-lived session managers, not one-off helper methods.

Required websocket behaviors:

- expose one subscribe method per logical stream
- return the underlying client or handle so callers can inspect or close it if needed
- reuse a single connection per logical channel when the same subscription is requested repeatedly
- support multiple listeners per logical channel
- validate all public inputs before connecting
- expose `disconnectAll()` to close every managed connection deterministically

Connection maintenance conventions:

- reconnect automatically after remote close or websocket error unless the caller intentionally closed the stream
- use a shared reconnect backoff calculation for all websocket reconnects in the module
- enforce connection-rate throttling across initial connects and reconnects
- refresh private subscription auth during reconnect when the exchange supports or requires short-lived auth
- isolate dedicated order-entry sockets from market-data sockets when the exchange has a special transaction channel

The Lighter implementation is the current reference:

- market-data and private subscription channels use per-channel reconnect state
- the tx websocket uses a separate reconnect state
- both paths share the same connect-throttling window

## Trading Algorithm Consumption Guidelines

Consumers building trading systems on top of this library should generally:

- prefer `ExchangeRestApiFactory` and `ExchangeWebSocketApiFactory` over exchange-specific constructors
- subscribe once and keep websocket sessions open instead of repeatedly reconnecting
- pair a REST snapshot with websocket deltas when maintaining local state
- call `disconnectAll()` during shutdown paths
- use dedicated transaction websockets only when low-latency order entry is needed

For strategy code, keep exchange-specific details at the boundary:

- resolve instruments, account identifiers, and auth in the exchange layer
- normalize callback payloads before they reach strategy logic
- avoid mixing raw exchange JSON with strategy code

## Testing Requirements For New Exchanges

Every new exchange module should include tests for:

- provider registration and factory resolution
- invalid input validation for public REST and websocket methods
- websocket reconnect after remote close
- websocket reconnect after websocket error
- auth refresh on reconnect for private streams, when applicable
- explicit shutdown behavior via `disconnectAll()`

If the module has order-entry websocket support, also test:

- explicit pre-connect behavior
- request/response correlation
- reconnect behavior for the order-entry socket

## Documentation Checklist

Before a new exchange module is considered complete, verify:

- public interfaces have Javadocs
- concrete REST and websocket classes have class-level Javadocs describing lifecycle behavior
- provider classes are discoverable through `ServiceLoader`
- the module has a `README.md`
- at least one REST example and one websocket example exist when those capabilities are supported
- docs mention any exchange-specific auth, throttling, and connection-maintenance rules
- `scripts/validate-common-module.sh commons/<exchange>-common-api` passes

## Recommended Starting Point

For a new exchange:

1. Run `scripts/new-exchange.sh` to scaffold the module.
2. Implement the public REST interface and provider first.
3. Implement websocket subscriptions with reconnect and throttling behavior that matches this document.
4. Add examples and tests before wiring strategy-layer integrations.
5. Update the module `README.md` with exchange-specific quirks.
