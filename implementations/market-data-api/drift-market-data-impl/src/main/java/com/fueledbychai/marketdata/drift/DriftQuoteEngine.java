package com.fueledbychai.marketdata.drift;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.drift.common.api.DriftConfiguration;
import com.fueledbychai.drift.common.api.IDriftRestApi;
import com.fueledbychai.drift.common.api.IDriftWebSocketApi;
import com.fueledbychai.drift.common.api.model.DriftMarket;
import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.drift.common.api.model.DriftOrderBookLevel;
import com.fueledbychai.drift.common.api.model.DriftOrderBookSnapshot;
import com.fueledbychai.marketdata.Level1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2Quote;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderBook;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteError;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class DriftQuoteEngine extends QuoteEngine {

    protected static final Logger logger = LoggerFactory.getLogger(DriftQuoteEngine.class);
    protected static final ZoneId UTC = ZoneId.of("UTC");
    protected static final long DEFAULT_STATS_REFRESH_SECONDS = 5L;

    protected final IDriftRestApi restApi;
    protected final IDriftWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;
    protected final Map<String, Ticker> tickerBySymbol = new ConcurrentHashMap<>();
    protected final Set<String> orderBookSubscriptions = ConcurrentHashMap.newKeySet();
    protected final Set<String> marketStatsSubscriptions = ConcurrentHashMap.newKeySet();

    protected volatile ScheduledExecutorService marketStatsScheduler;
    protected volatile boolean started;

    public DriftQuoteEngine() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.DRIFT, IDriftRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.DRIFT, IDriftWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.DRIFT));
    }

    protected DriftQuoteEngine(IDriftRestApi restApi, IDriftWebSocketApi webSocketApi, ITickerRegistry tickerRegistry) {
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
        return "Drift";
    }

    @Override
    public Date getServerTime() {
        return new Date();
    }

    @Override
    public boolean isConnected() {
        return started;
    }

    @Override
    public synchronized void startEngine() {
        if (started) {
            return;
        }
        started = true;
        ensureMarketStatsScheduler();
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
    public synchronized void stopEngine() {
        started = false;
        webSocketApi.disconnectAll();
        if (marketStatsScheduler != null) {
            marketStatsScheduler.shutdownNow();
            marketStatsScheduler = null;
        }
        orderBookSubscriptions.clear();
        marketStatsSubscriptions.clear();
        tickerBySymbol.clear();
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.warn("useDelayedData() is not supported for Drift");
    }

    @Override
    public synchronized void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        validateTickerAndListener(ticker, listener);
        tickerBySymbol.put(ticker.getSymbol(), ticker);
        ensureOrderBookSubscription(ticker);
        marketStatsSubscriptions.add(ticker.getSymbol());
        ensureMarketStatsScheduler();
        publishMarketStatsSnapshot(ticker);
        super.subscribeLevel1(ticker, listener);
    }

    @Override
    public synchronized void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        validateTickerAndListener(ticker, listener);
        tickerBySymbol.put(ticker.getSymbol(), ticker);
        ensureOrderBookSubscription(ticker);
        super.subscribeMarketDepth(ticker, listener);
    }

    @Override
    public synchronized void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        validateTickerAndListener(ticker, listener);
        fireErrorEvent(new QuoteError("Drift order-flow subscriptions are not implemented yet"));
        super.subscribeOrderFlow(ticker, listener);
    }

    protected void ensureOrderBookSubscription(Ticker ticker) {
        if (orderBookSubscriptions.add(ticker.getSymbol())) {
            DriftMarketType marketType = ticker.getInstrumentType() == com.fueledbychai.data.InstrumentType.CRYPTO_SPOT
                    ? DriftMarketType.SPOT
                    : DriftMarketType.PERP;
            webSocketApi.subscribeOrderBook(ticker.getSymbol(), marketType, this::handleOrderBookUpdate);
        }
    }

    protected void ensureMarketStatsScheduler() {
        if (marketStatsScheduler != null && !marketStatsScheduler.isShutdown()) {
            return;
        }
        synchronized (this) {
            if (marketStatsScheduler != null && !marketStatsScheduler.isShutdown()) {
                return;
            }
            marketStatsScheduler = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "drift-market-stats-poller"));
            marketStatsScheduler.scheduleAtFixedRate(this::pollMarketStats, DEFAULT_STATS_REFRESH_SECONDS,
                    DEFAULT_STATS_REFRESH_SECONDS, TimeUnit.SECONDS);
        }
    }

    protected void pollMarketStats() {
        if (!started || marketStatsSubscriptions.isEmpty()) {
            return;
        }
        for (String symbol : marketStatsSubscriptions) {
            Ticker ticker = tickerBySymbol.get(symbol);
            if (ticker != null && hasLevel1Listeners(ticker)) {
                publishMarketStatsSnapshot(ticker);
            }
        }
    }

    protected void publishMarketStatsSnapshot(Ticker ticker) {
        try {
            DriftMarket market = restApi.getMarket(ticker.getSymbol());
            if (market == null) {
                return;
            }
            Level1Quote quote = buildMarketStatsQuote(ticker, market);
            if (quote.hasUpdates()) {
                fireLevel1Quote(quote);
            }
        } catch (Exception e) {
            logger.warn("Failed to refresh Drift market stats for {}", ticker.getSymbol(), e);
        }
    }

    protected void handleOrderBookUpdate(DriftOrderBookSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Ticker ticker = tickerBySymbol.get(snapshot.getMarketName());
        if (ticker == null) {
            return;
        }

        ZonedDateTime timestamp = timestamp(snapshot.getTimestampMillis());
        OrderBook orderBook = buildOrderBook(ticker, snapshot, timestamp);

        if (hasLevel2Listeners(ticker)) {
            fireMarketDepthQuote(new Level2Quote(ticker, orderBook, timestamp));
        }
        if (hasLevel1Listeners(ticker)) {
            Level1Quote quote = new Level1Quote(ticker, timestamp);
            if (snapshot.getBestBidPrice() != null) {
                quote.addQuote(QuoteType.BID, ticker.formatPrice(snapshot.getBestBidPrice()));
            }
            if (snapshot.getBestAskPrice() != null) {
                quote.addQuote(QuoteType.ASK, ticker.formatPrice(snapshot.getBestAskPrice()));
            }
            DriftOrderBookLevel bestBidLevel = snapshot.getBids().isEmpty() ? null : snapshot.getBids().get(0);
            DriftOrderBookLevel bestAskLevel = snapshot.getAsks().isEmpty() ? null : snapshot.getAsks().get(0);
            if (bestBidLevel != null) {
                quote.addQuote(QuoteType.BID_SIZE, bestBidLevel.getSize());
            }
            if (bestAskLevel != null) {
                quote.addQuote(QuoteType.ASK_SIZE, bestAskLevel.getSize());
            }
            if (snapshot.getMarkPrice() != null) {
                quote.addQuote(QuoteType.MARK_PRICE, ticker.formatPrice(snapshot.getMarkPrice()));
            }
            if (snapshot.getOraclePrice() != null) {
                quote.addQuote(QuoteType.UNDERLYING_PRICE, ticker.formatPrice(snapshot.getOraclePrice()));
            }
            if (quote.hasUpdates()) {
                fireLevel1Quote(quote);
            }
        }
    }

    protected OrderBook buildOrderBook(Ticker ticker, DriftOrderBookSnapshot snapshot, ZonedDateTime timestamp) {
        OrderBook orderBook = new OrderBook(ticker);
        List<OrderBook.PriceLevel> bids = new ArrayList<>();
        List<OrderBook.PriceLevel> asks = new ArrayList<>();
        for (DriftOrderBookLevel level : snapshot.getBids()) {
            bids.add(new OrderBook.PriceLevel(ticker.formatPrice(level.getPrice()), level.getSize().doubleValue()));
        }
        for (DriftOrderBookLevel level : snapshot.getAsks()) {
            asks.add(new OrderBook.PriceLevel(ticker.formatPrice(level.getPrice()), level.getSize().doubleValue()));
        }
        orderBook.updateFromSnapshot(bids, asks, timestamp);
        return orderBook;
    }

    protected Level1Quote buildMarketStatsQuote(Ticker ticker, DriftMarket market) {
        ZonedDateTime timestamp = market.getFundingRateUpdateTs() == null ? ZonedDateTime.now(UTC)
                : ZonedDateTime.ofInstant(Instant.ofEpochSecond(market.getFundingRateUpdateTs()), UTC);
        Level1Quote quote = new Level1Quote(ticker, timestamp);
        if (market.getLastPrice() != null) {
            quote.addQuote(QuoteType.LAST, ticker.formatPrice(market.getLastPrice()));
        }
        if (market.getMarkPrice() != null) {
            quote.addQuote(QuoteType.MARK_PRICE, ticker.formatPrice(market.getMarkPrice()));
        }
        if (market.getOraclePrice() != null) {
            quote.addQuote(QuoteType.UNDERLYING_PRICE, ticker.formatPrice(market.getOraclePrice()));
        }
        if (market.getBaseVolume() != null) {
            quote.addQuote(QuoteType.VOLUME, market.getBaseVolume());
        }
        if (market.getQuoteVolume() != null) {
            quote.addQuote(QuoteType.VOLUME_NOTIONAL, market.getQuoteVolume());
        }

        BigDecimal openInterest = totalOpenInterest(market);
        if (openInterest != null) {
            quote.addQuote(QuoteType.OPEN_INTEREST, openInterest);
            if (market.getMarkPrice() != null) {
                quote.addQuote(QuoteType.OPEN_INTEREST_NOTIONAL, openInterest.multiply(market.getMarkPrice()));
            }
        }

        BigDecimal hourlyFundingPercent = market.getFundingRateLong();
        if (hourlyFundingPercent != null) {
            quote.addQuote(QuoteType.FUNDING_RATE_APR, hourlyFundingPercent.multiply(BigDecimal.valueOf(24L * 365L)));
            quote.addQuote(QuoteType.FUNDING_RATE_HOURLY_BPS,
                    hourlyFundingPercent.multiply(BigDecimal.valueOf(100L)));
        }
        return quote;
    }

    protected BigDecimal totalOpenInterest(DriftMarket market) {
        if (market.getOpenInterestLong() == null && market.getOpenInterestShort() == null) {
            return null;
        }
        BigDecimal longOi = market.getOpenInterestLong() == null ? BigDecimal.ZERO : market.getOpenInterestLong();
        BigDecimal shortOi = market.getOpenInterestShort() == null ? BigDecimal.ZERO : market.getOpenInterestShort().abs();
        return longOi.max(shortOi);
    }

    protected ZonedDateTime timestamp(long epochMillis) {
        if (epochMillis <= 0) {
            return ZonedDateTime.now(UTC);
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), UTC);
    }

    protected boolean hasLevel1Listeners(Ticker ticker) {
        List<Level1QuoteListener> listeners = level1ListenerMap.get(ticker);
        return listeners != null && !listeners.isEmpty();
    }

    protected boolean hasLevel2Listeners(Ticker ticker) {
        List<Level2QuoteListener> listeners = level2ListenerMap.get(ticker);
        return listeners != null && !listeners.isEmpty();
    }

    protected void validateTickerAndListener(Ticker ticker, Object listener) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
    }
}
