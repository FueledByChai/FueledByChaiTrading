package com.fueledbychai.grvt.historical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.BarData;
import com.fueledbychai.data.BarData.LengthUnit;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.grvt.common.api.IGrvtRestApi;
import com.fueledbychai.historicaldata.IHistoricalDataProvider;
import com.fueledbychai.util.ExchangeRestApiFactory;

/**
 * Historical OHLC provider for GRVT, backed by the market-data {@code kline} REST endpoint.
 * <p>
 * As with the live market-data feed, GRVT candlestick prices and unit volumes are fixed-point
 * integers scaled by {@code 10^9}; values are divided by {@link #SCALE} here.
 */
public class GrvtHistoricalDataProvider implements IHistoricalDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(GrvtHistoricalDataProvider.class);
    private static final BigDecimal SCALE = BigDecimal.TEN.pow(9);
    private static final int DEFAULT_LIMIT = 1000;
    private static final ZoneId UTC = ZoneId.of("UTC");

    protected boolean connected = true;
    protected IGrvtRestApi restApi;

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void connect() {
        // no-op; REST is stateless
    }

    @Override
    public void init(Properties props) {
        restApi = ExchangeRestApiFactory.getPublicApi(Exchange.GRVT, IGrvtRestApi.class);
        connected = true;
    }

    @Override
    public List<BarData> requestHistoricalData(Ticker ticker, int duration, BarData.LengthUnit durationLengthUnit,
            int barSize, BarData.LengthUnit barSizeUnit, ShowProperty whatToShow, boolean useRTH) {
        if (restApi == null) {
            init(null);
        }
        if (!(whatToShow == ShowProperty.TRADES || whatToShow == ShowProperty.MARK_PRICE)) {
            throw new FueledByChaiException("Only historical trades or mark price are supported");
        }

        String interval = toGrvtInterval(barSize, barSizeUnit);
        String type = whatToShow == ShowProperty.MARK_PRICE ? "MARK" : "TRADE";
        long lookbackMinutes = getDurationInMinutes(duration, durationLengthUnit);
        long nowMillis = System.currentTimeMillis();
        long startNanos = (nowMillis - lookbackMinutes * 60_000L) * 1_000_000L;
        long endNanos = nowMillis * 1_000_000L;

        JsonNode result = restApi.getCandlestick(ticker.getSymbol(), interval, type, startNanos, endNanos,
                DEFAULT_LIMIT);

        List<BarData> bars = new ArrayList<>();
        if (result != null && result.isArray()) {
            for (JsonNode candle : result) {
                BarData bar = toBarData(ticker, candle, barSize, barSizeUnit);
                if (bar != null) {
                    bars.add(bar);
                }
            }
        }
        return bars;
    }

    @Override
    public List<BarData> requestHistoricalData(Ticker ticker, Date endDateTime, int duration,
            BarData.LengthUnit durationLengthUnit, int barSize, BarData.LengthUnit barSizeUnit, ShowProperty whatToShow,
            boolean useRTH) {
        throw new FueledByChaiException("Not yet implemented");
    }

    protected BarData toBarData(Ticker ticker, JsonNode candle, int barSize, LengthUnit barSizeUnit) {
        if (candle == null) {
            return null;
        }
        long openTimeNanos = candle.path("open_time").asLong(0L);
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(openTimeNanos / 1_000_000L), UTC);
        BigDecimal open = scaled(candle, "open");
        BigDecimal high = scaled(candle, "high");
        BigDecimal low = scaled(candle, "low");
        BigDecimal close = scaled(candle, "close");
        BigDecimal volume = scaled(candle, "volume_u");
        if (open == null || high == null || low == null || close == null) {
            return null;
        }
        return new BarData(ticker, dateTime, open, high, low, close, volume == null ? BigDecimal.ZERO : volume, barSize,
                barSizeUnit);
    }

    protected String toGrvtInterval(int barSize, LengthUnit barSizeUnit) {
        String interval = switch (barSizeUnit) {
            case MINUTE -> switch (barSize) {
                case 1 -> "CI_1_M";
                case 3 -> "CI_3_M";
                case 5 -> "CI_5_M";
                case 15 -> "CI_15_M";
                case 30 -> "CI_30_M";
                default -> null;
            };
            case HOUR -> switch (barSize) {
                case 1 -> "CI_1_H";
                case 2 -> "CI_2_H";
                case 4 -> "CI_4_H";
                case 6 -> "CI_6_H";
                case 8 -> "CI_8_H";
                case 12 -> "CI_12_H";
                default -> null;
            };
            case DAY -> switch (barSize) {
                case 1 -> "CI_1_D";
                case 3 -> "CI_3_D";
                case 5 -> "CI_5_D";
                default -> null;
            };
            case WEEK -> switch (barSize) {
                case 1 -> "CI_1_W";
                case 2 -> "CI_2_W";
                case 3 -> "CI_3_W";
                case 4 -> "CI_4_W";
                default -> null;
            };
            default -> null;
        };
        if (interval == null) {
            throw new InvalidBarSizeException(
                    "Unsupported GRVT bar size " + barSize + " " + barSizeUnit + " (see CandlestickInterval).");
        }
        return interval;
    }

    protected long getDurationInMinutes(int duration, LengthUnit lengthUnit) {
        return switch (lengthUnit) {
            case MINUTE -> duration;
            case HOUR -> (long) duration * 60L;
            case DAY -> (long) duration * 60L * 24L;
            case WEEK -> (long) duration * 60L * 24L * 7L;
            case MONTH -> (long) duration * 60L * 24L * 30L;
            case YEAR -> (long) duration * 60L * 24L * 365L;
            default -> throw new IllegalArgumentException("Unsupported length unit: " + lengthUnit);
        };
    }

    protected BigDecimal scaled(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value).divide(SCALE);
        } catch (NumberFormatException | ArithmeticException e) {
            logger.debug("Unable to parse GRVT candle field {}={}", field, value);
            return null;
        }
    }
}
