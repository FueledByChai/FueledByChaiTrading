package com.fueledbychai.marketdata.binancefutures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.binancefutures.common.api.BinanceFuturesConfiguration;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesRestApi;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesWebSocketApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.Level1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2Quote;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderBook;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class BinanceFuturesQuoteEngine extends QuoteEngine {

    private static final Logger logger = LoggerFactory.getLogger(BinanceFuturesQuoteEngine.class);
    private static final BigDecimal BPS_MULTIPLIER = new BigDecimal("10000");
    private static final BigDecimal APR_MULTIPLIER = new BigDecimal("876000");
    private static final int DEFAULT_DEPTH = 20;
    private static final int DEFAULT_FUNDING_INTERVAL_HOURS = 8;

    protected volatile boolean started = false;
    protected final IBinanceFuturesRestApi restApi;
    protected final IBinanceFuturesWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;
    protected final Set<Ticker> level1StreamsStarted = ConcurrentHashMap.newKeySet();
    protected final Set<Ticker> depthStreamsStarted = ConcurrentHashMap.newKeySet();
    protected final Set<Ticker> tradeStreamsStarted = ConcurrentHashMap.newKeySet();

    public BinanceFuturesQuoteEngine() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.BINANCE_FUTURES, IBinanceFuturesRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.BINANCE_FUTURES, IBinanceFuturesWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.BINANCE_FUTURES));
        logger.info("Binance Futures WebSocket URL: {}", BinanceFuturesConfiguration.getInstance().getWebSocketUrl());
    }

    protected BinanceFuturesQuoteEngine(IBinanceFuturesRestApi restApi, IBinanceFuturesWebSocketApi webSocketApi,
            ITickerRegistry tickerRegistry) {
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        if (webSocketApi == null) {
            throw new IllegalArgumentException("webSocketApi is required");
        }
        if (tickerRegistry == null) {
            throw new IllegalArgumentException("tickerRegistry is required");
        }
        this.restApi = restApi;
        this.webSocketApi = webSocketApi;
        this.tickerRegistry = tickerRegistry;
    }

    @Override
    public String getDataProviderName() {
        return "BinanceFutures";
    }

    @Override
    public Date getServerTime() {
        return restApi.getServerTime();
    }

    @Override
    public boolean isConnected() {
        return started;
    }

    @Override
    public void startEngine() {
        started = true;
        webSocketApi.connect();
    }

    @Override
    public void startEngine(Properties props) {
        startEngine();
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public void stopEngine() {
        started = false;
        level1StreamsStarted.clear();
        depthStreamsStarted.clear();
        tradeStreamsStarted.clear();
        webSocketApi.disconnectAll();
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        requireTicker(ticker);
        if (level1StreamsStarted.add(ticker)) {
            startBookTickerStream(ticker);
            startSymbolTickerStream(ticker);
            startMarkPriceStream(ticker);
        }
        super.subscribeLevel1(ticker, listener);
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        requireTicker(ticker);
        if (depthStreamsStarted.add(ticker)) {
            startDepthStream(ticker);
        }
        super.subscribeMarketDepth(ticker, listener);
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        requireTicker(ticker);
        if (tradeStreamsStarted.add(ticker)) {
            startTradeStream(ticker);
        }
        super.subscribeOrderFlow(ticker, listener);
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.error("useDelayedData() is not supported for Binance futures market data");
    }

    void onBookTickerUpdate(Ticker ticker, JsonNode message) {
        BigDecimal bestBid = decimalValue(message, "b");
        BigDecimal bidSize = decimalValue(message, "B");
        BigDecimal bestAsk = decimalValue(message, "a");
        BigDecimal askSize = decimalValue(message, "A");
        if (bestBid == null && bestAsk == null) {
            return;
        }

        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "E"));
        if (bestBid != null) {
            quote.addQuote(QuoteType.BID, bestBid);
        }
        if (bidSize != null) {
            quote.addQuote(QuoteType.BID_SIZE, bidSize);
        }
        if (bestAsk != null) {
            quote.addQuote(QuoteType.ASK, bestAsk);
        }
        if (askSize != null) {
            quote.addQuote(QuoteType.ASK_SIZE, askSize);
        }
        fireLevel1Quote(quote);
    }

    void onMarkPriceUpdate(Ticker ticker, JsonNode message) {
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "E"));
        boolean hasValues = false;

        BigDecimal markPrice = decimalValue(message, "p");
        if (markPrice != null) {
            quote.addQuote(QuoteType.MARK_PRICE, markPrice);
            hasValues = true;
        }

        BigDecimal underlyingPrice = decimalValue(message, "i");
        if (underlyingPrice != null) {
            quote.addQuote(QuoteType.UNDERLYING_PRICE, underlyingPrice);
            hasValues = true;
        }

        BigDecimal fundingRate = decimalValue(message, "r");
        if (fundingRate != null) {
            BigDecimal hourlyFundingRate = toHourlyFundingRate(ticker, fundingRate);
            quote.addQuote(QuoteType.FUNDING_RATE_HOURLY_BPS, hourlyFundingRate.multiply(BPS_MULTIPLIER));
            quote.addQuote(QuoteType.FUNDING_RATE_APR, hourlyFundingRate.multiply(APR_MULTIPLIER));
            hasValues = true;
        }

        if (hasValues) {
            fireLevel1Quote(quote);
        }
    }

    void onSymbolTickerUpdate(Ticker ticker, JsonNode message) {
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "E"));
        boolean hasValues = false;

        BigDecimal volume = decimalValue(message, "v");
        if (volume != null) {
            quote.addQuote(QuoteType.VOLUME, volume);
            hasValues = true;
        }

        BigDecimal volumeNotional = decimalValue(message, "q");
        if (volumeNotional != null) {
            quote.addQuote(QuoteType.VOLUME_NOTIONAL, volumeNotional);
            hasValues = true;
        }

        if (hasValues) {
            fireLevel1Quote(quote);
        }
    }

    void onOrderBookUpdate(Ticker ticker, JsonNode message) {
        List<OrderBook.PriceLevel> bids = toPriceLevels(message.path("b"));
        List<OrderBook.PriceLevel> asks = toPriceLevels(message.path("a"));
        if (bids.isEmpty() && asks.isEmpty()) {
            return;
        }

        OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());
        ZonedDateTime timestamp = toTimestamp(message, "E");
        orderBook.updateFromSnapshot(bids, asks, timestamp);
        fireMarketDepthQuote(new Level2Quote(ticker, orderBook, timestamp));
    }

    void onTradeUpdate(Ticker ticker, JsonNode message) {
        BigDecimal price = decimalValue(message, "p");
        BigDecimal size = decimalValue(message, "q");
        if (price == null || size == null) {
            return;
        }

        OrderFlow.Side side = message.path("m").asBoolean(false) ? OrderFlow.Side.SELL : OrderFlow.Side.BUY;
        OrderFlow orderFlow = new OrderFlow(ticker, price, size, side, toTimestamp(message, "T"));
        fireOrderFlow(orderFlow);
    }

    protected void startBookTickerStream(Ticker ticker) {
        webSocketApi.subscribeBookTicker(ticker, message -> safeRun(() -> onBookTickerUpdate(ticker, message),
                "bookTicker", ticker));
    }

    protected void startMarkPriceStream(Ticker ticker) {
        webSocketApi.subscribeMarkPrice(ticker, message -> safeRun(() -> onMarkPriceUpdate(ticker, message),
                "markPrice", ticker));
    }

    protected void startSymbolTickerStream(Ticker ticker) {
        webSocketApi.subscribeSymbolTicker(ticker, message -> safeRun(() -> onSymbolTickerUpdate(ticker, message),
                "ticker", ticker));
    }

    protected void startDepthStream(Ticker ticker) {
        webSocketApi.subscribePartialDepth(ticker, DEFAULT_DEPTH,
                message -> safeRun(() -> onOrderBookUpdate(ticker, message), "depth", ticker));
    }

    protected void startTradeStream(Ticker ticker) {
        webSocketApi.subscribeAggTrades(ticker, message -> safeRun(() -> onTradeUpdate(ticker, message), "aggTrade", ticker));
    }

    protected void safeRun(Runnable action, String streamType, Ticker ticker) {
        try {
            action.run();
        } catch (Exception e) {
            logger.warn("Failed to process {} update for {}", streamType, ticker.getSymbol(), e);
        }
    }

    protected void requireTicker(Ticker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
    }

    protected BigDecimal decimalValue(JsonNode message, String field) {
        if (message == null || field == null) {
            return null;
        }
        String value = message.path(field).asText("");
        if (value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    protected ZonedDateTime toTimestamp(JsonNode message, String field) {
        long epochMillis = message == null ? 0L : message.path(field).asLong(0L);
        if (epochMillis <= 0L) {
            return ZonedDateTime.now(ZoneId.of("UTC"));
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("UTC"));
    }

    protected BigDecimal toHourlyFundingRate(Ticker ticker, BigDecimal fundingRate) {
        int intervalHours = ticker == null || ticker.getFundingRateInterval() <= 0 ? DEFAULT_FUNDING_INTERVAL_HOURS
                : ticker.getFundingRateInterval();
        return fundingRate.divide(BigDecimal.valueOf(intervalHours), MathContext.DECIMAL64);
    }

    protected List<OrderBook.PriceLevel> toPriceLevels(JsonNode levels) {
        List<OrderBook.PriceLevel> priceLevels = new ArrayList<>();
        if (levels == null || !levels.isArray()) {
            return priceLevels;
        }
        for (JsonNode level : levels) {
            if (level == null || !level.isArray() || level.size() < 2) {
                continue;
            }
            String priceString = level.get(0).asText("");
            String quantityString = level.get(1).asText("");
            if (priceString.isBlank() || quantityString.isBlank()) {
                continue;
            }
            priceLevels.add(new OrderBook.PriceLevel(new BigDecimal(priceString), Double.parseDouble(quantityString)));
        }
        return priceLevels;
    }
}
