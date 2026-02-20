package com.fueledbychai.marketdata.lighter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.ws.model.LighterMarketStats;
import com.fueledbychai.lighter.common.api.ws.model.LighterMarketStatsUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrderBookLevel;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrderBookUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;
import com.fueledbychai.lighter.common.api.ws.model.LighterTradesUpdate;
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

public class LighterQuoteEngine extends QuoteEngine {

    protected static final Logger logger = LoggerFactory.getLogger(LighterQuoteEngine.class);
    protected static final ZoneId UTC = ZoneId.of("UTC");
    protected static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(24L);
    protected static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365L);
    protected static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100L);
    protected static final BigDecimal BPS_MULTIPLIER = BigDecimal.valueOf(10_000L);
    protected static final InstrumentType[] SUPPORTED_INSTRUMENT_TYPES = new InstrumentType[] {
            InstrumentType.PERPETUAL_FUTURES, InstrumentType.CRYPTO_SPOT
    };

    protected final ILighterWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;
    protected final Map<Integer, Ticker> marketStatsTickerByMarketId = new ConcurrentHashMap<>();
    protected final Map<Integer, Ticker> orderBookTickerByMarketId = new ConcurrentHashMap<>();
    protected final Map<Integer, Ticker> orderFlowTickerByMarketId = new ConcurrentHashMap<>();
    protected final Map<Integer, MarketOrderBookState> orderBookStateByMarketId = new ConcurrentHashMap<>();
    protected final Set<Integer> marketStatsSubscriptions = ConcurrentHashMap.newKeySet();
    protected final Set<Integer> orderBookSubscriptions = ConcurrentHashMap.newKeySet();
    protected final Set<Integer> orderFlowSubscriptions = ConcurrentHashMap.newKeySet();

    protected volatile boolean started;

    public LighterQuoteEngine() {
        this(ExchangeWebSocketApiFactory.getApi(Exchange.LIGHTER, ILighterWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.LIGHTER));
    }

    protected LighterQuoteEngine(ILighterWebSocketApi webSocketApi, ITickerRegistry tickerRegistry) {
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
        return "Lighter";
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
        marketStatsSubscriptions.clear();
        orderBookSubscriptions.clear();
        orderFlowSubscriptions.clear();
        marketStatsTickerByMarketId.clear();
        orderBookTickerByMarketId.clear();
        orderFlowTickerByMarketId.clear();
        orderBookStateByMarketId.clear();
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.warn("useDelayedData() is not supported for Lighter market data");
    }

    @Override
    public synchronized void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        validateTickerAndListener(ticker, listener);
        int marketId = resolveMarketId(ticker);
        marketStatsTickerByMarketId.put(marketId, ticker);
        orderBookTickerByMarketId.put(marketId, ticker);

        if (marketStatsSubscriptions.add(marketId)) {
            webSocketApi.subscribeMarketStats(marketId, this::handleMarketStatsUpdate);
        }
        if (orderBookSubscriptions.add(marketId)) {
            webSocketApi.subscribeOrderBook(marketId, this::handleOrderBookUpdate);
        }
        super.subscribeLevel1(ticker, listener);
    }

    @Override
    public synchronized void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        validateTickerAndListener(ticker, listener);
        int marketId = resolveMarketId(ticker);
        orderBookTickerByMarketId.put(marketId, ticker);
        if (orderBookSubscriptions.add(marketId)) {
            webSocketApi.subscribeOrderBook(marketId, this::handleOrderBookUpdate);
        }
        super.subscribeMarketDepth(ticker, listener);
    }

    @Override
    public synchronized void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        validateTickerAndListener(ticker, listener);
        int marketId = resolveMarketId(ticker);
        orderFlowTickerByMarketId.put(marketId, ticker);
        if (orderFlowSubscriptions.add(marketId)) {
            webSocketApi.subscribeTrades(marketId, this::handleTradesUpdate);
        }
        super.subscribeOrderFlow(ticker, listener);
    }

    @Override
    public synchronized void unsubscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        super.unsubscribeLevel1(ticker, listener);
    }

    @Override
    public synchronized void unsubscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        super.unsubscribeMarketDepth(ticker, listener);
    }

    @Override
    public synchronized void unsubscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        super.unsubscribeOrderFlow(ticker, listener);
    }

    protected void handleMarketStatsUpdate(LighterMarketStatsUpdate update) {
        if (update == null || update.getMarketStatsByMarketId() == null || update.getMarketStatsByMarketId().isEmpty()) {
            return;
        }
        ZonedDateTime timestamp = ZonedDateTime.now(UTC);
        for (Map.Entry<String, LighterMarketStats> entry : update.getMarketStatsByMarketId().entrySet()) {
            Integer marketId = getMarketId(entry.getKey(), entry.getValue());
            if (marketId == null) {
                continue;
            }
            Ticker ticker = marketStatsTickerByMarketId.get(marketId);
            if (ticker == null || !hasLevel1Listeners(ticker)) {
                continue;
            }
            Level1Quote quote = buildMarketStatsQuote(ticker, entry.getValue(), timestamp);
            if (quote.getTypes().length > 0) {
                fireLevel1Quote(quote);
            }
        }
    }

    protected void handleOrderBookUpdate(LighterOrderBookUpdate update) {
        if (update == null) {
            return;
        }
        Integer marketId = update.getMarketId();
        if (marketId == null) {
            return;
        }
        Ticker ticker = orderBookTickerByMarketId.get(marketId);
        if (ticker == null) {
            return;
        }

        ZonedDateTime timestamp = ZonedDateTime.now(UTC);
        OrderBook orderBook = applyAndBuildOrderBook(ticker, update, timestamp);
        if (orderBook == null) {
            return;
        }

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

    protected void handleTradesUpdate(LighterTradesUpdate update) {
        if (update == null || update.getTrades() == null || update.getTrades().isEmpty()) {
            return;
        }
        for (LighterTrade trade : update.getTrades()) {
            if (trade == null || trade.getMarketId() == null) {
                continue;
            }
            Ticker ticker = orderFlowTickerByMarketId.get(trade.getMarketId());
            if (ticker == null || !hasOrderFlowListeners(ticker)) {
                continue;
            }
            OrderFlow orderFlow = toOrderFlow(ticker, trade);
            if (orderFlow != null) {
                fireOrderFlow(orderFlow);
            }
        }
    }

    protected Level1Quote buildMarketStatsQuote(Ticker ticker, LighterMarketStats stats, ZonedDateTime timestamp) {
        Level1Quote quote = new Level1Quote(ticker, timestamp);
        if (stats == null) {
            return quote;
        }
        if (stats.getMarkPrice() != null) {
            quote.addQuote(QuoteType.MARK_PRICE, ticker.formatPrice(stats.getMarkPrice()));
        }
        if (stats.getLastPrice() != null) {
            quote.addQuote(QuoteType.LAST, ticker.formatPrice(stats.getLastPrice()));
        }
        if (stats.getIndexPrice() != null) {
            quote.addQuote(QuoteType.UNDERLYING_PRICE, ticker.formatPrice(stats.getIndexPrice()));
        }
        if (stats.getDailyBaseVolume() != null) {
            quote.addQuote(QuoteType.VOLUME, stats.getDailyBaseVolume());
        }
        if (stats.getDailyQuoteVolume() != null) {
            quote.addQuote(QuoteType.VOLUME_NOTIONAL, stats.getDailyQuoteVolume());
        }
        if (stats.getOpenInterest() != null) {
            quote.addQuote(QuoteType.OPEN_INTEREST, stats.getOpenInterest());
            BigDecimal notionalPrice = stats.getMarkPrice() != null ? stats.getMarkPrice() : stats.getLastPrice();
            if (notionalPrice != null) {
                quote.addQuote(QuoteType.OPEN_INTEREST_NOTIONAL, stats.getOpenInterest().multiply(notionalPrice));
            }
        }
        addFundingQuotes(ticker, stats, quote);
        return quote;
    }

    protected OrderFlow toOrderFlow(Ticker ticker, LighterTrade trade) {
        if (ticker == null || trade == null || trade.getPrice() == null || trade.getSize() == null) {
            return null;
        }

        OrderFlow.Side side = resolveOrderFlowSide(trade);
        if (side == null) {
            return null;
        }

        ZonedDateTime timestamp = toZonedDateTime(trade.getTimestamp());
        BigDecimal formattedPrice = ticker.formatPrice(trade.getPrice());
        return new OrderFlow(ticker, formattedPrice, trade.getSize(), side, timestamp);
    }

    protected OrderFlow.Side resolveOrderFlowSide(LighterTrade trade) {
        if (trade == null) {
            return null;
        }
        String side = trade.getType();
        if (side != null) {
            if ("buy".equalsIgnoreCase(side)) {
                return OrderFlow.Side.BUY;
            }
            if ("sell".equalsIgnoreCase(side)) {
                return OrderFlow.Side.SELL;
            }
        }
        if (trade.getMakerAsk() != null) {
            // is_maker_ask=true means the resting maker order was an ask, so the taker flow was BUY.
            return trade.getMakerAsk() ? OrderFlow.Side.BUY : OrderFlow.Side.SELL;
        }
        return null;
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

    protected void addFundingQuotes(Ticker ticker, LighterMarketStats stats, Level1Quote quote) {
        if (ticker == null || stats == null || quote == null) {
            return;
        }
        BigDecimal hourlyFundingRate = toHourlyFundingRate(stats);
        if (hourlyFundingRate == null) {
            return;
        }

        BigDecimal fundingRateHourlyBps = hourlyFundingRate.multiply(BPS_MULTIPLIER);
        BigDecimal fundingRateApr = hourlyFundingRate.multiply(HOURS_PER_DAY).multiply(DAYS_PER_YEAR)
                .multiply(PERCENT_MULTIPLIER);

        quote.addQuote(QuoteType.FUNDING_RATE_HOURLY_BPS, fundingRateHourlyBps);
        quote.addQuote(QuoteType.FUNDING_RATE_APR, fundingRateApr);
    }

    protected BigDecimal toHourlyFundingRate(LighterMarketStats stats) {
        if (stats == null || stats.getCurrentFundingRate() == null) {
            return null;
        }
        // Lighter `current_funding_rate` is published as percentage points per hour.
        // Convert to a decimal hourly rate before deriving BPS/APR quote fields.
        return stats.getCurrentFundingRate().divide(PERCENT_MULTIPLIER, MathContext.DECIMAL64);
    }

    protected int resolveFundingIntervalHours(Ticker ticker) {
        if (ticker == null) {
            return 0;
        }
        if (ticker.getFundingRateInterval() > 0) {
            return ticker.getFundingRateInterval();
        }
        if (ticker.getInstrumentType() == InstrumentType.PERPETUAL_FUTURES) {
            return 8;
        }
        return 0;
    }

    protected Level1Quote buildTopOfBookQuote(Ticker ticker, OrderBook orderBook, ZonedDateTime timestamp) {
        Level1Quote quote = new Level1Quote(ticker, timestamp);
        OrderBook.BidSizePair bestBid = orderBook.getBestBidWithSize();
        if (bestBid != null && bestBid.price != null && bestBid.price.compareTo(BigDecimal.ZERO) > 0) {
            quote.addQuote(QuoteType.BID, ticker.formatPrice(bestBid.price));
            if (bestBid.size != null) {
                quote.addQuote(QuoteType.BID_SIZE, BigDecimal.valueOf(bestBid.size));
            }
        }
        OrderBook.BidSizePair bestAsk = orderBook.getBestAskWithSize();
        if (bestAsk != null && bestAsk.price != null && bestAsk.price.compareTo(BigDecimal.ZERO) > 0) {
            quote.addQuote(QuoteType.ASK, ticker.formatPrice(bestAsk.price));
            if (bestAsk.size != null) {
                quote.addQuote(QuoteType.ASK_SIZE, BigDecimal.valueOf(bestAsk.size));
            }
        }
        return quote;
    }

    protected OrderBook applyAndBuildOrderBook(Ticker ticker, LighterOrderBookUpdate update, ZonedDateTime timestamp) {
        MarketOrderBookState state = orderBookStateByMarketId.computeIfAbsent(update.getMarketId(),
                ignored -> new MarketOrderBookState());

        synchronized (state) {
            if (!applyOrderBookUpdate(state, update)) {
                return null;
            }
            return buildOrderBook(ticker, state, timestamp);
        }
    }

    protected boolean applyOrderBookUpdate(MarketOrderBookState state, LighterOrderBookUpdate update) {
        NonceStatus nonceStatus = getNonceStatus(state, update);
        if (nonceStatus == NonceStatus.STALE) {
            logger.debug("Skipping stale Lighter order book update for market {} (lastNonce={}, beginNonce={}, nonce={})",
                    update.getMarketId(), state.lastNonce, update.getBeginNonce(), update.getNonce());
            return false;
        }

        if (nonceStatus == NonceStatus.RESET || nonceStatus == NonceStatus.SNAPSHOT) {
            if (nonceStatus == NonceStatus.RESET) {
                logger.warn(
                        "Order book nonce gap for market {} (lastNonce={}, beginNonce={}, nonce={}). Rebuilding from current payload.",
                        update.getMarketId(), state.lastNonce, update.getBeginNonce(), update.getNonce());
            }
            replaceSide(state.bids, update.getBids());
            replaceSide(state.asks, update.getAsks());
            state.initialized = true;
        } else {
            applySideDelta(state.bids, update.getBids());
            applySideDelta(state.asks, update.getAsks());
        }

        if (update.getNonce() != null) {
            state.lastNonce = update.getNonce();
        }
        return true;
    }

    protected NonceStatus getNonceStatus(MarketOrderBookState state, LighterOrderBookUpdate update) {
        if (!state.initialized) {
            return NonceStatus.SNAPSHOT;
        }

        Long lastNonce = state.lastNonce;
        Long beginNonce = update.getBeginNonce();
        Long nonce = update.getNonce();

        if (lastNonce == null || beginNonce == null || nonce == null) {
            return NonceStatus.DELTA;
        }
        if (beginNonce.equals(lastNonce)) {
            if (nonce <= lastNonce) {
                return NonceStatus.STALE;
            }
            return NonceStatus.DELTA;
        }
        if (nonce <= lastNonce && beginNonce <= lastNonce) {
            return NonceStatus.STALE;
        }
        return NonceStatus.RESET;
    }

    protected void replaceSide(Map<BigDecimal, BigDecimal> target, List<LighterOrderBookLevel> levels) {
        target.clear();
        applySideDelta(target, levels);
    }

    protected void applySideDelta(Map<BigDecimal, BigDecimal> target, List<LighterOrderBookLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return;
        }
        for (LighterOrderBookLevel level : levels) {
            if (level == null || level.getPrice() == null || level.getSize() == null) {
                continue;
            }
            if (level.getSize().compareTo(BigDecimal.ZERO) <= 0) {
                target.remove(level.getPrice());
            } else {
                target.put(level.getPrice(), level.getSize());
            }
        }
    }

    protected OrderBook buildOrderBook(Ticker ticker, MarketOrderBookState state, ZonedDateTime timestamp) {
        OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());
        orderBook.updateFromSnapshot(toPriceLevels(state.bids), toPriceLevels(state.asks), timestamp);
        return orderBook;
    }

    protected List<OrderBook.PriceLevel> toPriceLevels(Map<BigDecimal, BigDecimal> levelsByPrice) {
        if (levelsByPrice == null || levelsByPrice.isEmpty()) {
            return List.of();
        }
        return levelsByPrice.entrySet().stream()
                .map(entry -> new OrderBook.PriceLevel(entry.getKey(), entry.getValue().doubleValue()))
                .toList();
    }

    protected Integer getMarketId(String marketIdString, LighterMarketStats stats) {
        if (stats != null && stats.getMarketId() != null) {
            return stats.getMarketId();
        }
        if (marketIdString == null || marketIdString.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(marketIdString);
        } catch (NumberFormatException ex) {
            logger.debug("Unable to parse market id from key '{}'", marketIdString);
            return null;
        }
    }

    protected int resolveMarketId(Ticker ticker) {
        Integer marketId = parseNonNegativeTickerId(ticker);
        if (marketId != null) {
            return marketId;
        }

        Ticker canonical = findTickerInRegistry(ticker);
        Integer canonicalMarketId = parseNonNegativeTickerId(canonical);
        if (canonical != null && canonicalMarketId != null) {
            applyCanonicalFields(ticker, canonical);
            return canonicalMarketId;
        }

        throw new IllegalArgumentException(
                "Unable to resolve Lighter market id for ticker " + ticker.getSymbol() + ". Set ticker id or use registry ticker.");
    }

    protected Integer parseNonNegativeTickerId(Ticker ticker) {
        if (ticker == null || ticker.getId() == null || ticker.getId().isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(ticker.getId().trim());
            if (parsed < 0) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected Ticker findTickerInRegistry(Ticker ticker) {
        for (InstrumentType type : candidateInstrumentTypes(ticker)) {
            Ticker byBrokerSymbol = tickerRegistry.lookupByBrokerSymbol(type, ticker.getSymbol());
            if (byBrokerSymbol != null) {
                return byBrokerSymbol;
            }

            Ticker byCommonSymbol = tickerRegistry.lookupByCommonSymbol(type, ticker.getSymbol());
            if (byCommonSymbol != null) {
                return byCommonSymbol;
            }

            if (ticker.getSymbol() != null && !ticker.getSymbol().contains("/")) {
                Ticker byDerivedCommonSymbol = tickerRegistry.lookupByCommonSymbol(type, ticker.getSymbol() + "/USDC");
                if (byDerivedCommonSymbol != null) {
                    return byDerivedCommonSymbol;
                }
            }
        }
        return null;
    }

    protected InstrumentType[] candidateInstrumentTypes(Ticker ticker) {
        if (ticker.getInstrumentType() != null) {
            return new InstrumentType[] { ticker.getInstrumentType() };
        }
        return SUPPORTED_INSTRUMENT_TYPES;
    }

    protected void applyCanonicalFields(Ticker target, Ticker source) {
        if (target.getId() == null || target.getId().isBlank()) {
            target.setId(source.getId());
        }
        if (target.getInstrumentType() == null) {
            target.setInstrumentType(source.getInstrumentType());
        }
        if (target.getExchange() == null) {
            target.setExchange(source.getExchange());
        }
        target.setMinimumTickSize(source.getMinimumTickSize());
        target.setOrderSizeIncrement(source.getOrderSizeIncrement());
        target.setContractMultiplier(source.getContractMultiplier());
        target.setFundingRateInterval(source.getFundingRateInterval());
        target.setMinimumOrderSize(source.getMinimumOrderSize());
        target.setMinimumOrderSizeNotional(source.getMinimumOrderSizeNotional());
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

    protected enum NonceStatus {
        SNAPSHOT, DELTA, RESET, STALE
    }

    protected static class MarketOrderBookState {
        protected final Map<BigDecimal, BigDecimal> bids = new HashMap<>();
        protected final Map<BigDecimal, BigDecimal> asks = new HashMap<>();
        protected Long lastNonce;
        protected boolean initialized;
    }
}
