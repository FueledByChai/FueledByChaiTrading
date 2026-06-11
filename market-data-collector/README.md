# market-data-collector

Always-on app that records raw **trade tape** and **L2 order book** from every configured
venue (Paradex, Hibachi, Binance futures/spot, OKX) to partitioned **Parquet**, for offline
fair-value / microstructure forecasting research.

It wires each venue's `QuoteEngine` to the `fueledbychai-data-recording-api` contract via the
`Recording*` adapters, and persists through the Carpet-backed `ParquetMarketDataRecorder`
(`implementations/data-recording-api`). A daily compaction pass merges each finished day's
small files into one per partition.

---

## Build

From the repo root (`FueledByChaiTrading/`):

```bash
mvn -pl market-data-collector -am -DskipTests package
```

Produces a runnable fat jar:

```
market-data-collector/target/market-data-collector.jar   (~65 MB)
```

Requires JDK 21+ (the libraries target 21; running on 25 is fine).

---

## Configure

A Java `.properties` file:

```properties
# Partition-tree root. Created if absent.
collector.root.dir=/data/marketdata

# Comma-separated EXCHANGE:SYMBOL:INSTRUMENT_TYPE specs.
collector.instruments=PARADEX:SOL:PERPETUAL_FUTURES,HIBACHI:SOL:PERPETUAL_FUTURES,BINANCE_FUTURES:SOL:PERPETUAL_FUTURES,BINANCE_SPOT:SOL:CRYPTO_SPOT,OKX:SOL:PERPETUAL_FUTURES
```

- **Exchanges:** `PARADEX`, `HIBACHI`, `BINANCE_FUTURES`, `BINANCE_SPOT`, `OKX`
- **Instrument types:** `PERPETUAL_FUTURES`, `CRYPTO_SPOT`
- `SYMBOL` is the venue's common symbol (e.g. `SOL`); each venue's registry maps it to its
  own market symbol (`SOL-USD-PERP`, `SOLUSDT`, …).
- Unresolvable / unreachable venues are logged and **skipped** — the collector keeps running.

With no config-file argument the app falls back to a built-in default: SOL across all five
venues into `./data`.

---

## Run

```bash
java -jar market-data-collector/target/market-data-collector.jar /path/to/collector.properties
```

Stop with `Ctrl-C` / `SIGTERM` — the shutdown hook flushes open Parquet files (writes their
footers so they're readable) before exiting.

### Geo-restriction & the SOCKS proxy

Binance and OKX **geo-block non-eligible regions (HTTP 451)**. The production host (Tokyo
AWS) is eligible, so it connects **directly — no proxy**. From a restricted location (e.g. a
local Mac) route those venues through a SOCKS proxy:

```bash
# 1. Open a SOCKS tunnel to the Tokyo box (separate terminal, keep it running):
ssh -D 1080 <tokyo-host>

# 2. Run the collector through it:
java -Dfueledbychai.run.proxy=true \
     -Dfueledbychai.proxy.host=127.0.0.1 \
     -Dfueledbychai.proxy.port=1080 \
     -jar market-data-collector/target/market-data-collector.jar collector.properties
```

`ProxyConfig` reads those system properties (host/port default to `127.0.0.1:1080`). Paradex
and Hibachi are not geo-blocked and work with or without the proxy. **On the Tokyo server,
omit all proxy flags.**

### Venue feed toggles (set automatically)

The app sets these at startup (before any engine is built) so it gets the richest book feed
each venue offers. Override with `-D` if needed:

| Property | Default (set by app) | Effect |
|---|---|---|
| `hibachi.market.data.live.book` | `true` | Use Hibachi's public windowed `live_book` (~5 ms, top-N) instead of the ~250 ms `orderbook`. |
| `paradex.orderbook.channel.suffix` | `deltas` | Subscribe Paradex's true per-level **delta** stream instead of the throttled top-15 snapshot channel. |

(These default to their *trading* values inside the engines — `false` / `interactive@15@200ms`
— so chaiwala is unaffected; only the collector opts in.)

---

## Output layout

Hive-partitioned Parquet, zstd-compressed:

```
{root}/{dataType}/exchange={EX}/symbol={SYM}/date=YYYY-MM-DD/part-*.parquet
```

`dataType` ∈ `trades`, `book_events`, `book_snapshots` (and `own_events` if a trading app
records through the same contract). What each venue produces:

| Venue | trades | book_events | book_snapshots | archetype |
|---|---|---|---|---|
| Paradex | ✅ | ✅ sequenced (`exchangeSeq`) | ✅ (delta-stream anchors) | sequenced deltas |
| Binance fut/spot | ✅ | ✅ sequenced | ✅ (initial anchor) | sequenced deltas |
| OKX | ✅ | ✅ sequenced | ✅ | sequenced deltas |
| Hibachi | ✅ | ✅ windowed (`windowStart/EndPrice`, no seq) | ✅ | windowed / un-sequenced |

All rows carry **two clocks**: `recvTimestampMicros` (our local clock — align cross-venue on
this, no inter-venue skew) and `eventTimestampMicros` (venue clock). `book_events` carry
`exchangeSeq`/`prevExchangeSeq` + `bookEpoch` (sequenced venues) or the window bounds + epoch
(Hibachi) so an offline pass can reconstruct the book and flag gaps.

### File rolling & compaction

- Live files roll at **~15 min or ~2 M rows** (whichever first), bounding crash loss to one
  roll interval.
- A **daily** pass (`ParquetCompactor`) merges each completed day's `part-*.parquet` into one
  file per partition — Parquet→Parquet, so reads stay single-format. Runs automatically every
  24 h; can also be invoked directly.

---

## Reading the data

Anything that reads Parquet works. With DuckDB:

```bash
pip install duckdb
```

```python
import duckdb
con = duckdb.connect()

# Cross-venue trade tape for SOL on a day:
con.execute("""
  select exchange, count(*)
  from read_parquet('/data/marketdata/trades/**/*.parquet')
  where date = '2026-06-10' group by 1
""").df()

# Snap a Binance trade to the prevailing book (align on recvTimestampMicros):
con.execute("""
  select t.recvTimestampMicros, t.price, t.aggressor, b.price as book_lvl, b.newSize
  from read_parquet('/data/marketdata/trades/exchange=BINANCE_FUTURES/**/*.parquet') t
  asof join read_parquet('/data/marketdata/book_events/exchange=BINANCE_FUTURES/**/*.parquet') b
    on t.recvTimestampMicros >= b.recvTimestampMicros
  limit 20
""").df()
```

`trades` columns: `exchange, symbol, recvTimestampMicros, eventTimestampMicros, exchangeSeq,
price, size, aggressor`. `book_events`: add `prevExchangeSeq, bookEpoch, side, newSize, action,
windowStartPrice, windowEndPrice`. `book_snapshots`: `... anchor, bids[], asks[]` (nested
`{price,size}` levels).

---

## Deploying on the Tokyo server

No proxy needed there. Example with a writable data disk and logs:

```bash
nohup java -jar market-data-collector.jar /etc/marketdata/collector.properties \
  > /var/log/marketdata/collector.out 2>&1 &
```

Or as a systemd unit:

```ini
[Unit]
Description=FBC market-data collector
After=network-online.target

[Service]
ExecStart=/usr/bin/java -jar /opt/marketdata/market-data-collector.jar /etc/marketdata/collector.properties
Restart=always
RestartSec=10
# SIGTERM triggers a clean flush of open Parquet files:
KillSignal=SIGTERM
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
```

Watch disk: `book_events` is the firehose (Binance/OKX can be tens of thousands of rows per
minute per symbol). Compaction reclaims the small-file overhead but not raw volume — size the
data disk for the symbol set and retention you want.

---

## Logging

`logback.xml` (bundled) keeps the console readable: app at INFO, `org.apache.parquet` /
`org.apache.hadoop` / WebSocket stacks at WARN. The collector's own `System.Logger` output is
routed through SLF4J/Logback via `slf4j-jdk-platform-logging`.
