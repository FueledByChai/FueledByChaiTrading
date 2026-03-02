# FueledByChai Trading

Java 21 + Maven multi-module library for building trading systems.

This repository combines:

- exchange and broker connectivity
- market data adapters
- historical data adapters
- real-time bar adapters
- reporting utilities
- strategy-layer building blocks

It is not a single exchange SDK. It is a layered toolkit for wiring common trading workflows across multiple venues while still allowing exchange-specific behavior where needed.

## What This Project Is

The codebase is organized around a consistent stack:

| Layer | Purpose |
| --- | --- |
| `fueledbychai-api/` | Stable capability interfaces such as broker, market data, historical data, reporting, and strategy APIs |
| `commons/` | Exchange-specific common clients, auth/signing logic, ticker registries, provider wiring, and websocket helpers |
| `implementations/` | Concrete adapters that implement the higher-level APIs using the commons modules |
| `examples/` | Runnable examples and strategy demos |
| `scripts/` | Scaffolding and validation tools for adding new exchange support |

In practice, the repository gives you both:

- reusable interfaces for writing strategy and infrastructure code
- exchange-specific modules for connecting to real venues

## Supported Integrations

The support level varies by venue and by capability. The table below reflects what is present in the repository today.

| Venue / Integration | Commons Module | REST API | Shared WebSocket API | Market Data Impl | Broker Impl | Historical Data Impl | Real-Time Bars | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Lighter | Yes | Yes | Yes | Yes | Yes | No | No | Most complete modern crypto commons reference module |
| Hyperliquid | Yes | Yes | Yes | Yes | Yes | No | No | Shared websocket API exists, but narrower than Lighter |
| Paradex | Yes | Yes | No | Yes | Yes | Yes | No | Websocket support exists as helper/client + processors |
| Binance Spot | Yes | Yes | No | Yes | No | No | No | Artifact ids are `binance-spot-*`; websocket support remains helper/client-level |
| Binance Futures | Yes | Yes | Yes | Yes | No | No | No | New USD(S)-margined perpetuals integration with shared websocket API and funding stream support |
| Interactive Brokers | Yes | Yes | Partial / legacy | Yes | Yes | Yes | Yes | Strong coverage across classic trading workflows |
| dYdX | No commons module in this repo | No shared commons REST layer | No | Yes | Yes | No | No | Implementations exist without a matching commons module here |
| Paper Broker | No exchange commons module | N/A | N/A | No | Yes | No | No | Local execution/testing broker implementation |

## Exchange Module Entry Points

If you are evaluating exchange support or extending the library, start with these module READMEs:

- `commons/lighter-common-api/README.md`
- `commons/hyperliquid-common-api/README.md`
- `commons/paradex-common-api/README.md`
- `commons/binance-spot-common-api/README.md`
- `commons/binance-futures-common-api/README.md`

These document the current public entry points and explain whether a module already follows the newer shared factory/provider websocket pattern or still uses lower-level websocket helpers.

## Examples Included

The repository includes example modules for both infrastructure usage and strategy workflows:

- `examples/CryptoExamples`
- `examples/InteractiveBrokersExamples`
- `examples/IntradayTradingExample`
- `examples/EODTradingStrategy`

Use these as starting points for:

- wiring exchange connectivity
- subscribing to data
- placing orders through broker adapters
- composing strategy logic around the higher-level APIs

## Developer Docs

If you want to add a new exchange, data provider, or broker implementation, start here:

- Shared conventions for exchange modules: `docs/commons-api-conventions.md`
- New exchange scaffolding: `scripts/README-new-exchange.md`
- Common-module validator: `scripts/validate-common-module.sh`

The intended workflow is:

1. Scaffold a new exchange module with `scripts/new-exchange.sh`.
2. Implement the exchange-specific REST and websocket behavior.
3. Validate the new commons module with `scripts/validate-common-module.sh`.
4. Add or update implementation modules under `implementations/`.

## Why This Repo Exists

Most trading codebases eventually need the same separation of concerns:

- strategy code should not care about exchange-specific payload formats
- connectivity code should handle auth, ticker normalization, and connection lifecycle
- implementations should be swappable across brokers and data providers

This repository exists to keep those boundaries explicit:

- strategy-facing code targets stable interfaces
- exchange-specific details live in commons modules
- concrete runtime adapters live in implementation modules

That makes it easier to:

- add support for new venues
- reuse strategy infrastructure across exchanges
- test components independently
- migrate older integrations toward a more consistent provider/factory model

## Repository Status

This is an actively evolving codebase. Not every integration is equally modernized yet.

The current direction is:

- standardize exchange modules around shared REST and websocket factories
- document exchange conventions clearly enough for humans and code-generation tools
- scaffold new modules with docs, examples, and validation from day one

The Lighter module is the current reference implementation for the newer conventions.

## Quick Start

If you want to explore the project locally:

```bash
mvn test -pl commons/lighter-common-api -am
```

If you want to scaffold a new exchange:

```bash
scripts/new-exchange.sh <exchange-slug> --all --update-exchange-enum
scripts/validate-common-module.sh commons/<exchange>-common-api
```
