package com.fueledbychai.marketdata.extended;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.extended.common.api.IExtendedRestApi;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.IOrderBook;
import com.fueledbychai.marketdata.Level1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2Quote;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderBookUpdateListener;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

/**
 * Market-data {@link QuoteEngine} for the Extended (extended.exchange) Starknet
 * perps DEX. Mirrors the Paradex engine: Level1 best-bid/ask comes from the
 * streamed order book, market depth from the same book, and order flow from the
 * public-trades stream. Extended subscribes via the WebSocket URL path, so the
 * per-ticker clients simply connect to the right stream URL.
 */
public class ExtendedQuoteEngine extends QuoteEngine
        implements OrderBookUpdateListener, TradesUpdateListener {

    protected static final Logger logger = LoggerFactory.getLogger(ExtendedQuoteEngine.class);
    protected Map<Ticker, TradesWebSocketClient> tradesClients = new HashMap<>();

    protected boolean started = false;
    protected final IExtendedRestApi restApi;
    protected ITickerRegistry tickerRegistry;

    public ExtendedQuoteEngine() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.EXTENDED, IExtendedRestApi.class),
                TickerRegistryFactory.getInstance(Exchange.EXTENDED));
    }

    protected ExtendedQuoteEngine(IExtendedRestApi restApi, ITickerRegistry tickerRegistry) {
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        if (tickerRegistry == null) {
            throw new IllegalArgumentException("tickerRegistry is required");
        }
        this.restApi = restApi;
        this.tickerRegistry = tickerRegistry;
    }

    @Override
    public String getDataProviderName() {
        return "Extended";
    }

    @Override
    public Date getServerTime() {
        return null;
    }

    @Override
    public boolean isConnected() {
        return started;
    }

    @Override
    public void startEngine() {
        started = true;
    }

    @Override
    public void startEngine(Properties props) {
        started = true;
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public void stopEngine() {
        started = false;
        clearSessionListeners();
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        // Not implemented for Extended
    }

    @Override
    public ILevel1Quote requestLevel1Snapshot(Ticker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
        Ticker canonical = canonicalize(ticker);
        JsonObject response = restApi.getMarketStats(canonical.getSymbol());
        if (response == null) {
            throw new IllegalStateException("No market stats returned for " + canonical.getSymbol());
        }
        // Some venues nest the stats payload under "data".
        JsonObject stats = response;
        if (response.has("data") && !response.get("data").isJsonNull() && response.get("data").isJsonObject()) {
            stats = response.getAsJsonObject("data");
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        Level1Quote quote = new Level1Quote(canonical, now);
        BigDecimal bidPrice = firstBigDecimal(stats, "bidPrice", "bid", "bestBid");
        BigDecimal askPrice = firstBigDecimal(stats, "askPrice", "ask", "bestAsk");
        BigDecimal lastPrice = firstBigDecimal(stats, "lastPrice", "last", "markPrice", "indexPrice");
        if (bidPrice != null) {
            quote.addQuote(QuoteType.BID, canonical.formatPrice(bidPrice));
        }
        if (askPrice != null) {
            quote.addQuote(QuoteType.ASK, canonical.formatPrice(askPrice));
        }
        if (lastPrice != null) {
            quote.addQuote(QuoteType.LAST, canonical.formatPrice(lastPrice));
        }
        return quote;
    }

    /**
     * Returns the canonical {@link Ticker} for the given input by resolving it
     * through the ticker registry. Mirrors the Paradex engine so all downstream
     * REST URLs, WebSocket stream paths, and OrderBookRegistry keys use a
     * consistent symbol. If lookup fails the original ticker is returned.
     */
    protected Ticker canonicalize(Ticker ticker) {
        if (ticker == null) {
            return null;
        }
        String symbol = ticker.getSymbol();
        if (symbol == null) {
            return ticker;
        }
        InstrumentType instrumentType = ticker.getInstrumentType();
        if (instrumentType == null) {
            return ticker;
        }
        Ticker direct = tickerRegistry.lookupByBrokerSymbol(instrumentType, symbol);
        if (direct != null) {
            return direct;
        }
        Ticker viaCommon = tickerRegistry.lookupByCommonSymbol(instrumentType, symbol);
        if (viaCommon != null) {
            return viaCommon;
        }
        return ticker;
    }

    protected BigDecimal firstBigDecimal(JsonObject object, String... keys) {
        for (String key : keys) {
            BigDecimal value = getBigDecimalField(object, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected BigDecimal getBigDecimalField(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return new BigDecimal(object.get(key).getAsString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        Ticker canonical = canonicalize(ticker);
        logger.debug("Subscribing to market depth for ticker: {} Listener: {}", canonical.getSymbol(), listener);
        super.subscribeMarketDepth(canonical, listener);
        OrderBookRegistry.getInstance().getOrderBook(canonical).addOrderBookUpdateListener(this);
    }

    @Override
    public void unsubscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        super.unsubscribeMarketDepth(canonicalize(ticker), listener);
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        Ticker canonical = canonicalize(ticker);
        super.subscribeLevel1(canonical, listener);
        OrderBookRegistry.getInstance().getOrderBook(canonical).addOrderBookUpdateListener(this);
    }

    @Override
    public void unsubscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        super.unsubscribeLevel1(canonicalize(ticker), listener);
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        Ticker canonical = canonicalize(ticker);
        super.subscribeOrderFlow(canonical, listener);
        TradesWebSocketClient tradesClient = tradesClients.get(canonical);
        if (tradesClient == null) {
            tradesClient = new TradesWebSocketClient();
            tradesClients.put(canonical, tradesClient);
            tradesClient.startTradesWSClient(canonical, this);
        }
    }

    @Override
    public void unsubscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        super.unsubscribeOrderFlow(canonicalize(ticker), listener);
    }

    @Override
    public void bestBidUpdated(Ticker ticker, BigDecimal bestBid, Double bidSize, ZonedDateTime timestamp) {
        Level1Quote quote = new Level1Quote(ticker, timestamp);
        quote.addQuote(QuoteType.BID, ticker.formatPrice(bestBid));
        if (bidSize != null) {
            quote.addQuote(QuoteType.BID_SIZE, BigDecimal.valueOf(bidSize));
        }
        super.fireLevel1Quote(quote);
    }

    @Override
    public void bestAskUpdated(Ticker ticker, BigDecimal bestAsk, Double askSize, ZonedDateTime timestamp) {
        Level1Quote quote = new Level1Quote(ticker, timestamp);
        quote.addQuote(QuoteType.ASK, ticker.formatPrice(bestAsk));
        if (askSize != null) {
            quote.addQuote(QuoteType.ASK_SIZE, BigDecimal.valueOf(askSize));
        }
        super.fireLevel1Quote(quote);
    }

    @Override
    public void orderBookImbalanceUpdated(Ticker ticker, BigDecimal imbalance, ZonedDateTime timestamp) {
        // Not surfaced as a quote for Extended (mirrors Paradex).
    }

    @Override
    public void orderBookUpdated(Ticker ticker, IOrderBook book, ZonedDateTime timestamp) {
        logger.debug("Order book updated for {}: bestBid: {}, bestAsk: {}", ticker, book.getBestBid().price,
                book.getBestAsk().price);
        Level2Quote quote = new Level2Quote(ticker, book, timestamp);
        super.fireMarketDepthQuote(quote);
    }

    @Override
    public void newTrade(long createdAtTimestamp, String market, String price, String side, String size) {
        Ticker ticker = resolveTicker(market);
        if (ticker == null) {
            logger.warn("Skipping trade update for unknown symbol '{}'", market);
            return;
        }

        BigDecimal sizeDecimal = parseBigDecimal(size);
        if (sizeDecimal == null) {
            logger.debug("Skipping trade update for '{}' with missing/blank size", market);
            return;
        }

        BigDecimal formattedPrice = ticker.formatPrice(parseBigDecimal(price));
        if (formattedPrice == null) {
            logger.debug("Skipping trade update for '{}' with missing/blank price", market);
            return;
        }

        OrderFlow.Side orderSide = parseSide(side);
        if (orderSide == null) {
            logger.debug("Skipping trade update for '{}' with unrecognized side '{}'", market, side);
            return;
        }

        OrderFlow orderFlow = new OrderFlow(ticker, formattedPrice, sizeDecimal, orderSide,
                convertToZonedDateTime(createdAtTimestamp));
        super.fireOrderFlow(orderFlow);
    }

    protected OrderFlow.Side parseSide(String side) {
        if (side == null) {
            return null;
        }
        String normalized = side.trim().toUpperCase();
        if (normalized.startsWith("B")) {
            return OrderFlow.Side.BUY;
        }
        if (normalized.startsWith("S")) {
            return OrderFlow.Side.SELL;
        }
        return null;
    }

    protected BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            logger.debug("Could not parse BigDecimal from value '{}'", value);
            return null;
        }
    }

    protected Ticker resolveTicker(String symbol) {
        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (ticker == null) {
            ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.CRYPTO_SPOT, symbol);
        }
        return ticker;
    }

    protected ZonedDateTime convertToZonedDateTime(long timestamp) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"));
    }
}
