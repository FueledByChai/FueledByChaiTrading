package com.fueledbychai.marketdata.deribit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.deribit.common.api.IDeribitWebSocketApi;
import com.fueledbychai.deribit.common.api.ws.model.DeribitBookLevel;
import com.fueledbychai.deribit.common.api.ws.model.DeribitBookUpdate;
import com.fueledbychai.deribit.common.api.ws.model.DeribitTickerUpdate;
import com.fueledbychai.deribit.common.api.ws.model.DeribitTrade;
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

public class DeribitQuoteEngine extends QuoteEngine {

    protected static final Logger logger = LoggerFactory.getLogger(DeribitQuoteEngine.class);
    protected static final ZoneId UTC = ZoneId.of("UTC");
    protected static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(24L);
    protected static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365L);
    protected static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100L);
    protected static final BigDecimal BPS_MULTIPLIER = BigDecimal.valueOf(10_000L);
    protected static final MathContext FUNDING_MATH = MathContext.DECIMAL64;
    protected static final InstrumentType[] SUPPORTED_TYPES = new InstrumentType[] {
            InstrumentType.CRYPTO_SPOT, InstrumentType.PERPETUAL_FUTURES, InstrumentType.OPTION
    };

    protected final IDeribitWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;
    protected final Map<String, Ticker> level1TickersBySymbol = new ConcurrentHashMap<>();
    protected final Map<String, Ticker> level2TickersBySymbol = new ConcurrentHashMap<>();
    protected final Map<String, Ticker> orderFlowTickersBySymbol = new ConcurrentHashMap<>();
    protected final Map<String, BookState> orderBookStateBySymbol = new ConcurrentHashMap<>();
    protected final Set<String> tickerSubscriptions = ConcurrentHashMap.newKeySet();
    protected final Set<String> orderBookSubscriptions = ConcurrentHashMap.newKeySet();
    protected final Set<String> orderFlowSubscriptions = ConcurrentHashMap.newKeySet();

    protected volatile boolean started;

    public DeribitQuoteEngine() {
        this(ExchangeWebSocketApiFactory.getApi(Exchange.DERIBIT, IDeribitWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.DERIBIT));
    }

    protected DeribitQuoteEngine(IDeribitWebSocketApi webSocketApi, ITickerRegistry tickerRegistry) {
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
        return "Deribit";
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
        orderBookStateBySymbol.clear();
        tickerSubscriptions.clear();
        orderBookSubscriptions.clear();
        orderFlowSubscriptions.clear();
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.warn("useDelayedData() is not supported for Deribit market data");
    }

    @Override
    public synchronized void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        validateTickerAndListener(ticker, listener);
        Ticker normalizedTicker = normalizeTicker(ticker);
        super.subscribeLevel1(normalizedTicker, listener);

        String instrumentName = normalizedTicker.getSymbol();
        level1TickersBySymbol.put(instrumentName, normalizedTicker);
        if (tickerSubscriptions.add(instrumentName)) {
            webSocketApi.subscribeTicker(instrumentName, this::handleTickerUpdate);
        }
    }

    @Override
    public synchronized void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        validateTickerAndListener(ticker, listener);
        Ticker normalizedTicker = normalizeTicker(ticker);
        super.subscribeMarketDepth(normalizedTicker, listener);

        String instrumentName = normalizedTicker.getSymbol();
        level2TickersBySymbol.put(instrumentName, normalizedTicker);
        if (orderBookSubscriptions.add(instrumentName)) {
            webSocketApi.subscribeOrderBook(instrumentName, this::handleOrderBookUpdate);
        }
    }

    @Override
    public synchronized void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        validateTickerAndListener(ticker, listener);
        Ticker normalizedTicker = normalizeTicker(ticker);
        super.subscribeOrderFlow(normalizedTicker, listener);

        String instrumentName = normalizedTicker.getSymbol();
        orderFlowTickersBySymbol.put(instrumentName, normalizedTicker);
        if (orderFlowSubscriptions.add(instrumentName)) {
            webSocketApi.subscribeTrades(instrumentName, this::handleTradesUpdate);
        }
    }

    protected void handleTickerUpdate(DeribitTickerUpdate update) {
        if (update == null || update.getInstrumentName() == null) {
            return;
        }

        Ticker ticker = level1TickersBySymbol.get(update.getInstrumentName());
        if (ticker == null || !hasLevel1Listeners(ticker)) {
            return;
        }

        Level1Quote quote = buildLevel1Quote(ticker, update);
        if (quote.getTypes().length > 0) {
            fireLevel1Quote(quote);
        }
    }

    protected void handleOrderBookUpdate(DeribitBookUpdate update) {
        if (update == null || update.getInstrumentName() == null) {
            return;
        }

        Ticker ticker = level2TickersBySymbol.get(update.getInstrumentName());
        if (ticker == null || !hasLevel2Listeners(ticker)) {
            return;
        }

        BookState state = orderBookStateBySymbol.computeIfAbsent(update.getInstrumentName(), key -> new BookState());
        OrderBook orderBook = applyOrderBookUpdate(ticker, update, state);
        if (orderBook != null) {
            fireMarketDepthQuote(new Level2Quote(ticker, orderBook, toZonedDateTime(update.getTimestamp())));
        }
    }

    protected void handleTradesUpdate(String instrumentName, List<DeribitTrade> trades) {
        if (instrumentName == null || trades == null || trades.isEmpty()) {
            return;
        }

        Ticker ticker = orderFlowTickersBySymbol.get(instrumentName);
        if (ticker == null || !hasOrderFlowListeners(ticker)) {
            return;
        }

        for (DeribitTrade trade : trades) {
            OrderFlow orderFlow = toOrderFlow(ticker, trade);
            if (orderFlow != null) {
                fireOrderFlow(orderFlow);
            }
        }
    }

    protected Level1Quote buildLevel1Quote(Ticker ticker, DeribitTickerUpdate update) {
        ZonedDateTime timestamp = toZonedDateTime(update.getTimestamp());
        Level1Quote quote = new Level1Quote(ticker, timestamp);

        addPriceLevel1Quote(quote, ticker, QuoteType.BID, update.getBestBidPrice(), QuoteType.BID_SIZE,
                update.getBestBidAmount());
        addPriceLevel1Quote(quote, ticker, QuoteType.ASK, update.getBestAskPrice(), QuoteType.ASK_SIZE,
                update.getBestAskAmount());
        addScalarQuote(quote, ticker, QuoteType.LAST, update.getLastPrice(), true);
        addScalarQuote(quote, ticker, QuoteType.MARK_PRICE, update.getMarkPrice(), true);
        addScalarQuote(quote, ticker, QuoteType.OPEN_INTEREST, update.getOpenInterest(), false);
        addScalarQuote(quote, ticker, QuoteType.VOLUME, update.getVolume(), false);
        addScalarQuote(quote, ticker, QuoteType.VOLUME_NOTIONAL, resolveVolumeNotional(update), false);
        addScalarQuote(quote, ticker, QuoteType.UNDERLYING_PRICE, update.getUnderlyingPrice(), true);

        if (update.getOpenInterest() != null) {
            BigDecimal referencePrice = firstNonNull(update.getMarkPrice(), update.getLastPrice(), update.getUnderlyingPrice());
            if (referencePrice != null) {
                quote.addQuote(QuoteType.OPEN_INTEREST_NOTIONAL, update.getOpenInterest().multiply(referencePrice));
            }
        }

        if (ticker.getInstrumentType() == InstrumentType.PERPETUAL_FUTURES) {
            addFundingQuotes(ticker, update.getCurrentFunding(), quote);
        }
        if (ticker.getInstrumentType() == InstrumentType.OPTION) {
            addScalarQuote(quote, ticker, QuoteType.BID_IV, update.getBidIv(), false);
            addScalarQuote(quote, ticker, QuoteType.ASK_IV, update.getAskIv(), false);
            addScalarQuote(quote, ticker, QuoteType.MARK_IV, update.getMarkIv(), false);
            addScalarQuote(quote, ticker, QuoteType.DELTA, update.getDelta(), false);
            addScalarQuote(quote, ticker, QuoteType.GAMMA, update.getGamma(), false);
            addScalarQuote(quote, ticker, QuoteType.THETA, update.getTheta(), false);
            addScalarQuote(quote, ticker, QuoteType.VEGA, update.getVega(), false);
            addScalarQuote(quote, ticker, QuoteType.RHO, update.getRho(), false);
            addScalarQuote(quote, ticker, QuoteType.INTEREST_RATE, update.getInterestRate(), false);
        }

        return quote;
    }

    protected void addPriceLevel1Quote(Level1Quote quote, Ticker ticker, QuoteType priceType, BigDecimal price,
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

    protected BigDecimal resolveVolumeNotional(DeribitTickerUpdate update) {
        if (update.getVolumeNotional() != null) {
            return update.getVolumeNotional();
        }
        if (update.getVolume() == null) {
            return null;
        }
        BigDecimal referencePrice = firstNonNull(update.getLastPrice(), update.getMarkPrice(), update.getUnderlyingPrice());
        if (referencePrice == null) {
            return null;
        }
        return update.getVolume().multiply(referencePrice);
    }

    protected void addFundingQuotes(Ticker ticker, BigDecimal fundingRate, Level1Quote quote) {
        if (fundingRate == null || quote == null || ticker == null) {
            return;
        }

        int fundingInterval = ticker.getFundingRateInterval();
        if (fundingInterval <= 0) {
            return;
        }

        BigDecimal hourlyFundingRate = fundingRate.divide(BigDecimal.valueOf(fundingInterval), FUNDING_MATH);
        BigDecimal fundingRateHourlyBps = hourlyFundingRate.multiply(BPS_MULTIPLIER);
        BigDecimal fundingRateApr = hourlyFundingRate.multiply(HOURS_PER_DAY).multiply(DAYS_PER_YEAR)
                .multiply(PERCENT_MULTIPLIER);

        quote.addQuote(QuoteType.FUNDING_RATE_HOURLY_BPS, fundingRateHourlyBps);
        quote.addQuote(QuoteType.FUNDING_RATE_APR, fundingRateApr);
    }

    protected OrderBook applyOrderBookUpdate(Ticker ticker, DeribitBookUpdate update, BookState state) {
        if (ticker == null || update == null || state == null) {
            return null;
        }

        synchronized (state) {
            if ("snapshot".equalsIgnoreCase(update.getUpdateType())) {
                state.bids.clear();
                state.asks.clear();
            }

            applyBookLevels(state.bids, update.getBids());
            applyBookLevels(state.asks, update.getAsks());
        }

        OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());
        orderBook.updateFromSnapshot(toPriceLevels(state.bids), toPriceLevels(state.asks),
                toZonedDateTime(update.getTimestamp()));
        return orderBook;
    }

    protected void applyBookLevels(NavigableMap<BigDecimal, Double> side, List<DeribitBookLevel> levels) {
        if (side == null || levels == null) {
            return;
        }

        for (DeribitBookLevel level : levels) {
            if (level == null || level.getPrice() == null) {
                continue;
            }

            String action = level.getAction();
            BigDecimal amount = level.getAmount();
            boolean remove = "delete".equalsIgnoreCase(action) || amount == null || amount.signum() <= 0;
            if (remove) {
                side.remove(level.getPrice());
            } else {
                side.put(level.getPrice(), amount.doubleValue());
            }
        }
    }

    protected List<OrderBook.PriceLevel> toPriceLevels(NavigableMap<BigDecimal, Double> side) {
        List<OrderBook.PriceLevel> levels = new ArrayList<>();
        if (side == null) {
            return levels;
        }
        for (Map.Entry<BigDecimal, Double> entry : side.entrySet()) {
            levels.add(new OrderBook.PriceLevel(entry.getKey(), entry.getValue()));
        }
        return levels;
    }

    protected OrderFlow toOrderFlow(Ticker ticker, DeribitTrade trade) {
        if (ticker == null || trade == null || trade.getPrice() == null || trade.getAmount() == null) {
            return null;
        }

        OrderFlow.Side side = resolveTradeSide(trade.getDirection());
        if (side == null) {
            return null;
        }

        return new OrderFlow(ticker, ticker.formatPrice(trade.getPrice()), trade.getAmount(), side,
                toZonedDateTime(trade.getTimestamp()));
    }

    protected OrderFlow.Side resolveTradeSide(String direction) {
        if (direction == null) {
            return null;
        }
        if ("buy".equalsIgnoreCase(direction)) {
            return OrderFlow.Side.BUY;
        }
        if ("sell".equalsIgnoreCase(direction)) {
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
            throw new IllegalArgumentException("Unknown Deribit ticker: " + ticker.getSymbol());
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
        target.setExchange(Exchange.DERIBIT);
        target.setPrimaryExchange(Exchange.DERIBIT);
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

    protected ZonedDateTime toZonedDateTime(Long timestamp) {
        if (timestamp == null) {
            return ZonedDateTime.now(UTC);
        }
        long epochMillis = timestamp;
        if (Math.abs(epochMillis) < 100_000_000_000L) {
            epochMillis = epochMillis * 1000L;
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), UTC);
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

    protected static class BookState {
        protected final NavigableMap<BigDecimal, Double> bids = new TreeMap<>(Comparator.reverseOrder());
        protected final NavigableMap<BigDecimal, Double> asks = new TreeMap<>();
    }
}
