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

    protected final IHibachiRestApi restApi;
    protected final ITickerRegistry tickerRegistry;
    protected final HibachiConfiguration config;
    protected final Set<SubKey> activeSubscriptions = ConcurrentHashMap.newKeySet();

    protected volatile HibachiWebSocketClient marketClient;
    protected volatile HibachiJsonProcessor marketProcessor;
    protected volatile boolean started = false;

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
            marketClient.connect();
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
                marketClient.send(msg);
            }
        } catch (Exception e) {
            logger.warn("Failed to send subscribe for {} {}", ticker.getSymbol(), topic, e);
        }
    }

    protected void onMarketWsClosed() {
        if (!started) {
            return;
        }
        logger.warn("Hibachi market WS closed; will not auto-reconnect in this engine version");
    }

    protected void onMarketMessage(JsonNode message) {
        if (message == null) {
            return;
        }
        String topic = message.path("topic").asText("");
        String symbol = message.path("symbol").asText("");
        if (topic.isEmpty()) {
            return;
        }
        Ticker ticker = lookupTicker(symbol);
        if (ticker == null) {
            return;
        }
        try {
            switch (topic) {
                case HibachiTopicRouter.TOPIC_ASK_BID_PRICE -> onAskBidUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_MARK_PRICE -> onMarkPriceUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_ORDERBOOK -> onOrderBookUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_TRADES -> onTradesUpdate(ticker, message);
                default -> { /* unhandled topic */ }
            }
        } catch (Exception e) {
            logger.warn("Failed to dispatch Hibachi market message for {} {}", symbol, topic, e);
        }
    }

    protected void onAskBidUpdate(Ticker ticker, JsonNode message) {
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "timestamp"));
        boolean any = false;
        BigDecimal bid = decimal(message, "bidPrice");
        if (bid != null) { quote.addQuote(QuoteType.BID, bid); any = true; }
        BigDecimal bidSize = decimal(message, "bidSize");
        if (bidSize != null) { quote.addQuote(QuoteType.BID_SIZE, bidSize); any = true; }
        BigDecimal ask = decimal(message, "askPrice");
        if (ask != null) { quote.addQuote(QuoteType.ASK, ask); any = true; }
        BigDecimal askSize = decimal(message, "askSize");
        if (askSize != null) { quote.addQuote(QuoteType.ASK_SIZE, askSize); any = true; }
        if (any) {
            fireLevel1Quote(quote);
        }
    }

    protected void onMarkPriceUpdate(Ticker ticker, JsonNode message) {
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "timestamp"));
        boolean any = false;
        BigDecimal mark = firstDecimal(message, "markPrice", "price");
        if (mark != null) { quote.addQuote(QuoteType.MARK_PRICE, mark); any = true; }
        if (any) {
            fireLevel1Quote(quote);
        }
    }

    protected void onOrderBookUpdate(Ticker ticker, JsonNode message) {
        List<OrderBook.PriceLevel> bids = parseLevels(message.path("bids"));
        List<OrderBook.PriceLevel> asks = parseLevels(message.path("asks"));
        if (bids.isEmpty() && asks.isEmpty()) {
            return;
        }
        OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());
        ZonedDateTime timestamp = toTimestamp(message, "timestamp");
        orderBook.updateFromSnapshot(bids, asks, timestamp);
        fireMarketDepthQuote(new Level2Quote(ticker, orderBook, timestamp));
    }

    protected void onTradesUpdate(Ticker ticker, JsonNode message) {
        JsonNode trades = message.path("trades");
        if (!trades.isArray()) {
            // Single-trade payload?
            emitTrade(ticker, message);
            return;
        }
        for (JsonNode trade : trades) {
            emitTrade(ticker, trade);
        }
    }

    protected void emitTrade(Ticker ticker, JsonNode trade) {
        BigDecimal price = decimal(trade, "price");
        BigDecimal size = firstDecimal(trade, "quantity", "size");
        if (price == null || size == null) {
            return;
        }
        String sideStr = trade.path("side").asText("BID").toUpperCase();
        OrderFlow.Side side = "ASK".equals(sideStr) || "SELL".equals(sideStr)
                ? OrderFlow.Side.SELL : OrderFlow.Side.BUY;
        OrderFlow flow = new OrderFlow(ticker, price, size, side, toTimestamp(trade, "timestamp"));
        fireOrderFlow(flow);
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
            if (!lvl.isArray() || lvl.size() < 2) continue;
            try {
                BigDecimal price = new BigDecimal(lvl.get(0).asText());
                double size = Double.parseDouble(lvl.get(1).asText());
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
