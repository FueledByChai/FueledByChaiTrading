package com.fueledbychai.datacollector;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesRestApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.datarecording.RecordedOpenInterest;
import com.fueledbychai.hibachi.common.api.IHibachiRestApi;
import com.fueledbychai.okx.common.api.IOkxRestApi;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Per-venue REST open-interest adapters, mirroring {@link FundingSources}. Each fetches the
 * current open interest for one instrument and maps it to a uniform {@link RecordedOpenInterest}
 * (base/contract units plus a USD notional where reported or derivable as oi x mark). The
 * unparsed venue payload is kept in {@code rawJson} as a safety net.
 */
final class OpenInterestSources {

    private OpenInterestSources() {
    }

    interface OpenInterestSource {
        /** @return the observation, or {@code null} if the venue returned nothing usable. */
        RecordedOpenInterest fetch(Ticker ticker);
    }

    static OpenInterestSource forExchange(Exchange exchange) {
        if (Exchange.OKX.equals(exchange)) {
            return new OkxOiSource();
        }
        if (Exchange.HIBACHI.equals(exchange)) {
            return new HibachiOiSource();
        }
        if (Exchange.PARADEX.equals(exchange)) {
            return new ParadexOiSource();
        }
        if (Exchange.BINANCE_FUTURES.equals(exchange)) {
            return new BinanceFuturesOiSource();
        }
        return null;
    }

    // ----------------------------------------------------------------- OKX

    private static final class OkxOiSource implements OpenInterestSource {
        @Override
        public RecordedOpenInterest fetch(Ticker ticker) {
            IOkxRestApi api = ExchangeRestApiFactory.getPublicApi(Exchange.OKX, IOkxRestApi.class);
            JsonObject o = api.getOpenInterest(ticker.getSymbol());
            if (o == null) {
                return null;
            }
            // OKX: oi (contracts), oiCcy (base), oiUsd (notional), ts (ms)
            BigDecimal oi = firstBd(o, "oiCcy", "oi");
            BigDecimal usd = bd(o, "oiUsd");
            return build(Exchange.OKX, ticker, oi, usd, msToMicros(asLong(o, "ts")), o.toString());
        }
    }

    // ----------------------------------------------------------------- Binance futures

    private static final class BinanceFuturesOiSource implements OpenInterestSource {
        @Override
        public RecordedOpenInterest fetch(Ticker ticker) {
            IBinanceFuturesRestApi api = ExchangeRestApiFactory.getPublicApi(
                    Exchange.BINANCE_FUTURES, IBinanceFuturesRestApi.class);
            JsonNode n = api.getOpenInterest(ticker.getSymbol());
            if (n == null || n.isMissingNode()) {
                return null;
            }
            // {openInterest (base contracts), symbol, time (ms)}; no USD reported here
            return build(Exchange.BINANCE_FUTURES, ticker, bd(n, "openInterest"), null,
                    msToMicros(asLong(n, "time")), n.toString());
        }
    }

    // ----------------------------------------------------------------- Paradex

    private static final class ParadexOiSource implements OpenInterestSource {
        @Override
        public RecordedOpenInterest fetch(Ticker ticker) {
            IParadexRestApi api = ExchangeRestApiFactory.getPublicApi(Exchange.PARADEX, IParadexRestApi.class);
            JsonObject resp = api.getMarketSummary(ticker.getSymbol());
            if (resp == null) {
                return null;
            }
            JsonObject row = firstResult(resp);
            if (row == null) {
                return null;
            }
            BigDecimal oi = bd(row, "open_interest");           // base units
            BigDecimal mark = bd(row, "mark_price");
            BigDecimal usd = (oi != null && mark != null) ? oi.multiply(mark) : null;
            return build(Exchange.PARADEX, ticker, oi, usd, msToMicros(asLong(row, "created_at")),
                    resp.toString());
        }

        private static JsonObject firstResult(JsonObject resp) {
            if (resp.has("results") && resp.get("results").isJsonArray()) {
                JsonArray arr = resp.getAsJsonArray("results");
                return (!arr.isEmpty() && arr.get(0).isJsonObject()) ? arr.get(0).getAsJsonObject() : null;
            }
            return resp.has("open_interest") ? resp : null;
        }
    }

    // ----------------------------------------------------------------- Hibachi

    private static final class HibachiOiSource implements OpenInterestSource {
        @Override
        public RecordedOpenInterest fetch(Ticker ticker) {
            IHibachiRestApi api = ExchangeRestApiFactory.getPublicApi(Exchange.HIBACHI, IHibachiRestApi.class);
            JsonNode resp = api.getOpenInterest(ticker.getSymbol());
            if (resp == null || resp.isMissingNode()) {
                return null;
            }
            // Shape not contractually fixed; unwrap a {"data":...} envelope / array, probe names.
            JsonNode row = resp.has("data") ? resp.get("data") : resp;
            if (row.isArray() && !row.isEmpty()) {
                row = row.get(row.size() - 1);
            }
            // Hibachi returns a flat {"totalQuantity":"..."} (base units, no timestamp).
            BigDecimal oi = firstBd(row, "totalQuantity", "openInterest", "oi", "open_interest", "value");
            return build(Exchange.HIBACHI, ticker, oi, null, 0L, resp.toString());
        }
    }

    // ----------------------------------------------------------------- helpers

    private static RecordedOpenInterest build(Exchange exchange, Ticker ticker, BigDecimal oi,
            BigDecimal oiUsd, long eventMicros, String rawJson) {
        long nowMicros = System.currentTimeMillis() * 1_000L;
        return new RecordedOpenInterest(exchange.getExchangeName(), ticker.getSymbol(), nowMicros,
                eventMicros, oi, oiUsd, rawJson);
    }

    private static long msToMicros(Long ms) {
        return ms == null || ms <= 0L ? 0L : ms * 1_000L;
    }

    private static BigDecimal bd(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal bd(JsonObject o, String field) {
        JsonElement v = o.get(field);
        if (v == null || v.isJsonNull()) {
            return null;
        }
        try {
            return new BigDecimal(v.getAsString().trim());
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return null;
        }
    }

    private static BigDecimal firstBd(JsonNode n, String... fields) {
        for (String f : fields) {
            BigDecimal v = bd(n, f);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal firstBd(JsonObject o, String... fields) {
        for (String f : fields) {
            BigDecimal v = bd(o, f);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static Long asLong(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return v.isNumber() ? v.asLong() : Long.parseLong(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long asLong(JsonObject o, String field) {
        JsonElement v = o.get(field);
        if (v == null || v.isJsonNull()) {
            return null;
        }
        try {
            return Long.parseLong(v.getAsString().trim());
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return null;
        }
    }
}
