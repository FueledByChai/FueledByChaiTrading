package com.fueledbychai.marketdata.bybit;

import java.math.BigDecimal;
import java.math.MathContext;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.bybit.common.api.IBybitWebSocketApi;
import com.fueledbychai.bybit.common.api.ws.model.BybitOrderBookLevel;
import com.fueledbychai.bybit.common.api.ws.model.BybitOrderBookUpdate;
import com.fueledbychai.bybit.common.api.ws.model.BybitTickerUpdate;
import com.fueledbychai.bybit.common.api.ws.model.BybitTrade;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.Level1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2Quote;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderBook;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class BybitQuoteEngine extends QuoteEngine {

    protected static final Logger logger = LoggerFactory.getLogger(BybitQuoteEngine.class);
    protected static final ZoneId UTC = ZoneId.of("UTC");
    protected static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(24L);
    protected static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365L);
    protected static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100L);
    protected static final BigDecimal BPS_MULTIPLIER = BigDecimal.valueOf(10_000L);
    protected static final MathContext FUNDING_MATH = MathContext.DECIMAL64;

    protected static final InstrumentType[] SUPPORTED_TYPES = new InstrumentType[] { InstrumentType.CRYPTO_SPOT,
            InstrumentType.PERPETUAL_FUTURES, InstrumentType.FUTURES, InstrumentType.OPTION };

    protected final IBybitWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;

    protected final Map<String, Ticker> level1TickersBySymbol = new ConcurrentHashMap<>();
    protected final Map<String, Ticker> level2TickersBySymbol = new ConcurrentHashMap<>();
    protected final Map<String, Ticker> orderFlowTickersBySymbol = new ConcurrentHashMap<>();

    protected final Set<String> tickerSubscriptions = ConcurrentHashMap.newKeySet();
    protected final Set<String> orderBookSubscriptions = ConcurrentHashMap.newKeySet();
    protected final Set<String> orderFlowSubscriptions = ConcurrentHashMap.newKeySet();

    protected volatile boolean started;

    public BybitQuoteEngine() {
        this(ExchangeWebSocketApiFactory.getApi(Exchange.BYBIT, IBybitWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.BYBIT));
    }

    protected BybitQuoteEngine(IBybitWebSocketApi webSocketApi, ITickerRegistry tickerRegistry) {
        if (webSocketApi == null) {
            throw new IllegalArgumentException("webSocketApi is required");
        }
        if (tickerRegistry == null) {
            throw new IllegalArgumentException("tickerRegistry is required");
        }
        this.webSocketApi = webSocketApi;
        this.tickerRegistry = tickerRegistry;
    }

    @Override
    public String getDataProviderName() {
        return "Bybit";
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
    public synchronized void stopEngine() {
        started = false;
        webSocketApi.disconnectAll();
        level1TickersBySymbol.clear();
        level2TickersBySymbol.clear();
        orderFlowTickersBySymbol.clear();
        tickerSubscriptions.clear();
        orderBookSubscriptions.clear();
        orderFlowSubscriptions.clear();
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.warn("useDelayedData() is not supported for Bybit market data");
    }

    @Override
    public synchronized void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        validateTickerAndListener(ticker, listener);
        Ticker normalizedTicker = normalizeTicker(ticker);
        super.subscribeLevel1(normalizedTicker, listener);

        String instrumentId = normalizedTicker.getSymbol();
        level1TickersBySymbol.put(instrumentId, normalizedTicker);
        if (tickerSubscriptions.add(instrumentId)) {
            webSocketApi.subscribeTicker(instrumentId, normalizedTicker.getInstrumentType(), this::handleTickerUpdate);
        }

        // Bybit option ticker updates can omit top-of-book fields. Subscribe to
        // orderbook for options so L1 listeners still receive bid/ask via
        // buildTopOfBookQuote.
        if (normalizedTicker.getInstrumentType() == InstrumentType.OPTION && orderBookSubscriptions.add(instrumentId)) {
            webSocketApi.subscribeOrderBook(instrumentId, normalizedTicker.getInstrumentType(),
                    this::handleOrderBookUpdate);
        }
    }

    @Override
    public synchronized void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        validateTickerAndListener(ticker, listener);
        Ticker normalizedTicker = normalizeTicker(ticker);
        super.subscribeMarketDepth(normalizedTicker, listener);

        String instrumentId = normalizedTicker.getSymbol();
        level2TickersBySymbol.put(instrumentId, normalizedTicker);
        if (orderBookSubscriptions.add(instrumentId)) {
            webSocketApi.subscribeOrderBook(instrumentId, normalizedTicker.getInstrumentType(),
                    this::handleOrderBookUpdate);
        }
    }

    @Override
    public synchronized void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        validateTickerAndListener(ticker, listener);
        Ticker normalizedTicker = normalizeTicker(ticker);
        super.subscribeOrderFlow(normalizedTicker, listener);

        String instrumentId = normalizedTicker.getSymbol();
        orderFlowTickersBySymbol.put(instrumentId, normalizedTicker);
        if (orderFlowSubscriptions.add(instrumentId)) {
            webSocketApi.subscribeTrades(instrumentId, normalizedTicker.getInstrumentType(), this::handleTradesUpdate);
        }
    }

    protected void handleTickerUpdate(BybitTickerUpdate update) {
        if (update == null || update.getInstrumentId() == null) {
            return;
        }

        Ticker ticker = level1TickersBySymbol.get(update.getInstrumentId());
        if (ticker == null || !hasLevel1Listeners(ticker)) {
            return;
        }

        Level1Quote quote = buildLevel1Quote(ticker, update);
        if (quote.getTypes().length > 0) {
            fireLevel1Quote(quote);
        }
    }

    protected Level1Quote buildLevel1Quote(Ticker ticker, BybitTickerUpdate update) {
        ZonedDateTime timestamp = toZonedDateTime(update.getTimestamp());
        Level1Quote quote = new Level1Quote(ticker, timestamp);

        addPriceQuote(quote, ticker, QuoteType.BID, update.getBestBidPrice(), QuoteType.BID_SIZE, update.getBestBidSize());
        addPriceQuote(quote, ticker, QuoteType.ASK, update.getBestAskPrice(), QuoteType.ASK_SIZE, update.getBestAskSize());
        addPriceQuote(quote, ticker, QuoteType.LAST, update.getLastPrice(), QuoteType.LAST_SIZE, update.getLastSize());

        addScalarQuote(quote, ticker, QuoteType.MARK_PRICE, update.getMarkPrice(), true);
        addScalarQuote(quote, ticker, QuoteType.OPEN_INTEREST, update.getOpenInterest(), false);
        addScalarQuote(quote, ticker, QuoteType.VOLUME, update.getVolume(), false);
        addScalarQuote(quote, ticker, QuoteType.VOLUME_NOTIONAL, update.getVolumeNotional(), false);
        addScalarQuote(quote, ticker, QuoteType.UNDERLYING_PRICE, update.getUnderlyingPrice(), true);

        if (update.getOpenInterest() != null) {
            BigDecimal referencePrice = firstNonNull(update.getMarkPrice(), update.getIndexPrice(), update.getLastPrice(),
                    update.getBestBidPrice(), update.getBestAskPrice());
            if (referencePrice != null) {
                quote.addQuote(QuoteType.OPEN_INTEREST_NOTIONAL, update.getOpenInterest().multiply(referencePrice));
            }
        }

        if (ticker.getInstrumentType() == InstrumentType.PERPETUAL_FUTURES) {
            addFundingQuotes(ticker, update.getFundingRate(), quote);
        }

        if (ticker.getInstrumentType() == InstrumentType.OPTION) {
            addScalarQuote(quote, ticker, QuoteType.BID_IV, update.getBidIv(), false);
            addScalarQuote(quote, ticker, QuoteType.ASK_IV, update.getAskIv(), false);
            addScalarQuote(quote, ticker, QuoteType.MARK_IV, update.getMarkIv(), false);
            addScalarQuote(quote, ticker, QuoteType.DELTA, update.getDelta(), false);
            addScalarQuote(quote, ticker, QuoteType.GAMMA, update.getGamma(), false);
            addScalarQuote(quote, ticker, QuoteType.THETA, update.getTheta(), false);
            addScalarQuote(quote, ticker, QuoteType.VEGA, update.getVega(), false);
        }

        return quote;
    }

    protected void addPriceQuote(Level1Quote quote, Ticker ticker, QuoteType priceType, BigDecimal price,
            QuoteType sizeType, BigDecimal size) {
        if (price == null) {
            return;
        }
        quote.addQuote(priceType, ticker.formatPrice(price));
        if (size != null) {
            quote.addQuote(sizeType, size);
        }
    }

    protected void addScalarQuote(Level1Quote quote, Ticker ticker, QuoteType quoteType, BigDecimal value,
            boolean formatAsPrice) {
        if (value == null) {
            return;
        }
        quote.addQuote(quoteType, formatAsPrice ? ticker.formatPrice(value) : value);
    }

    protected void addFundingQuotes(Ticker ticker, BigDecimal fundingRate, Level1Quote quote) {
        if (ticker == null || quote == null || fundingRate == null) {
            return;
        }

        int fundingIntervalHours = ticker.getFundingRateInterval() > 0 ? ticker.getFundingRateInterval() : 8;
        BigDecimal hourlyFundingRate = fundingRate.divide(BigDecimal.valueOf(fundingIntervalHours), FUNDING_MATH);
        BigDecimal hourlyBps = hourlyFundingRate.multiply(BPS_MULTIPLIER);
        BigDecimal annualizedPercent = hourlyFundingRate.multiply(HOURS_PER_DAY).multiply(DAYS_PER_YEAR)
                .multiply(PERCENT_MULTIPLIER);

        quote.addQuote(QuoteType.FUNDING_RATE_HOURLY_BPS, hourlyBps);
        quote.addQuote(QuoteType.FUNDING_RATE_APR, annualizedPercent);
    }

    protected void handleOrderBookUpdate(BybitOrderBookUpdate update) {
        if (update == null || update.getInstrumentId() == null) {
            return;
        }

        Ticker ticker = level2TickersBySymbol.get(update.getInstrumentId());
        if (ticker == null) {
            ticker = level1TickersBySymbol.get(update.getInstrumentId());
        }
        if (ticker == null) {
            return;
        }

        OrderBook orderBook = toOrderBook(ticker, update);
        if (orderBook == null) {
            return;
        }

        ZonedDateTime timestamp = toZonedDateTime(update.getTimestamp());
        if (hasLevel2Listeners(ticker)) {
            fireMarketDepthQuote(new Level2Quote(ticker, orderBook, timestamp));
        }

        if (hasLevel1Listeners(ticker)) {
            Level1Quote topOfBookQuote = buildTopOfBookQuote(ticker, orderBook, timestamp);
            if (topOfBookQuote.getTypes().length > 0) {
                fireLevel1Quote(topOfBookQuote);
            }
        }
    }

    protected OrderBook toOrderBook(Ticker ticker, BybitOrderBookUpdate update) {
        if (ticker == null || update == null) {
            return null;
        }

        List<OrderBook.PriceLevel> bids = toPriceLevels(update.getBids());
        List<OrderBook.PriceLevel> asks = toPriceLevels(update.getAsks());

        if (bids.isEmpty() && asks.isEmpty()) {
            return null;
        }

        OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());
        orderBook.updateFromSnapshot(bids, asks, toZonedDateTime(update.getTimestamp()));
        return orderBook;
    }

    protected List<OrderBook.PriceLevel> toPriceLevels(List<BybitOrderBookLevel> levels) {
        List<OrderBook.PriceLevel> converted = new ArrayList<>();
        if (levels == null) {
            return converted;
        }

        for (BybitOrderBookLevel level : levels) {
            if (level == null || level.getPrice() == null || level.getSize() == null) {
                continue;
            }
            converted.add(new OrderBook.PriceLevel(level.getPrice(), level.getSize().doubleValue()));
        }
        return converted;
    }

    protected Level1Quote buildTopOfBookQuote(Ticker ticker, OrderBook orderBook, ZonedDateTime timestamp) {
        Level1Quote quote = new Level1Quote(ticker, timestamp);
        if (orderBook == null) {
            return quote;
        }

        OrderBook.BidSizePair bestBid = orderBook.getBestBid();
        OrderBook.BidSizePair bestAsk = orderBook.getBestAsk();

        if (bestBid != null && bestBid.getPrice() != null && bestBid.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            quote.addQuote(QuoteType.BID, ticker.formatPrice(bestBid.getPrice()));
            if (bestBid.getSize() != null) {
                quote.addQuote(QuoteType.BID_SIZE, BigDecimal.valueOf(bestBid.getSize()));
            }
        }

        if (bestAsk != null && bestAsk.getPrice() != null && bestAsk.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            quote.addQuote(QuoteType.ASK, ticker.formatPrice(bestAsk.getPrice()));
            if (bestAsk.getSize() != null) {
                quote.addQuote(QuoteType.ASK_SIZE, BigDecimal.valueOf(bestAsk.getSize()));
            }
        }

        return quote;
    }

    protected void handleTradesUpdate(String instrumentId, List<BybitTrade> trades) {
        if (instrumentId == null || trades == null || trades.isEmpty()) {
            return;
        }

        Ticker ticker = orderFlowTickersBySymbol.get(instrumentId);
        if (ticker == null || !hasOrderFlowListeners(ticker)) {
            return;
        }

        for (BybitTrade trade : trades) {
            OrderFlow orderFlow = toOrderFlow(ticker, trade);
            if (orderFlow != null) {
                fireOrderFlow(orderFlow);
            }
        }
    }

    protected OrderFlow toOrderFlow(Ticker ticker, BybitTrade trade) {
        if (ticker == null || trade == null || trade.getPrice() == null || trade.getSize() == null) {
            return null;
        }

        OrderFlow.Side side = resolveTradeSide(trade.getSide());
        if (side == null) {
            return null;
        }

        return new OrderFlow(ticker, ticker.formatPrice(trade.getPrice()), trade.getSize(), side,
                toZonedDateTime(trade.getTimestamp()));
    }

    protected OrderFlow.Side resolveTradeSide(String side) {
        if (side == null) {
            return null;
        }
        if ("buy".equalsIgnoreCase(side)) {
            return OrderFlow.Side.BUY;
        }
        if ("sell".equalsIgnoreCase(side)) {
            return OrderFlow.Side.SELL;
        }
        return null;
    }

    protected Ticker normalizeTicker(Ticker ticker) {
        if (ticker == null || ticker.getSymbol() == null || ticker.getSymbol().isBlank()) {
            throw new IllegalArgumentException("ticker symbol is required");
        }

        Ticker canonical = resolveCanonicalTicker(ticker);
        if (canonical == null) {
            throw new IllegalArgumentException("Unknown Bybit ticker: " + ticker.getSymbol());
        }

        applyCanonicalFields(ticker, canonical);
        return ticker;
    }

    protected Ticker resolveCanonicalTicker(Ticker ticker) {
        String symbol = ticker.getSymbol().trim();
        for (InstrumentType instrumentType : candidateTypesFor(ticker)) {
            Ticker resolved = tickerRegistry.lookupByBrokerSymbol(instrumentType, symbol);
            if (resolved == null) {
                resolved = tickerRegistry.lookupByCommonSymbol(instrumentType, symbol);
            }
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    protected InstrumentType[] candidateTypesFor(Ticker ticker) {
        if (ticker != null && ticker.getInstrumentType() != null) {
            InstrumentType instrumentType = ticker.getInstrumentType();
            for (InstrumentType supportedType : SUPPORTED_TYPES) {
                if (supportedType == instrumentType) {
                    return new InstrumentType[] { instrumentType };
                }
            }
        }
        return SUPPORTED_TYPES;
    }

    protected void applyCanonicalFields(Ticker target, Ticker source) {
        target.setSymbol(source.getSymbol());
        target.setExchange(Exchange.BYBIT);
        target.setPrimaryExchange(Exchange.BYBIT);
        target.setInstrumentType(source.getInstrumentType());
        target.setCurrency(source.getCurrency());
        target.setMinimumTickSize(source.getMinimumTickSize());
        target.setOrderSizeIncrement(source.getOrderSizeIncrement());
        target.setContractMultiplier(source.getContractMultiplier());
        target.setFundingRateInterval(source.getFundingRateInterval());
        target.setMinimumOrderSize(source.getMinimumOrderSize());
        target.setMinimumOrderSizeNotional(source.getMinimumOrderSizeNotional());
        target.setExpiryYear(source.getExpiryYear());
        target.setExpiryMonth(source.getExpiryMonth());
        target.setExpiryDay(source.getExpiryDay());
        target.setStrike(source.getStrike());
        target.setRight(source.getRight());
        if (source.getId() != null && !source.getId().isBlank()) {
            target.setId(source.getId());
        }
    }

    private static final long EPOCH_MILLIS_THRESHOLD = 100_000_000_000L;

    protected static ZonedDateTime convertEpochToZonedDateTime(Long timestamp, ZoneId zoneId) {
        if (timestamp == null) {
            return ZonedDateTime.now(zoneId);
        }
        long epochMillis = timestamp;
        if (Math.abs(epochMillis) < EPOCH_MILLIS_THRESHOLD) {
            epochMillis = epochMillis * 1000L;
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId);
    }

    protected ZonedDateTime toZonedDateTime(Long timestamp) {
        return convertEpochToZonedDateTime(timestamp, UTC);
    }

    protected BigDecimal firstNonNull(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected boolean hasLevel1Listeners(Ticker ticker) {
        synchronized (level1ListenerMap) {
            List<Level1QuoteListener> listeners = level1ListenerMap.get(ticker);
            if (listeners == null) {
                return false;
            }
            synchronized (listeners) {
                return !listeners.isEmpty();
            }
        }
    }

    protected boolean hasLevel2Listeners(Ticker ticker) {
        synchronized (level2ListenerMap) {
            List<Level2QuoteListener> listeners = level2ListenerMap.get(ticker);
            if (listeners == null) {
                return false;
            }
            synchronized (listeners) {
                return !listeners.isEmpty();
            }
        }
    }

    protected boolean hasOrderFlowListeners(Ticker ticker) {
        synchronized (orderFlowListenerMap) {
            List<OrderFlowListener> listeners = orderFlowListenerMap.get(ticker);
            if (listeners == null) {
                return false;
            }
            synchronized (listeners) {
                return !listeners.isEmpty();
            }
        }
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
