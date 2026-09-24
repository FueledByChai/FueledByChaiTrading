package com.fueledbychai.marketdata.qfex;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.Level1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.qfex.common.api.IQfexRestApi;
import com.fueledbychai.qfex.common.api.IQfexWebSocketApi;
import com.fueledbychai.qfex.common.api.ws.QfexMarketDataStream;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

/**
 * QFEX market data. Level 1 combines the {@code bbo}, {@code mark_price} and
 * {@code trade} channels; order flow comes from {@code trade}. Prices and
 * sizes arrive as strings.
 */
public class QfexQuoteEngine extends QuoteEngine {

    private static final Logger logger = LoggerFactory.getLogger(QfexQuoteEngine.class);

    protected final IQfexRestApi restApi;
    protected final QfexMarketDataStream stream;
    protected final IQfexWebSocketApi webSocketApi;
    /** Venue symbol -> subscribed ticker. */
    protected final Map<String, Ticker> tickers = new ConcurrentHashMap<>();
    protected volatile boolean started;

    public QfexQuoteEngine() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.QFEX, IQfexRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.QFEX, IQfexWebSocketApi.class));
    }

    protected QfexQuoteEngine(IQfexRestApi restApi, IQfexWebSocketApi webSocketApi) {
        if (restApi == null || webSocketApi == null) {
            throw new IllegalArgumentException("restApi and webSocketApi are required");
        }
        this.restApi = restApi;
        this.webSocketApi = webSocketApi;
        this.stream = webSocketApi.marketData();
        this.stream.addListener(this::onMessage);
    }

    @Override
    public String getDataProviderName() {
        return "QFEX";
    }

    @Override
    public Date getServerTime() {
        return new Date();
    }

    @Override
    public boolean isConnected() {
        return started && stream.isOpen();
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
        tickers.clear();
        stream.clearSubscriptions();
        clearSessionListeners();
        webSocketApi.disconnectAll();
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        requireTicker(ticker);
        super.subscribeLevel1(ticker, listener);
        tickers.put(ticker.getSymbol(), ticker);
        stream.subscribe(ticker.getSymbol(), "bbo", "mark_price", "trade");
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        requireTicker(ticker);
        super.subscribeOrderFlow(ticker, listener);
        tickers.put(ticker.getSymbol(), ticker);
        stream.subscribe(ticker.getSymbol(), "trade");
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        throw new UnsupportedOperationException("QFEX market depth is not implemented yet");
    }

    @Override
    public ILevel1Quote requestLevel1Snapshot(Ticker ticker) {
        requireTicker(ticker);
        JsonNode book = restApi.getOrderBook(ticker.getSymbol());
        Level1Quote quote = new Level1Quote(ticker, ZonedDateTime.now(ZoneOffset.UTC));
        addTopOfBook(quote, book);
        return quote;
    }

    /** Routes one market data message; package-visible for tests. */
    void onMessage(JsonNode msg) {
        String type = msg.path("type").asText("");
        Ticker ticker = tickers.get(msg.path("symbol").asText(""));
        if (ticker == null) {
            return;
        }
        ZonedDateTime time = parseTime(msg.path("time").asText(null));
        switch (type) {
            case "bbo" -> {
                Level1Quote quote = new Level1Quote(ticker, time);
                if (addTopOfBook(quote, msg)) {
                    fireLevel1Quote(quote);
                }
            }
            case "mark_price" -> {
                BigDecimal px = decimal(msg.path("price"));
                if (px != null) {
                    Level1Quote quote = new Level1Quote(ticker, time);
                    quote.addQuote(QuoteType.MARK_PRICE, px);
                    fireLevel1Quote(quote);
                }
            }
            case "trade" -> {
                BigDecimal px = decimal(msg.path("price"));
                BigDecimal size = decimal(msg.path("size"));
                if (px == null) {
                    return;
                }
                Level1Quote quote = new Level1Quote(ticker, time);
                quote.addQuote(QuoteType.LAST, px);
                if (size != null) {
                    quote.addQuote(QuoteType.LAST_SIZE, size);
                }
                fireLevel1Quote(quote);
                OrderFlow.Side side = "SELL".equalsIgnoreCase(msg.path("side").asText(""))
                        ? OrderFlow.Side.SELL : OrderFlow.Side.BUY;
                fireOrderFlow(new OrderFlow(ticker, px, size, side, time));
            }
            default -> {
            }
        }
    }

    /** Adds best bid/ask from a {@code bid:[[px,qty]], ask:[[px,qty]]} node; false if neither side present. */
    static boolean addTopOfBook(Level1Quote quote, JsonNode node) {
        boolean any = false;
        JsonNode bid = node.path("bid").path(0);
        if (bid.isArray() && decimal(bid.path(0)) != null) {
            quote.addQuote(QuoteType.BID, decimal(bid.path(0)));
            addIfPresent(quote, QuoteType.BID_SIZE, decimal(bid.path(1)));
            any = true;
        }
        JsonNode ask = node.path("ask").path(0);
        if (ask.isArray() && decimal(ask.path(0)) != null) {
            quote.addQuote(QuoteType.ASK, decimal(ask.path(0)));
            addIfPresent(quote, QuoteType.ASK_SIZE, decimal(ask.path(1)));
            any = true;
        }
        return any;
    }

    private static void addIfPresent(Level1Quote quote, QuoteType type, BigDecimal value) {
        if (value != null) {
            quote.addQuote(type, value);
        }
    }

    static BigDecimal decimal(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(n.asText());
            return v.signum() > 0 ? v.stripTrailingZeros() : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static ZonedDateTime parseTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return ZonedDateTime.now(ZoneOffset.UTC);
        }
        try {
            return Instant.parse(iso).atZone(ZoneOffset.UTC);
        } catch (RuntimeException e) {
            logger.debug("Unparseable QFEX time {}", iso);
            return ZonedDateTime.now(ZoneOffset.UTC);
        }
    }

    private static void requireTicker(Ticker ticker) {
        if (ticker == null || ticker.getSymbol() == null) {
            throw new IllegalArgumentException("ticker with symbol is required");
        }
    }
}
