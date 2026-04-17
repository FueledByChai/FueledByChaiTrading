package com.fueledbychai.marketdata.hibachi;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.hibachi.common.api.HibachiConfiguration;
import com.fueledbychai.hibachi.common.api.IHibachiRestApi;
import com.fueledbychai.hibachi.common.api.ws.HibachiJsonProcessor;
import com.fueledbychai.hibachi.common.api.ws.HibachiMarketSubscribeMessage;
import com.fueledbychai.hibachi.common.api.ws.HibachiTopicRouter;
import com.fueledbychai.hibachi.common.api.ws.HibachiWebSocketClient;
import com.fueledbychai.marketdata.ILevel1Quote;
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
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

/**
 * Hibachi market-data quote engine.
 *
 * <p>Maintains a single shared market WebSocket connection (Hibachi multiplexes topics on
 * one connection). Subscriptions map to:
 * <ul>
 *   <li><b>L1</b> = {@code ask_bid_price} + {@code mark_price}</li>
 *   <li><b>L2</b> = {@code orderbook}</li>
 *   <li><b>OrderFlow</b> = {@code trades}</li>
 * </ul>
 */
public class HibachiQuoteEngine extends QuoteEngine {

    private static final Logger logger = LoggerFactory.getLogger(HibachiQuoteEngine.class);
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final java.math.MathContext MC = java.math.MathContext.DECIMAL64;
    private static final BigDecimal HOURS_PER_YEAR = BigDecimal.valueOf(24L * 365L);
    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);
    private static final BigDecimal BPS_MULTIPLIER = BigDecimal.valueOf(10000);

    protected final IHibachiRestApi restApi;
    protected final ITickerRegistry tickerRegistry;
    protected final HibachiConfiguration config;
    protected final Set<SubKey> activeSubscriptions = ConcurrentHashMap.newKeySet();

    protected volatile HibachiWebSocketClient marketClient;
    protected volatile HibachiJsonProcessor marketProcessor;
    protected volatile boolean started = false;

    protected final Set<String> volumePollingSymbols = ConcurrentHashMap.newKeySet();
    protected volatile ScheduledExecutorService volumeScheduler;
    protected volatile ScheduledFuture<?> volumeTask;

    public HibachiQuoteEngine() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.HIBACHI, IHibachiRestApi.class),
                TickerRegistryFactory.getInstance(Exchange.HIBACHI),
                HibachiConfiguration.getInstance());
        logger.info("Hibachi market WS URL: {}", config.getMarketWsUrl());
    }

    protected HibachiQuoteEngine(IHibachiRestApi restApi, ITickerRegistry tickerRegistry,
                                 HibachiConfiguration config) {
        if (restApi == null) throw new IllegalArgumentException("restApi is required");
        if (tickerRegistry == null) throw new IllegalArgumentException("tickerRegistry is required");
        if (config == null) throw new IllegalArgumentException("config is required");
        this.restApi = restApi;
        this.tickerRegistry = tickerRegistry;
        this.config = config;
    }

    @Override
    public String getDataProviderName() {
        return "Hibachi";
    }

    @Override
    public Date getServerTime() {
        return restApi.getServerTime();
    }

    @Override
    public boolean isConnected() {
        return started && marketClient != null && marketClient.isOpen();
    }

    @Override
    public void startEngine() {
        if (started) {
            return;
        }
        started = true;
        ensureMarketClient();
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
        activeSubscriptions.clear();
        volumePollingSymbols.clear();
        if (volumeTask != null) {
            volumeTask.cancel(false);
            volumeTask = null;
        }
        if (volumeScheduler != null) {
            volumeScheduler.shutdownNow();
            volumeScheduler = null;
        }
        HibachiWebSocketClient client = marketClient;
        marketClient = null;
        HibachiJsonProcessor processor = marketProcessor;
        marketProcessor = null;
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
        if (processor != null) {
            processor.shutdown();
        }
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        requireTicker(ticker);
        super.subscribeLevel1(ticker, listener);
        ensureMarketClient();
        for (String topic : HibachiTopicRouter.LEVEL1_TOPICS) {
            subscribeTopic(ticker, topic);
        }
        startVolumePolling(ticker);
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        requireTicker(ticker);
        super.subscribeMarketDepth(ticker, listener);
        ensureMarketClient();
        subscribeTopic(ticker, HibachiTopicRouter.LEVEL2_TOPIC);
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        requireTicker(ticker);
        super.subscribeOrderFlow(ticker, listener);
        ensureMarketClient();
        subscribeTopic(ticker, HibachiTopicRouter.ORDER_FLOW_TOPIC);
    }

    @Override
    public ILevel1Quote requestLevel1Snapshot(Ticker ticker) {
        requireTicker(ticker);
        JsonNode response = restApi.getOrderBookSnapshot(ticker.getSymbol());
        if (response == null || response.isMissingNode()) {
            throw new IllegalStateException("No order-book snapshot for " + ticker.getSymbol());
        }
        Level1Quote quote = new Level1Quote(ticker, ZonedDateTime.now(UTC));
        BigDecimal bid = topPrice(response, "bids");
        BigDecimal ask = topPrice(response, "asks");
        if (bid != null) quote.addQuote(QuoteType.BID, bid);
        if (ask != null) quote.addQuote(QuoteType.ASK, ask);
        return quote;
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.debug("useDelayedData() not supported for Hibachi");
    }

    // ---------- WS plumbing ----------

    protected synchronized void ensureMarketClient() {
        if (marketClient != null && marketClient.isOpen()) {
            return;
        }
        try {
            marketProcessor = new HibachiJsonProcessor(this::onMarketWsClosed);
            marketProcessor.addEventListener(this::onMarketMessage);
            marketClient = HibachiWebSocketClient.createMarket(
                    config.getMarketWsUrl(), marketProcessor, config.getClient(), null);
            if (!marketClient.connectBlocking(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out connecting to Hibachi market WS at "
                        + config.getMarketWsUrl());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted connecting to Hibachi market WS", ie);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Hibachi market WS client", e);
        }
    }

    protected synchronized void subscribeTopic(Ticker ticker, String topic) {
        SubKey key = new SubKey(ticker.getSymbol(), topic);
        if (!activeSubscriptions.add(key)) {
            return;
        }
        try {
            String msg = HibachiMarketSubscribeMessage.subscribe(ticker.getSymbol(), topic);
            if (marketClient != null && marketClient.isOpen()) {
                logger.info("Hibachi WS send: {}", msg);
                marketClient.send(msg);
            } else {
                logger.warn("Hibachi WS not open; dropping subscribe for {} {}", ticker.getSymbol(), topic);
            }
        } catch (Exception e) {
            logger.warn("Failed to send subscribe for {} {}", ticker.getSymbol(), topic, e);
        }
    }

    protected void onMarketWsClosed() {
        if (!started) {
            return;
        }
        logger.warn("Hibachi market WS closed; auto-reconnect is disabled in this engine version because "
                + "the engine does not rebuild the websocket/subscription state after disconnect. "
                + "Manual recovery is required before market data will resume.");
    }

    protected void onMarketMessage(JsonNode message) {
        if (message == null) {
            return;
        }
        String topic = message.path("topic").asText("");
        String symbol = message.path("symbol").asText("");
        if (topic.isEmpty()) {
            logger.info("Hibachi WS recv (no topic): {}", message);
            return;
        }
        logger.info("Hibachi WS dispatch topic={} symbol={}", topic, symbol);
        Ticker ticker = lookupTicker(symbol);
        if (ticker == null) {
            logger.warn("Hibachi WS no ticker for symbol={} topic={}", symbol, topic);
            return;
        }
        try {
            switch (topic) {
                case HibachiTopicRouter.TOPIC_ASK_BID_PRICE -> onAskBidUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_MARK_PRICE -> onMarkPriceUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_ORDERBOOK -> onOrderBookUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_TRADES -> onTradesUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_FUNDING_RATE_ESTIMATION -> onFundingRateUpdate(ticker, message);
                default -> { /* unhandled topic */ }
            }
        } catch (Exception e) {
            logger.warn("Failed to dispatch Hibachi market message for {} {}", symbol, topic, e);
        }
    }

    protected void onAskBidUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "timestamp_ms"));
        boolean any = false;
        BigDecimal bid = decimal(data, "bidPrice");
        if (bid != null) { quote.addQuote(QuoteType.BID, bid); any = true; }
        BigDecimal bidSize = decimal(data, "bidSize");
        if (bidSize != null) { quote.addQuote(QuoteType.BID_SIZE, bidSize); any = true; }
        BigDecimal ask = decimal(data, "askPrice");
        if (ask != null) { quote.addQuote(QuoteType.ASK, ask); any = true; }
        BigDecimal askSize = decimal(data, "askSize");
        if (askSize != null) { quote.addQuote(QuoteType.ASK_SIZE, askSize); any = true; }
        if (any) {
            fireLevel1Quote(quote);
        }
    }

    protected void onMarkPriceUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "timestamp_ms"));
        boolean any = false;
        BigDecimal mark = firstDecimal(data, "markPrice", "price");
        if (mark != null) { quote.addQuote(QuoteType.MARK_PRICE, mark); any = true; }
        if (any) {
            fireLevel1Quote(quote);
        }
    }

    protected void onOrderBookUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        List<OrderBook.PriceLevel> bids = parseLevels(data.path("bid").path("levels"));
        List<OrderBook.PriceLevel> asks = parseLevels(data.path("ask").path("levels"));
        if (bids.isEmpty() && asks.isEmpty()) {
            return;
        }
        OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());
        ZonedDateTime timestamp = toTimestamp(message, "timestamp_ms");
        orderBook.updateFromSnapshot(bids, asks, timestamp);
        fireMarketDepthQuote(new Level2Quote(ticker, orderBook, timestamp));
    }

    protected void onTradesUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        // Hibachi may wrap a single trade as {"trade":{...}} or batch as {"trades":[...]}
        JsonNode singleTrade = data.path("trade");
        if (singleTrade.isObject()) {
            emitTrade(ticker, singleTrade);
            return;
        }
        JsonNode trades = data.path("trades");
        if (trades.isArray()) {
            for (JsonNode trade : trades) {
                emitTrade(ticker, trade);
            }
            return;
        }
        emitTrade(ticker, data);
    }

    protected void emitTrade(Ticker ticker, JsonNode trade) {
        BigDecimal price = decimal(trade, "price");
        BigDecimal size = firstDecimal(trade, "quantity", "size");
        if (price == null || size == null) {
            logger.info("Hibachi trade missing fields: price={} size={} raw={}", price, size, trade);
            return;
        }
        String sideStr = firstString(trade, "takerSide", "side").toUpperCase();
        OrderFlow.Side side = "ASK".equals(sideStr) || "SELL".equals(sideStr)
                ? OrderFlow.Side.SELL : OrderFlow.Side.BUY;
        ZonedDateTime ts = toTimestamp(trade, "timestamp_ms");
        OrderFlow flow = new OrderFlow(ticker, price, size, side, ts);
        fireOrderFlow(flow);

        Level1Quote lastQuote = new Level1Quote(ticker, ts);
        lastQuote.addQuote(QuoteType.LAST, price);
        lastQuote.addQuote(QuoteType.LAST_SIZE, size);
        fireLevel1Quote(lastQuote);
    }

    protected void onFundingRateUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        BigDecimal rate = decimal(data, "estimatedFundingRate");
        if (rate == null) {
            rate = decimal(data.path("fundingRateEstimation"), "estimatedFundingRate");
        }
        if (rate == null) {
            logger.info("Hibachi funding rate missing estimatedFundingRate: {}", data);
            return;
        }
        int fundingInterval = ticker.getFundingRateInterval();
        if (fundingInterval <= 0) {
            fundingInterval = 8;
        }
        BigDecimal hourlyRate = rate.divide(BigDecimal.valueOf(fundingInterval), MC);
        BigDecimal annualizedPercent = hourlyRate.multiply(HOURS_PER_YEAR).multiply(PERCENT_MULTIPLIER, MC);
        BigDecimal hourlyBps = hourlyRate.multiply(BPS_MULTIPLIER, MC);
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "timestamp_ms"));
        quote.addQuote(QuoteType.FUNDING_RATE_APR, annualizedPercent);
        quote.addQuote(QuoteType.FUNDING_RATE_HOURLY_BPS, hourlyBps);
        fireLevel1Quote(quote);
    }

    protected synchronized void startVolumePolling(Ticker ticker) {
        String symbol = ticker.getSymbol();
        if (!volumePollingSymbols.add(symbol)) {
            return;
        }
        if (volumeScheduler == null) {
            volumeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "hibachi-volume-poll");
                t.setDaemon(true);
                return t;
            });
            volumeTask = volumeScheduler.scheduleAtFixedRate(
                    this::pollVolume, 0, 1, TimeUnit.SECONDS);
        }
    }

    protected void pollVolume() {
        for (String symbol : volumePollingSymbols) {
            try {
                JsonNode stats = restApi.getMarketStats(symbol);
                if (stats == null || stats.isMissingNode()) {
                    continue;
                }
                Ticker ticker = lookupTicker(symbol);
                if (ticker == null) {
                    continue;
                }
                Level1Quote quote = new Level1Quote(ticker, ZonedDateTime.now(UTC));
                boolean any = false;
                BigDecimal volume = decimal(stats, "volume24h");
                if (volume != null) { quote.addQuote(QuoteType.VOLUME, volume); any = true; }
                BigDecimal volumeNotional = decimal(stats, "volumeNotional24h");
                if (volumeNotional != null) { quote.addQuote(QuoteType.VOLUME_NOTIONAL, volumeNotional); any = true; }
                if (any) {
                    fireLevel1Quote(quote);
                }
            } catch (Exception e) {
                logger.debug("Failed to poll volume for {}", symbol, e);
            }
        }
    }

    protected Ticker lookupTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        Ticker t = tickerRegistry.lookupByBrokerSymbol(com.fueledbychai.data.InstrumentType.PERPETUAL_FUTURES, symbol);
        if (t == null) {
            t = tickerRegistry.lookupByCommonSymbol(com.fueledbychai.data.InstrumentType.PERPETUAL_FUTURES, symbol);
        }
        return t;
    }

    protected BigDecimal topPrice(JsonNode root, String side) {
        JsonNode levels = root.path(side);
        if (!levels.isArray() || levels.size() == 0) {
            return null;
        }
        JsonNode first = levels.get(0);
        if (!first.isArray() || first.size() == 0) {
            return null;
        }
        try {
            return new BigDecimal(first.get(0).asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected List<OrderBook.PriceLevel> parseLevels(JsonNode arr) {
        List<OrderBook.PriceLevel> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode lvl : arr) {
            try {
                BigDecimal price;
                double size;
                if (lvl.isArray() && lvl.size() >= 2) {
                    price = new BigDecimal(lvl.get(0).asText());
                    size = Double.parseDouble(lvl.get(1).asText());
                } else if (lvl.isObject()) {
                    price = new BigDecimal(lvl.path("price").asText());
                    size = Double.parseDouble(lvl.path("quantity").asText());
                } else {
                    continue;
                }
                out.add(new OrderBook.PriceLevel(price, size));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    protected ZonedDateTime toTimestamp(JsonNode message, String field) {
        if (message == null) {
            return ZonedDateTime.now(UTC);
        }
        long epoch = message.path(field).asLong(0L);
        if (epoch <= 0L) {
            return ZonedDateTime.now(UTC);
        }
        // Heuristic: values < 1e12 are seconds, else millis.
        long millis = epoch < 1_000_000_000_000L ? epoch * 1000L : epoch;
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), UTC);
    }

    protected BigDecimal decimal(JsonNode message, String field) {
        if (message == null) return null;
        String raw = message.path(field).asText("");
        if (raw.isBlank()) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected BigDecimal firstDecimal(JsonNode message, String... fields) {
        for (String f : fields) {
            BigDecimal v = decimal(message, f);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    protected String firstString(JsonNode message, String... fields) {
        if (message == null) return "";
        for (String f : fields) {
            String v = message.path(f).asText("");
            if (!v.isBlank()) return v;
        }
        return "";
    }

    protected void requireTicker(Ticker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
    }

    private static final class SubKey {
        final String symbol;
        final String topic;

        SubKey(String symbol, String topic) {
            this.symbol = symbol;
            this.topic = topic;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SubKey)) return false;
            SubKey k = (SubKey) o;
            return symbol.equals(k.symbol) && topic.equals(k.topic);
        }

        @Override
        public int hashCode() {
            return 31 * symbol.hashCode() + topic.hashCode();
        }
    }

    // unused but kept for spi clarity
    @SuppressWarnings("unused")
    private static final Set<String> SUPPORTED_TOPICS = Set.copyOf(new HashSet<>(List.of(
            HibachiTopicRouter.TOPIC_ASK_BID_PRICE,
            HibachiTopicRouter.TOPIC_MARK_PRICE,
            HibachiTopicRouter.TOPIC_ORDERBOOK,
            HibachiTopicRouter.TOPIC_TRADES)));
}
