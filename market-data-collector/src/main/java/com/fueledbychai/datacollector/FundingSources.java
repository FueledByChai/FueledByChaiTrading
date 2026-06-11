package com.fueledbychai.datacollector;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesRestApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.hibachi.common.api.IHibachiRestApi;
import com.fueledbychai.okx.common.api.IOkxRestApi;
import com.fueledbychai.okx.common.api.ws.model.OkxFundingRateUpdate;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.datarecording.RecordedFundingRate;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Per-venue REST funding-rate adapters. Each {@link FundingSource} fetches the current funding
 * observation for one instrument and maps it to a uniform {@link RecordedFundingRate}, stamping
 * the per-asset interval from the resolved {@link Ticker} (and falling back to a venue-reported
 * interval where available) so the raw rate is normalizable across venues. The unparsed venue
 * payload is always kept in {@code rawJson} as a safety net against shape drift.
 */
final class FundingSources {

    private FundingSources() {
    }

    /** Fetches a single funding observation for one instrument on one venue. */
    interface FundingSource {
        /** @return the observation, or {@code null} if the venue returned nothing usable. */
        RecordedFundingRate fetch(Ticker ticker);
    }

    /** The adapter for an exchange, or {@code null} if funding collection isn't supported there. */
    static FundingSource forExchange(Exchange exchange) {
        if (Exchange.OKX.equals(exchange)) {
            return new OkxFundingSource();
        }
        if (Exchange.HIBACHI.equals(exchange)) {
            return new HibachiFundingSource();
        }
        if (Exchange.PARADEX.equals(exchange)) {
            return new ParadexFundingSource();
        }
        if (Exchange.BINANCE_FUTURES.equals(exchange)) {
            return new BinanceFuturesFundingSource();
        }
        return null;
    }

    // ----------------------------------------------------------------- OKX

    private static final class OkxFundingSource implements FundingSource {
        @Override
        public RecordedFundingRate fetch(Ticker ticker) {
            IOkxRestApi api = ExchangeRestApiFactory.getPublicApi(Exchange.OKX, IOkxRestApi.class);
            OkxFundingRateUpdate u = api.getFundingRate(ticker.getSymbol());
            if (u == null) {
                return null;
            }
            long eventMicros = msToMicros(u.getFundingTime());
            long nextMicros = msToMicros(u.getNextFundingTime());
            // OKX reports both funding and next-funding times; their gap is the true interval.
            int derivedInterval = (u.getFundingTime() != null && u.getNextFundingTime() != null
                    && u.getNextFundingTime() > u.getFundingTime())
                            ? (int) Math.round((u.getNextFundingTime() - u.getFundingTime()) / 3_600_000.0)
                            : 0;
            return build(Exchange.OKX, ticker, u.getFundingRate(), null, eventMicros, nextMicros,
                    derivedInterval, u.toString());
        }
    }

    // ----------------------------------------------------------------- Binance futures

    private static final class BinanceFuturesFundingSource implements FundingSource {
        @Override
        public RecordedFundingRate fetch(Ticker ticker) {
            IBinanceFuturesRestApi api = ExchangeRestApiFactory.getPublicApi(
                    Exchange.BINANCE_FUTURES, IBinanceFuturesRestApi.class);
            JsonNode n = api.getPremiumIndex(ticker.getSymbol());
            if (n == null || n.isMissingNode()) {
                return null;
            }
            BigDecimal rate = bd(n, "lastFundingRate");
            BigDecimal mark = bd(n, "markPrice");
            long eventMicros = msToMicros(asLong(n, "time"));
            long nextMicros = msToMicros(asLong(n, "nextFundingTime"));
            return build(Exchange.BINANCE_FUTURES, ticker, rate, mark, eventMicros, nextMicros, 0, n.toString());
        }
    }

    // ----------------------------------------------------------------- Paradex

    private static final class ParadexFundingSource implements FundingSource {
        @Override
        public RecordedFundingRate fetch(Ticker ticker) {
            IParadexRestApi api = ExchangeRestApiFactory.getPublicApi(Exchange.PARADEX, IParadexRestApi.class);
            JsonObject resp = api.getMarketSummary(ticker.getSymbol());
            if (resp == null) {
                return null;
            }
            JsonObject row = firstResult(resp);
            if (row == null) {
                return null;
            }
            BigDecimal rate = bd(row, "funding_rate");
            BigDecimal mark = bd(row, "mark_price");
            long eventMicros = msToMicros(asLong(row, "created_at"));
            // Paradex summary carries no next-funding time; the interval comes from the descriptor.
            return build(Exchange.PARADEX, ticker, rate, mark, eventMicros, 0L, 0, resp.toString());
        }

        private static JsonObject firstResult(JsonObject resp) {
            if (resp.has("results") && resp.get("results").isJsonArray()) {
                JsonArray arr = resp.getAsJsonArray("results");
                if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                    return arr.get(0).getAsJsonObject();
                }
                return null;
            }
            // Some deployments return the bare object; tolerate it.
            return resp.has("funding_rate") ? resp : null;
        }
    }

    // ----------------------------------------------------------------- Hibachi

    private static final class HibachiFundingSource implements FundingSource {
        @Override
        public RecordedFundingRate fetch(Ticker ticker) {
            IHibachiRestApi api = ExchangeRestApiFactory.getPublicApi(Exchange.HIBACHI, IHibachiRestApi.class);
            JsonNode resp = api.getFundingRates(ticker.getSymbol());
            if (resp == null || resp.isMissingNode()) {
                return null;
            }
            // Response is {"data":[{contractId, fundingTimestamp (epoch SECONDS), fundingRate,
            // indexPrice}, ...]} ascending by time — the last entry is the most recent settled
            // funding. Hibachi settles HOURLY (timestamps 3600s apart), not 8h, so we derive the
            // true interval from the last two timestamps rather than trust the 8h descriptor default.
            JsonNode data = resp.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                return null;
            }
            JsonNode last = data.get(data.size() - 1);
            BigDecimal rate = bd(last, "fundingRate");
            BigDecimal indexPrice = bd(last, "indexPrice"); // index price stands in for mark
            long eventMicros = secondsToMicros(asDouble(last, "fundingTimestamp"));
            int derivedInterval = 0;
            if (data.size() >= 2) {
                Double prev = asDouble(data.get(data.size() - 2), "fundingTimestamp");
                Double curr = asDouble(last, "fundingTimestamp");
                if (prev != null && curr != null && curr > prev) {
                    derivedInterval = (int) Math.round((curr - prev) / 3600.0);
                }
            }
            return build(Exchange.HIBACHI, ticker, rate, indexPrice, eventMicros, 0L, derivedInterval, resp.toString());
        }
    }

    // ----------------------------------------------------------------- shared helpers

    private static RecordedFundingRate build(Exchange exchange, Ticker ticker, BigDecimal rate,
            BigDecimal markPrice, long eventMicros, long nextMicros, int derivedIntervalHours, String rawJson) {
        long nowMicros = System.currentTimeMillis() * 1_000L;
        // Prefer an interval DERIVED from actual consecutive funding timestamps (ground truth) over
        // the instrument descriptor, whose default (8h) is wrong for venues like Hibachi that settle
        // hourly. Fall back to the descriptor when no derivation is available (e.g. Paradex/Binance,
        // where Paradex carries a real per-asset value and Binance defaults to 8h).
        int interval = derivedIntervalHours > 0 ? derivedIntervalHours : ticker.getFundingRateInterval();
        BigDecimal annualized = RecordedFundingRate.annualize(rate, interval);
        return new RecordedFundingRate(exchange.getExchangeName(), ticker.getSymbol(), nowMicros,
                eventMicros, rate, interval, nextMicros, markPrice, annualized, rawJson);
    }

    private static long msToMicros(Long ms) {
        return ms == null || ms <= 0L ? 0L : ms * 1_000L;
    }

    private static long secondsToMicros(Double seconds) {
        return seconds == null || seconds <= 0.0 ? 0L : Math.round(seconds * 1_000_000.0);
    }

    private static Double asDouble(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return v.isNumber() ? v.asDouble() : Double.parseDouble(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
