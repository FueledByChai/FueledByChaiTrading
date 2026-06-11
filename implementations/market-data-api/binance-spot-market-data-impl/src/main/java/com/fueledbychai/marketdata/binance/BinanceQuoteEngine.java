package com.fueledbychai.marketdata.binance;

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
import com.fueledbychai.binance.BinanceConfiguration;
import com.fueledbychai.binance.IBinanceRestApi;
import com.fueledbychai.binance.ws.BinanceWebSocketClient;
import com.fueledbychai.binance.ws.BinanceWebSocketClientBuilder;
import com.fueledbychai.binance.ws.aggtrade.AggTradeRecordProcessor;
import com.fueledbychai.binance.ws.aggtrade.TradeRecord;
import com.fueledbychai.binance.ws.bookticker.BookTickerRecord;
import com.fueledbychai.binance.ws.bookticker.BookTickerRecordProcessor;
import com.fueledbychai.binance.ws.RawDiffDepthProcessor;
import com.fueledbychai.binance.ws.partialbook.OrderBookSnapshot;
import com.fueledbychai.binance.ws.partialbook.PartialOrderBookProcessor;
import com.fueledbychai.binance.ws.symbolticker.SymbolTickerRecord;
import com.fueledbychai.binance.ws.symbolticker.SymbolTickerRecordProcessor;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
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

public class BinanceQuoteEngine extends QuoteEngine
        implements com.fueledbychai.marketdata.RawOrderBookSubscribable {

    protected static Logger logger = LoggerFactory.getLogger(BinanceQuoteEngine.class);

    protected volatile boolean started = false;
    protected boolean threadCompleted = false;

    protected int fundingRateUpdateIntervalSeconds = 5;
    protected ArrayList<String> urlStrings = new ArrayList<>();

    protected String wsUrl;
    protected boolean includeFundingRate = true;

    protected ITickerRegistry tickerRegistry;
    protected IBinanceRestApi restApi;

    public BinanceQuoteEngine() {
        this(BinanceConfiguration.getInstance().getWebSocketUrl(),
                TickerRegistryFactory.getInstance(Exchange.BINANCE_SPOT),
                ExchangeRestApiFactory.getPublicApi(Exchange.BINANCE_SPOT, IBinanceRestApi.class));
    }

    protected BinanceQuoteEngine(String wsUrl, ITickerRegistry tickerRegistry, IBinanceRestApi restApi) {
        this.wsUrl = wsUrl;
        logger.info("Binance WebSocket URL: {}", wsUrl);
        this.tickerRegistry = tickerRegistry;
        this.restApi = restApi;
    }

    @Override
    public String getDataProviderName() {
        return "Binance";
    }

    @Override
    public Date getServerTime() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isConnected() {
        return started;
    }

    @Override
    public void startEngine() {
        if (threadCompleted) {
            throw new IllegalStateException("Quote Engine was already stopped");
        }
        started = true;

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
        // stopFundingRateUpdates();
    }

    public ILevel1Quote requestLevel1Snapshot(Ticker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
        String symbol = ticker.getSymbol();
        JsonNode response = restApi.getBookTicker(symbol);
        if (response == null || response.isNull() || response.isMissingNode()) {
            throw new IllegalStateException("No book ticker data returned for " + symbol);
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        Level1Quote quote = new Level1Quote(ticker, now);
        BigDecimal bidPrice = decimalValue(response, "bidPrice");
        BigDecimal bidQty = decimalValue(response, "bidQty");
        BigDecimal askPrice = decimalValue(response, "askPrice");
        BigDecimal askQty = decimalValue(response, "askQty");
        if (bidPrice != null) quote.addQuote(QuoteType.BID, bidPrice);
        if (bidQty != null) quote.addQuote(QuoteType.BID_SIZE, bidQty);
        if (askPrice != null) quote.addQuote(QuoteType.ASK, askPrice);
        if (askQty != null) quote.addQuote(QuoteType.ASK_SIZE, askQty);
        return quote;
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

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.error("useDelayedData() Not supported for binance market data");
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        boolean startStreams = !super.level1ListenerMap.containsKey(ticker);
        super.subscribeLevel1(ticker, listener);
        if (startStreams) {
            startBookTickerWSClient(ticker);
            startSymbolTickerWSClient(ticker);
        }
    }

    @Override
    public void unsubscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        super.unsubscribeLevel1(ticker, listener);
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        boolean startStream = !super.level2ListenerMap.containsKey(ticker) && !super.level1ListenerMap.containsKey(ticker);
        super.subscribeMarketDepth(ticker, listener);
        if (startStream) {
            startPartialOrderBookClient(ticker);
        }
    }

    @Override
    public void unsubscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        super.unsubscribeMarketDepth(ticker, listener);
    }

    // Raw full-depth diff tap for the data recorder (the @depth incremental stream + a REST
    // snapshot anchor) — independent of the algo's @depth5 partial-snapshot path.
    protected final java.util.Map<Ticker, java.util.List<com.fueledbychai.marketdata.RawOrderBookEventListener>>
            rawBookListenerMap = new java.util.concurrent.ConcurrentHashMap<>();
    protected final java.util.Map<Ticker, Boolean> rawDepthStarted = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Register a raw full-depth listener (the recorder) and, on first call per ticker, start the
     * {@code @depth} diff stream and REST snapshot anchor. Independent of {@link #subscribeMarketDepth}
     * (the algo's {@code @depth5} partial book); both can run for the same instrument.
     */
    public void subscribeRawOrderBook(Ticker ticker,
            com.fueledbychai.marketdata.RawOrderBookEventListener listener) {
        java.util.List<com.fueledbychai.marketdata.RawOrderBookEventListener> ls =
                rawBookListenerMap.computeIfAbsent(ticker, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        if (!ls.contains(listener)) {
            ls.add(listener);
        }
        if (rawDepthStarted.putIfAbsent(ticker, Boolean.TRUE) == null) {
            startRawDiffDepthClient(ticker);
        }
    }

    /**
     * (Re)connect the diff-depth stream, then fetch a REST depth snapshot to re-anchor. The stream
     * is connected before the snapshot so no deltas are missed; deltas before the anchor are
     * recorded but flagged out-of-epoch by the downstream sequencer.
     */
    protected void startRawDiffDepthClient(final Ticker ticker) {
        try {
            RawDiffDepthProcessor processor = new RawDiffDepthProcessor(() -> {
                logger.info("Raw diff-depth WebSocket closed for {}, restarting...", ticker.getSymbol());
                startRawDiffDepthClient(ticker);
            });
            processor.addEventListener((JsonNode msg) -> {
                try {
                    onRawDiffDepth(ticker, msg);
                } catch (Exception e) {
                    logger.error("Error processing raw diff-depth update", e);
                }
            });
            BinanceWebSocketClient client = BinanceWebSocketClientBuilder.buildDiffDepth(wsUrl, ticker, processor);
            client.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try {
            JsonNode snap = restApi.getDepthSnapshot(ticker.getSymbol(), 1000);
            fireRawAnchor(ticker, snap);
        } catch (RuntimeException e) {
            logger.warn("Failed to fetch raw depth snapshot anchor for {}: {}", ticker.getSymbol(), e.getMessage());
        }
    }

    /** Fire a full-book snapshot anchor (from the REST {@code /depth} response) to raw listeners. */
    protected void fireRawAnchor(Ticker ticker, JsonNode snapshot) {
        if (snapshot == null) {
            return;
        }
        long lastUpdateId = snapshot.path("lastUpdateId").asLong(com.fueledbychai.marketdata.RawBookUpdate.NO_SEQUENCE);
        java.util.List<com.fueledbychai.marketdata.RawBookUpdate.Entry> entries = new java.util.ArrayList<>();
        addRawEntries(entries, snapshot.path("bids"), com.fueledbychai.marketdata.RawBookUpdate.Side.BUY, true);
        addRawEntries(entries, snapshot.path("asks"), com.fueledbychai.marketdata.RawBookUpdate.Side.SELL, true);
        fireRaw(ticker, new com.fueledbychai.marketdata.RawBookUpdate(true, lastUpdateId,
                com.fueledbychai.marketdata.RawBookUpdate.NO_SEQUENCE, ZonedDateTime.now(ZoneId.of("UTC")),
                entries, null));
    }

    /**
     * Map a spot {@code @depth} diff frame to a sequenced {@link com.fueledbychai.marketdata.RawBookUpdate}.
     * Spot continuity is {@code U == last u + 1}, so the prev-id is {@code U - 1}; qty 0 = remove.
     */
    protected void onRawDiffDepth(Ticker ticker, JsonNode message) {
        java.util.List<com.fueledbychai.marketdata.RawOrderBookEventListener> listeners = rawBookListenerMap.get(ticker);
        if (listeners == null || listeners.isEmpty() || message == null) {
            return;
        }
        long u = message.path("u").asLong(com.fueledbychai.marketdata.RawBookUpdate.NO_SEQUENCE);
        long firstU = message.path("U").asLong(com.fueledbychai.marketdata.RawBookUpdate.NO_SEQUENCE);
        long prevSeq = firstU >= 0 ? firstU - 1 : com.fueledbychai.marketdata.RawBookUpdate.NO_SEQUENCE;
        java.util.List<com.fueledbychai.marketdata.RawBookUpdate.Entry> entries = new java.util.ArrayList<>();
        addRawEntries(entries, message.path("b"), com.fueledbychai.marketdata.RawBookUpdate.Side.BUY, false);
        addRawEntries(entries, message.path("a"), com.fueledbychai.marketdata.RawBookUpdate.Side.SELL, false);
        fireRaw(ticker, new com.fueledbychai.marketdata.RawBookUpdate(false, u, prevSeq,
                toTimestamp(message.path("E").asLong(0L)), entries, null));
    }

    private void fireRaw(Ticker ticker, com.fueledbychai.marketdata.RawBookUpdate update) {
        java.util.List<com.fueledbychai.marketdata.RawOrderBookEventListener> listeners = rawBookListenerMap.get(ticker);
        if (listeners == null) {
            return;
        }
        for (com.fueledbychai.marketdata.RawOrderBookEventListener listener : listeners) {
            try {
                listener.onBookUpdate(ticker, update);
            } catch (RuntimeException e) {
                logger.warn("Raw book listener failed for {}: {}", ticker.getSymbol(), e.getMessage());
            }
        }
    }

    /** Map Binance {@code [price, qty]} level arrays to raw entries; qty 0 = delete. */
    private static void addRawEntries(java.util.List<com.fueledbychai.marketdata.RawBookUpdate.Entry> out,
            JsonNode levels, com.fueledbychai.marketdata.RawBookUpdate.Side side, boolean isSnapshot) {
        if (levels == null || !levels.isArray()) {
            return;
        }
        for (JsonNode lvl : levels) {
            if (lvl == null || !lvl.isArray() || lvl.size() < 2) {
                continue;
            }
            String priceStr = lvl.get(0).asText("");
            String qtyStr = lvl.get(1).asText("");
            if (priceStr.isBlank() || qtyStr.isBlank()) {
                continue;
            }
            double qty = Double.parseDouble(qtyStr);
            com.fueledbychai.marketdata.RawBookUpdate.Action action;
            if (isSnapshot) {
                action = com.fueledbychai.marketdata.RawBookUpdate.Action.INSERT;
            } else if (qty <= 0.0) {
                action = com.fueledbychai.marketdata.RawBookUpdate.Action.DELETE;
            } else {
                action = com.fueledbychai.marketdata.RawBookUpdate.Action.UPDATE;
            }
            out.add(new com.fueledbychai.marketdata.RawBookUpdate.Entry(side, action,
                    new java.math.BigDecimal(priceStr), qty));
        }
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        boolean startStream = !super.orderFlowListenerMap.containsKey(ticker);
        super.subscribeOrderFlow(ticker, listener);
        if (startStream) {
            // No order flow client yet for this ticker, so create one.
            startTradesWSClient(ticker);
        }
    }

    @Override
    public void unsubscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        super.unsubscribeOrderFlow(ticker, listener);
    }

    public void onBBOUpdate(Ticker ticker, BigDecimal bestBid, BigDecimal bidSize, BigDecimal bestAsk,
            BigDecimal askSize, ZonedDateTime timeStamp) {
        Level1Quote quote = new Level1Quote(ticker, timeStamp);
        if (bestBid != null) {
            quote.addQuote(QuoteType.BID, bestBid);
            quote.addQuote(QuoteType.BID_SIZE, bidSize);
        }
        if (bestAsk != null) {
            quote.addQuote(QuoteType.ASK, bestAsk);
            quote.addQuote(QuoteType.ASK_SIZE, askSize);
        }
        fireLevel1Quote(quote);
    }

    public void onOrderBookUpdate(Ticker ticker, OrderBookSnapshot orderBookSnapshot, ZonedDateTime timeStamp) {
        OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());

        orderBook.updateFromSnapshot(convertPriceLevels(orderBookSnapshot.getBids()),
                convertPriceLevels(orderBookSnapshot.getAsks()), timeStamp);

        Level2Quote quote = new Level2Quote(ticker, orderBook, timeStamp);
        fireMarketDepthQuote(quote);
    }

    public void onTradeRecordUpdate(Ticker ticker, TradeRecord tradeRecord) {
        // Convert TradeRecord to OrderFlow and fire event
        ZonedDateTime eventTime = Instant.ofEpochMilli(tradeRecord.getTradeTime()).atZone(ZoneId.of("UTC"));
        OrderFlow orderFlow = new OrderFlow(ticker, new BigDecimal(tradeRecord.getPrice()),
                new BigDecimal(tradeRecord.getQuantity()),
                tradeRecord.isBuyerMarketMaker() ? OrderFlow.Side.SELL : OrderFlow.Side.BUY, eventTime);

        fireOrderFlow(orderFlow);
    }

    public void onBookTickerUpdate(Ticker ticker, BookTickerRecord record) {
        if (record == null) {
            return;
        }
        ZonedDateTime eventTime = toTimestamp(record.getEventTime());
        BigDecimal bestBid = parsePrice(record.getBestBidPrice());
        BigDecimal bestAsk = parsePrice(record.getBestAskPrice());
        BigDecimal bidSize = parseQuantity(record.getBestBidQty());
        BigDecimal askSize = parseQuantity(record.getBestAskQty());

        onBBOUpdate(ticker, bestBid, bidSize, bestAsk, askSize, eventTime);
    }

    public void onSymbolTickerUpdate(Ticker ticker, SymbolTickerRecord record) {
        if (record == null) {
            return;
        }

        Level1Quote quote = new Level1Quote(ticker, toTimestamp(record.getEventTime()));
        boolean hasValues = false;

        BigDecimal lastPrice = parsePrice(record.getLastPrice());
        if (lastPrice != null) {
            quote.addQuote(QuoteType.LAST, lastPrice);
            hasValues = true;
        }

        BigDecimal lastSize = parseQuantity(record.getLastQuantity());
        if (lastSize != null) {
            quote.addQuote(QuoteType.LAST_SIZE, lastSize);
            hasValues = true;
        }

        BigDecimal volume = parseQuantity(record.getVolume());
        if (volume != null) {
            quote.addQuote(QuoteType.VOLUME, volume);
            hasValues = true;
        }

        BigDecimal volumeNotional = parseQuantity(record.getVolumeNotional());
        if (volumeNotional != null) {
            quote.addQuote(QuoteType.VOLUME_NOTIONAL, volumeNotional);
            hasValues = true;
        }

        if (hasValues) {
            fireLevel1Quote(quote);
        }
    }

    protected List<OrderBook.PriceLevel> convertPriceLevels(
            List<com.fueledbychai.binance.ws.partialbook.PriceLevel> binanceLevels) {
        List<OrderBook.PriceLevel> priceLevels = new ArrayList<>();
        for (com.fueledbychai.binance.ws.partialbook.PriceLevel bl : binanceLevels) {
            try {
                OrderBook.PriceLevel pl = new OrderBook.PriceLevel(new BigDecimal(bl.getPrice()),
                        Double.valueOf(bl.getQuantity()));
                priceLevels.add(pl);
            } catch (Exception e) {
                logger.error("Error converting price level: " + bl, e);
            }
        }
        return priceLevels;
    }

    protected void startPartialOrderBookClient(final Ticker ticker) {
        try {
            logger.info("Starting Partial Order Book WebSocket client");
            PartialOrderBookProcessor processor = new PartialOrderBookProcessor(() -> {
                logger.info("Partial Order Book WebSocket closed, trying to restart...");
                startPartialOrderBookClient(ticker);
            });
            processor.addEventListener((OrderBookSnapshot obs) -> {
                ZonedDateTime eventTime = resolveSnapshotEventTime(obs);
                try {
                    onOrderBookUpdate(ticker, obs, eventTime);
                } catch (Exception e) {
                    logger.error("Error processing Order Book update", e);
                }
            });

            BinanceWebSocketClient partialBookDepthClient = BinanceWebSocketClientBuilder.buildPartialBookDepth(wsUrl,
                    ticker, processor);
            partialBookDepthClient.connect();

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

    protected ZonedDateTime resolveSnapshotEventTime(OrderBookSnapshot snapshot) {
        if (snapshot != null && snapshot.getEventTime() != null) {
            return Instant.ofEpochMilli(snapshot.getEventTime()).atZone(ZoneId.of("UTC"));
        }
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    protected ZonedDateTime toTimestamp(long epochMillis) {
        if (epochMillis > 0L) {
            return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of("UTC"));
        }
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    protected BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    protected BigDecimal parseQuantity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    protected void startBookTickerWSClient(final Ticker ticker) {
        try {
            logger.info("Starting Book Ticker WebSocket client");
            BookTickerRecordProcessor processor = new BookTickerRecordProcessor(() -> {
                logger.info("Book Ticker WebSocket closed, trying to restart...");
                startBookTickerWSClient(ticker);
            });
            processor.addEventListener((BookTickerRecord obs) -> {
                try {
                    onBookTickerUpdate(ticker, obs);
                } catch (Exception e) {
                    logger.error("Error processing book ticker update", e);
                }
            });

            BinanceWebSocketClient bookTickerClient = BinanceWebSocketClientBuilder.buildBookTickerClient(wsUrl,
                    ticker, processor);
            bookTickerClient.connect();

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected void startSymbolTickerWSClient(final Ticker ticker) {
        try {
            logger.info("Starting Symbol Ticker WebSocket client");
            SymbolTickerRecordProcessor processor = new SymbolTickerRecordProcessor(() -> {
                logger.info("Symbol Ticker WebSocket closed, trying to restart...");
                startSymbolTickerWSClient(ticker);
            });
            processor.addEventListener((SymbolTickerRecord obs) -> {
                try {
                    onSymbolTickerUpdate(ticker, obs);
                } catch (Exception e) {
                    logger.error("Error processing symbol ticker update", e);
                }
            });

            BinanceWebSocketClient symbolTickerClient = BinanceWebSocketClientBuilder.buildSymbolTickerClient(wsUrl,
                    ticker, processor);
            symbolTickerClient.connect();

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected void startTradesWSClient(final Ticker ticker) {
        try {
            logger.info("Starting Trades WebSocket client");
            AggTradeRecordProcessor processor = new AggTradeRecordProcessor(() -> {
                logger.info("Trades WebSocket closed, trying to restart...");
                startTradesWSClient(ticker);
            });
            processor.addEventListener((TradeRecord obs) -> {

                try {
                    onTradeRecordUpdate(ticker, obs);
                } catch (Exception e) {
                    logger.error("Error processing trade record update", e);
                }
            });

            BinanceWebSocketClient tradesClient = BinanceWebSocketClientBuilder.buildTradesClient(wsUrl, ticker,
                    processor);
            tradesClient.connect();

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
