package com.fueledbychai.broker.paper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerErrorListener;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderStatus.CancelReason;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Modifier;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.ComboTicker;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.time.TimeUpdatedListener;

/**
 * A paper broker that supports trading multiple tickers simultaneously through a
 * single QuoteEngine. Uses global Level1 and OrderFlow subscriptions to receive
 * market data for all tickers, with per-ticker state for positions, BBO, and
 * fill evaluation, and a shared account balance across all tickers.
 */
public class MultiTickerPaperBroker extends AbstractBasicBroker implements Level1QuoteListener, OrderFlowListener {

    protected final Logger logger = LoggerFactory.getLogger(MultiTickerPaperBroker.class);

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    protected final QuoteEngine quoteEngine;
    protected final PaperBrokerLatency latencyModel;

    private Timer accountUpdateTimer = new Timer(true);

    // --- Shared account state ---
    protected double startingAccountBalance;
    protected double currentAccountBalance;
    protected double totalFees = 0.0;
    protected double dollarVolume = 0.0;
    protected int totalOrdersPlaced = 0;
    protected double fundingAccruedOrPaidTotal = 0.0;

    // --- Shared order tracking ---
    protected final Map<String, OrderTicket> openOrders = new LinkedHashMap<>();
    protected final Set<OrderTicket> executedOrders = Collections.synchronizedSet(
            new TreeSet<>((o1, o2) -> o2.getOrderEntryTime().compareTo(o1.getOrderEntryTime())));
    protected final ReadWriteLock orderOperationsLock = new ReentrantReadWriteLock();

    // --- Per-ticker state (keyed by symbol string to avoid Ticker.equals mismatches) ---
    protected final ConcurrentHashMap<String, TickerState> tickerStates = new ConcurrentHashMap<>();

    protected boolean isConnected = false;
    protected boolean firstTradeWrittenToFile = false;
    protected String csvFilePath = null;
    protected String outputDir = null;

    // --- Commission (per exchange) ---
    protected final double makerFee;
    protected final double takerFee;


    // ========================================================================
    // Per-ticker state holder
    // ========================================================================

    protected static class TickerState {
        final Ticker ticker;
        final ReadWriteLock marketDataLock = new ReentrantReadWriteLock();

        double bestBidPrice = 0.0;
        double bestAskPrice = Double.MAX_VALUE;
        double midPrice = 0.0;
        double markPrice = 0.0;
        double fundingRate = 0.0;
        double fundingAccruedOrPaid = 0.0;
        long lastFundingTimestamp = 0;

        BigDecimal currentPosition = BigDecimal.ZERO;
        double averageEntryPrice = 0.0;
        double realizedPnL = 0.0;

        final ConcurrentHashMap<String, OrderTicket> openBids = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, OrderTicket> openAsks = new ConcurrentHashMap<>();

        final Deque<SpreadEntry> spreadHistory = new ArrayDeque<>();
        static final long TIME_WINDOW_MILLIS = 6000;
        static final double DISLOCATION_MULTIPLIER = 7.5;

        TickerState(Ticker ticker) {
            this.ticker = ticker;
        }
    }

    protected static class SpreadEntry {
        final double spread;
        final long timestamp;

        SpreadEntry(double spread, long timestamp) {
            this.spread = spread;
            this.timestamp = timestamp;
        }
    }


    // ========================================================================
    // Constructors
    // ========================================================================

    public MultiTickerPaperBroker(Exchange exchange, double startingBalance) {
        this(exchange, null, startingBalance);
    }

    public MultiTickerPaperBroker(Exchange exchange, PaperBrokerLatency latencyModel, double startingBalance) {
        this(exchange, null, latencyModel, startingBalance);
    }

    public MultiTickerPaperBroker(Exchange exchange, PaperBrokerCommission commission,
                                  PaperBrokerLatency latencyModel, double startingBalance) {
        this.quoteEngine = QuoteEngine.getInstance(exchange);
        this.latencyModel = latencyModel != null ? new PaperBrokerLatency(latencyModel)
                : PaperBrokerProfileRegistry.getLatencyProfile(exchange);
        if (commission != null) {
            this.makerFee = commission.getMakerFeeBps() / 10000.0;
            this.takerFee = commission.getTakerFeeBps() / 10000.0;
        } else {
            PaperBrokerCommission defaultCommission = PaperBrokerProfileRegistry.getCommissionProfile(exchange,
                    com.fueledbychai.data.InstrumentType.PERPETUAL_FUTURES);
            this.makerFee = defaultCommission.getMakerFeeBps() / 10000.0;
            this.takerFee = defaultCommission.getTakerFeeBps() / 10000.0;
        }
        this.startingAccountBalance = startingBalance;
        this.currentAccountBalance = startingBalance;
    }

    protected TickerState getOrCreateTickerState(Ticker ticker) {
        return tickerStates.computeIfAbsent(ticker.getSymbol(), sym -> new TickerState(ticker));
    }

    // ========================================================================
    // Broker identity & configuration
    // ========================================================================

    @Override
    public String getBrokerName() {
        return "MultiTickerPaperBroker-" + quoteEngine.getDataProviderName();
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    // ========================================================================
    // Connection lifecycle
    // ========================================================================

    @Override
    public void connect() {
        if (isConnected) {
            logger.warn("Already connected to MultiTickerPaperBroker. Ignoring connect call.");
            return;
        }
        logger.info("Connecting to MultiTickerPaperBroker...");
        isConnected = true;

        if (!quoteEngine.started()) {
            quoteEngine.startEngine();
        }

        String balanceFilePath = generateBalanceFilename();
        File balanceFile = new File(balanceFilePath);
        if (balanceFile.exists()) {
            try {
                String balanceContent = new String(Files.readAllBytes(Paths.get(balanceFilePath)));
                startingAccountBalance = Double.parseDouble(balanceContent.trim());
                currentAccountBalance = startingAccountBalance;
                logger.info("Starting account balance read from file: {}", startingAccountBalance);
            } catch (Exception e) {
                logger.error("Failed to read starting balance from file. Using default: {}", startingAccountBalance, e);
            }
        }

        csvFilePath = generateCsvFilename();

        accountUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    fireAccountUpdate();
                } catch (Exception e) {
                    logger.error("Error during account update: {}", e.getMessage(), e);
                }
                try {
                    writeCurrentBalanceToFile(balanceFilePath);
                } catch (Exception e) {
                    logger.error("Error writing current balance to file: {}", e.getMessage(), e);
                }
            }
        }, 0, 1000);

        quoteEngine.subscribeGlobalLevel1(this);
        quoteEngine.subscribeGlobalOrderFlow(this);
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    protected void onDisconnect() {
        isConnected = false;
        accountUpdateTimer.cancel();
        accountUpdateTimer = new Timer(true);
        quoteEngine.unsubscribeGlobalLevel1(this);
        quoteEngine.unsubscribeGlobalOrderFlow(this);
    }

    // ========================================================================
    // Market data callbacks (global subscriptions)
    // ========================================================================

    @Override
    public void quoteRecieved(ILevel1Quote quote) {
        if (quote == null || quote.getTicker() == null) {
            return;
        }

        Ticker ticker = quote.getTicker();
        TickerState state = getOrCreateTickerState(ticker);

        if (quote.containsType(QuoteType.MARK_PRICE)) {
            markPriceUpdated(state, quote.getValue(QuoteType.MARK_PRICE), quote.getTimeStamp());
        }

        if (quote.containsType(QuoteType.FUNDING_RATE_APR)) {
            fundingRateUpdated(state, quote.getValue(QuoteType.FUNDING_RATE_APR), quote.getTimeStamp());
        }

        applyTopOfBookUpdate(state,
                quote.containsType(QuoteType.BID) ? quote.getValue(QuoteType.BID) : null,
                quote.containsType(QuoteType.ASK) ? quote.getValue(QuoteType.ASK) : null,
                quote.isCleared(QuoteType.BID), quote.isCleared(QuoteType.ASK));
    }

    @Override
    public void orderflowReceived(OrderFlow orderflow) {
        if (orderflow == null || orderflow.getSide() == null || orderflow.getPrice() == null
                || orderflow.getTicker() == null) {
            return;
        }
        TickerState state = getOrCreateTickerState(orderflow.getTicker());
        evaluateTradeBasedFills(state, orderflow.getSide(), orderflow.getPrice().doubleValue());
    }

    protected void markPriceUpdated(TickerState state, BigDecimal markPrice, ZonedDateTime timestamp) {
        logger.debug("Mark Price Updated: {} - {} at {}", state.ticker.getSymbol(), markPrice, timestamp);
        state.markPrice = markPrice.doubleValue();
    }

    protected void fundingRateUpdated(TickerState state, BigDecimal rate, ZonedDateTime timestamp) {
        logger.debug("Funding Rate Updated: {} - {} at {}", state.ticker.getSymbol(), rate, timestamp);
        double annualizedFundingRate = rate.doubleValue();
        state.fundingRate = annualizedFundingRate / (365 * 24 * 100);
        long currentTimestamp = timestamp.toInstant().toEpochMilli();
        if (state.lastFundingTimestamp > 0 && currentTimestamp > state.lastFundingTimestamp) {
            double hours = (currentTimestamp - state.lastFundingTimestamp) / 3600000.0;
            double fundingThisPeriod = (state.markPrice * state.currentPosition.doubleValue()) * (-state.fundingRate)
                    * hours;
            state.fundingAccruedOrPaid += fundingThisPeriod;
            fundingAccruedOrPaidTotal += fundingThisPeriod;
            logger.debug("Funding accrued/paid this period for {}: {} total: {}", state.ticker.getSymbol(),
                    fundingThisPeriod, state.fundingAccruedOrPaid);
            currentAccountBalance += fundingThisPeriod;
        }
        state.lastFundingTimestamp = currentTimestamp;
    }

    protected void applyTopOfBookUpdate(TickerState state, BigDecimal newBid, BigDecimal newAsk, boolean clearBid,
            boolean clearAsk) {
        boolean quoteChanged = clearBid || clearAsk || newBid != null || newAsk != null;
        if (!quoteChanged) {
            return;
        }

        state.marketDataLock.writeLock().lock();
        try {
            if (clearBid) {
                state.bestBidPrice = 0.0;
            }
            if (clearAsk) {
                state.bestAskPrice = Double.MAX_VALUE;
            }
            if (newBid != null) {
                state.bestBidPrice = newBid.doubleValue();
            }
            if (newAsk != null) {
                state.bestAskPrice = newAsk.doubleValue();
            }
            recalculateMidPrice(state);
            evaluatePassiveFillCandidatesLocked(state);
        } finally {
            state.marketDataLock.writeLock().unlock();
        }
    }

    protected void recalculateMidPrice(TickerState state) {
        if (state.bestBidPrice > 0.0 && state.bestAskPrice < Double.MAX_VALUE) {
            state.midPrice = (state.bestBidPrice + state.bestAskPrice) / 2.0;
        } else {
            state.midPrice = 0.0;
        }
    }

    // ========================================================================
    // Order operations
    // ========================================================================

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        order.setOrderEntryTime(getCurrentTime());
        delayRestCall();
        String orderId = UUID.randomUUID().toString();
        order.setOrderId(orderId);

        Ticker ticker = order.getTicker();
        TickerState state = getOrCreateTickerState(ticker);

        orderOperationsLock.writeLock().lock();
        try {

            openOrders.put(orderId, order);
        } finally {
            orderOperationsLock.writeLock().unlock();
        }

        executorService.submit(() -> {
            try {
                state.marketDataLock.readLock().lock();
                try {
                    orderOperationsLock.writeLock().lock();
                    try {
                        if (order.getType() == Type.LIMIT) {
                            if (order.getDirection() == TradeDirection.BUY) {
                                if (order.containsModifier(Modifier.POST_ONLY)
                                        && order.getLimitPrice().doubleValue() >= state.bestAskPrice) {
                                    logger.warn("Limit buy order would cross the best ask price. Cancelling order.");
                                    cancelOrderInternal(state, orderId, order.getClientOrderId(),
                                            CancelReason.POST_ONLY_WOULD_CROSS);
                                    return;
                                }
                                state.openBids.put(orderId, order);
                                OrderStatus status = new OrderStatus(OrderStatus.Status.NEW, orderId, orderId, ticker,
                                        getCurrentTime());
                                fireOrderStatusUpdate(new OrderEvent(order, status));
                                logger.info("Limit buy order placed: {}", order);
                            } else if (order.getDirection() == TradeDirection.SELL) {
                                if (order.containsModifier(Modifier.POST_ONLY)
                                        && order.getLimitPrice().doubleValue() <= state.bestBidPrice) {
                                    logger.warn("Limit sell order would cross the best bid price. Cancelling order.");
                                    cancelOrderInternal(state, orderId, order.getClientOrderId(),
                                            CancelReason.POST_ONLY_WOULD_CROSS);
                                    return;
                                }
                                state.openAsks.put(orderId, order);
                                OrderStatus status = new OrderStatus(OrderStatus.Status.NEW, orderId, orderId, ticker,
                                        getCurrentTime());
                                fireOrderStatusUpdate(new OrderEvent(order, status));
                                logger.info("Limit sell order placed: {}", order);
                            }
                        } else if (order.getType() == Type.MARKET) {
                            // Reject market orders if no market data has arrived yet
                            if (state.bestBidPrice <= 0.0 || state.bestAskPrice >= Double.MAX_VALUE) {
                                logger.warn("Rejecting market order {} — no market data for {}",
                                        orderId, ticker);
                                cancelOrderInternal(state, orderId, order.getClientOrderId(),
                                        CancelReason.NO_MARKET_DATA);
                                return;
                            }
                            if (order.getDirection() == TradeDirection.BUY) {
                                if (fillOrder(state, order, state.bestAskPrice)) {
                                    logger.info("Market buy executed: {}", order);
                                }
                            } else if (order.getDirection() == TradeDirection.SELL) {
                                if (fillOrder(state, order, state.bestBidPrice)) {
                                    logger.info("Market sell executed: {}", order);
                                }
                            }
                        }
                    } finally {
                        orderOperationsLock.writeLock().unlock();
                    }
                } finally {
                    state.marketDataLock.readLock().unlock();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });

        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        order.setOrderEntryTime(getCurrentTime());
        delayRestCall();

        Ticker ticker = order.getTicker();
        TickerState state = getOrCreateTickerState(ticker);

        state.marketDataLock.readLock().lock();
        try {
            orderOperationsLock.writeLock().lock();
            try {
                String orderId = order.getOrderId();
                if (order.getType() == Type.LIMIT) {
                    if (order.getDirection() == TradeDirection.BUY) {
                        if (order.containsModifier(Modifier.POST_ONLY)
                                && order.getLimitPrice().doubleValue() >= state.bestAskPrice) {
                            state.openBids.remove(orderId);
                
                            cancelOrderInternal(state, orderId, order.getClientOrderId(),
                                    CancelReason.POST_ONLY_WOULD_CROSS);
                            return new BrokerRequestResult(false, true,
                                    "Post-only buy would cross best ask; order canceled");
                        }
                        OrderTicket removed = state.openBids.remove(orderId);
                        if (removed == null) {
                            logger.error("No existing buy order found with ID: {} to modify.", orderId);
                            return new BrokerRequestResult(false, true, "404 Order not found: " + orderId);
                        }
            
                        state.openBids.put(orderId, order);
                        OrderStatus status = new OrderStatus(OrderStatus.Status.REPLACED, orderId, orderId, ticker,
                                getCurrentTime());
                        fireOrderStatusUpdate(new OrderEvent(order, status));
                    } else if (order.getDirection() == TradeDirection.SELL) {
                        if (order.containsModifier(Modifier.POST_ONLY)
                                && order.getLimitPrice().doubleValue() <= state.bestBidPrice) {
                            state.openAsks.remove(orderId);
                
                            cancelOrderInternal(state, orderId, order.getClientOrderId(),
                                    CancelReason.POST_ONLY_WOULD_CROSS);
                            return new BrokerRequestResult(false, true,
                                    "Post-only sell would cross best bid; order canceled");
                        }
                        OrderTicket removed = state.openAsks.remove(orderId);
                        if (removed == null) {
                            logger.error("No existing sell order found with ID: {} to modify.", orderId);
                            return new BrokerRequestResult(false, true, "404 Order not found: " + orderId);
                        }
            
                        state.openAsks.put(orderId, order);
                        OrderStatus status = new OrderStatus(OrderStatus.Status.REPLACED, orderId, orderId, ticker,
                                getCurrentTime());
                        fireOrderStatusUpdate(new OrderEvent(order, status));
                    }
                }
            } finally {
                orderOperationsLock.writeLock().unlock();
            }
        } finally {
            state.marketDataLock.readLock().unlock();
        }

        return new BrokerRequestResult();
    }

    @Override
    public synchronized BrokerRequestResult cancelOrder(String orderId) {
        cancelOrderSubmitWithDelay(orderId, true);
        return new BrokerRequestResult();
    }

    @Override
    public synchronized BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        String orderIdToCancel = null;
        orderOperationsLock.readLock().lock();
        try {
            for (OrderTicket order : openOrders.values()) {
                if (clientOrderId.equals(order.getClientOrderId())) {
                    orderIdToCancel = order.getOrderId();
                    break;
                }
            }
        } finally {
            orderOperationsLock.readLock().unlock();
        }
        if (orderIdToCancel == null) {
            logger.warn("No order found with client order ID: {}", clientOrderId);
            return new BrokerRequestResult(false, true, "404 Order not found for client order ID: " + clientOrderId);
        } else {
            cancelOrderSubmitWithDelay(orderIdToCancel, true);
            return new BrokerRequestResult();
        }
    }

    @Override
    public synchronized BrokerRequestResult cancelOrder(OrderTicket order) {
        cancelOrder(order.getOrderId());
        return new BrokerRequestResult();
    }

    @Override
    public synchronized BrokerRequestResult cancelAllOrders(Ticker ticker) {
        TickerState state = tickerStates.get(ticker.getSymbol());
        if (state == null) {
            return new BrokerRequestResult();
        }

        executorService.submit(() -> {
            delayRestCall();
            for (String orderId : new ArrayList<>(state.openBids.keySet())) {
                try {
                    cancelOrderSubmitWithDelay(orderId, false);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
            for (String orderId : new ArrayList<>(state.openAsks.keySet())) {
                try {
                    cancelOrderSubmitWithDelay(orderId, false);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        });
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        executorService.submit(() -> {
            delayRestCall();
            for (TickerState state : tickerStates.values()) {
                for (String orderId : new ArrayList<>(state.openBids.keySet())) {
                    try {
                        cancelOrderSubmitWithDelay(orderId, false);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
                for (String orderId : new ArrayList<>(state.openAsks.keySet())) {
                    try {
                        cancelOrderSubmitWithDelay(orderId, false);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        });
        return new BrokerRequestResult();
    }

    protected void cancelOrderSubmitWithDelay(String orderId, boolean shouldDelay) {
        if (shouldDelay) {
            delayRestCall();
        }

        TickerState state = findTickerStateForOrder(orderId);
        if (state == null) {
            logger.warn("Order {} not found in any ticker state.", orderId);
            return;
        }

        state.marketDataLock.readLock().lock();
        try {
            orderOperationsLock.writeLock().lock();
            try {
                if (state.openBids.containsKey(orderId)) {
                    OrderTicket order = state.openBids.remove(orderId);
                    if (order != null) {
                        cancelOrderInternal(state, orderId, order.getClientOrderId(), CancelReason.USER_CANCELED);
                    }
                    logger.info("Order {} cancelled from bids.", orderId);
                } else if (state.openAsks.containsKey(orderId)) {
                    OrderTicket order = state.openAsks.remove(orderId);
                    if (order != null) {
                        cancelOrderInternal(state, orderId, order.getClientOrderId(), CancelReason.USER_CANCELED);
                    }
                    logger.info("Order {} cancelled from asks.", orderId);
                } else {
                    logger.warn("Order {} not found in bids or asks for ticker {}.", orderId,
                            state.ticker.getSymbol());
                }
            } finally {
                orderOperationsLock.writeLock().unlock();
            }
        } finally {
            state.marketDataLock.readLock().unlock();
        }
    }

    protected TickerState findTickerStateForOrder(String orderId) {
        for (TickerState state : tickerStates.values()) {
            if (state.openBids.containsKey(orderId) || state.openAsks.containsKey(orderId)) {
                return state;
            }
        }
        OrderTicket order = openOrders.get(orderId);
        if (order != null && order.getTicker() != null) {
            return getOrCreateTickerState(order.getTicker());
        }
        return null;
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        cancelOrder(originalOrderId);
        placeOrder(newOrder);
    }

    // ========================================================================
    // Fill logic
    // ========================================================================

    protected boolean fillOrder(TickerState state, OrderTicket order, double price) {
        OrderTicket fillableOrder = null;
        OrderEvent event = null;
        Fill fill = null;
        double fee = 0.0;

        orderOperationsLock.writeLock().lock();
        try {
            String orderId = order.getOrderId();
            OrderTicket trackedOrder = openOrders.get(orderId);
            if (trackedOrder == null) {
                logger.debug("Skipping fill for order {} because it is no longer open.", orderId);
                return false;
            }
            if (trackedOrder.getCurrentStatus() == OrderStatus.Status.FILLED) {
                logger.warn("Skipping duplicate fill for order {} because it is already FILLED.", orderId);
                return false;
            }

            fillableOrder = openOrders.remove(orderId);
            if (fillableOrder == null) {
                logger.debug("Skipping fill for order {} because another thread already claimed it.", orderId);
                return false;
            }


            state.openBids.remove(orderId);
            state.openAsks.remove(orderId);

            BigDecimal remainingSize = BigDecimal.ZERO;
            BigDecimal originalSize = fillableOrder.getSize();
            BigDecimal averageFillPrice = BigDecimal.valueOf(price);
            ZonedDateTime fillTime = getCurrentTime();

            fillableOrder.setFilledPrice(averageFillPrice);
            fillableOrder.setFilledSize(originalSize);
            fillableOrder.setCurrentStatus(OrderStatus.Status.FILLED);
            fillableOrder.setOrderFilledTime(fillTime);

            logger.info("Filling order: {} at price: {} with size: {}", fillableOrder, price, fillableOrder.getSize());

            try {
                updatePosition(state, fillableOrder.getTradeDirection(), fillableOrder.getSize(), price,
                        fillableOrder.getType() != Type.MARKET);
            } catch (Exception e) {
                logger.error(e.getLocalizedMessage(), e);
            }

            OrderStatus orderStatus = new OrderStatus(OrderStatus.Status.FILLED, orderId, orderId, originalSize,
                    remainingSize, averageFillPrice, state.ticker, fillTime);
            orderStatus.setClientOrderId(fillableOrder.getClientOrderId());
            event = new OrderEvent(fillableOrder, orderStatus);

            fee = calcFee(state, price, fillableOrder.getSize().doubleValue(),
                    fillableOrder.getType() != Type.MARKET);

            fill = new Fill();
            fill.setCommission(BigDecimal.valueOf(fee));
            fill.setFillId(UUID.randomUUID().toString());
            fill.setOrderId(orderId);
            fill.setClientOrderId(fillableOrder.getClientOrderId());
            fill.setPrice(averageFillPrice);
            fill.setSide(fillableOrder.getTradeDirection());
            fill.setSize(originalSize);
            fill.setTaker(fillableOrder.getType() == Type.MARKET);
            fill.setTicker(state.ticker);
            fill.setTime(fillTime);

            fillableOrder.setCommission(BigDecimal.valueOf(fee));
            fillableOrder.addFill(fill);
            executedOrders.add(fillableOrder);
        } finally {
            orderOperationsLock.writeLock().unlock();
        }

        if (fillableOrder == null || event == null || fill == null) {
            return false;
        }

        fireFillUpdate(fill);
        fireOrderStatusUpdate(event);
        writeTradeToCsv(fillableOrder, price, fee);
        return true;
    }

    protected void updatePosition(TickerState state, TradeDirection side, BigDecimal orderSize, double price,
            boolean isMaker) {
        BigDecimal size = orderSize;
        double notional = size.doubleValue() * price;

        if (side == TradeDirection.BUY) {
            if (state.currentPosition.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal closingSize = state.currentPosition.negate().min(size);
                double pnl = (state.averageEntryPrice - price) * closingSize.doubleValue();
                state.realizedPnL += pnl;
                currentAccountBalance += pnl;
                state.currentPosition = state.currentPosition.add(closingSize);
                size = size.subtract(closingSize);

                if (state.currentPosition.compareTo(BigDecimal.ZERO) == 0)
                    state.averageEntryPrice = 0;

                if (size.compareTo(BigDecimal.ZERO) > 0) {
                    state.averageEntryPrice = price;
                    state.currentPosition = state.currentPosition.add(size);
                }
            } else {
                state.averageEntryPrice = ((state.averageEntryPrice * state.currentPosition.doubleValue())
                        + (price * size.doubleValue()))
                        / (state.currentPosition.doubleValue() + size.doubleValue());
                state.currentPosition = state.currentPosition.add(size);
            }
        } else { // SELL
            if (state.currentPosition.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal closingSize = state.currentPosition.min(size);
                double pnl = (price - state.averageEntryPrice) * closingSize.doubleValue();
                state.realizedPnL += pnl;
                currentAccountBalance += pnl;
                state.currentPosition = state.currentPosition.subtract(closingSize);
                size = size.subtract(closingSize);

                if (state.currentPosition.compareTo(BigDecimal.ZERO) == 0)
                    state.averageEntryPrice = 0;

                if (size.compareTo(BigDecimal.ZERO) > 0) {
                    state.averageEntryPrice = price;
                    state.currentPosition = state.currentPosition.subtract(size);
                }
            } else {
                state.averageEntryPrice = ((state.averageEntryPrice * -state.currentPosition.doubleValue())
                        + (price * size.doubleValue()))
                        / (-state.currentPosition.doubleValue() + size.doubleValue());
                state.currentPosition = state.currentPosition.subtract(size);
            }
        }

        double fee = calcFee(state, price, orderSize.doubleValue(), isMaker);
        currentAccountBalance += fee;
        totalFees += fee;
        dollarVolume += Math.abs(notional);
        totalOrdersPlaced++;
    }

    protected double calcFee(TickerState state, double price, double size, boolean isMaker) {
        double notional = Math.abs(price * size);
        double feeRate = isMaker ? makerFee : takerFee;
        return notional * feeRate;
    }

    // ========================================================================
    // Passive fill evaluation
    // ========================================================================

    /**
     * Evaluates resting limit orders against the current top-of-book.
     * A resting buy fills when the best ask drops to the limit price or better.
     * A resting sell fills when the best bid rises to the limit price or better.
     * Called under the market-data write lock.
     */
    protected void evaluatePassiveFillCandidatesLocked(TickerState state) {
        for (OrderTicket order : new ArrayList<>(state.openBids.values())) {
            if (order == null || order.getType() != Type.LIMIT || order.getLimitPrice() == null) {
                continue;
            }
            double limitPrice = order.getLimitPrice().doubleValue();
            if (state.bestAskPrice < Double.MAX_VALUE && limitPrice >= state.bestAskPrice) {
                fillOrder(state, order, limitPrice);
            }
        }

        for (OrderTicket order : new ArrayList<>(state.openAsks.values())) {
            if (order == null || order.getType() != Type.LIMIT || order.getLimitPrice() == null) {
                continue;
            }
            double limitPrice = order.getLimitPrice().doubleValue();
            if (state.bestBidPrice > 0.0 && limitPrice <= state.bestBidPrice) {
                fillOrder(state, order, limitPrice);
            }
        }
    }

    /**
     * Evaluates resting limit orders against the trade tape.
     * A resting buy fills when a sell-aggressor trade prints at the bid price or better (lower).
     * A resting sell fills when a buy-aggressor trade prints at the ask price or better (higher).
     */
    protected void evaluateTradeBasedFills(TickerState state, OrderFlow.Side side, double tradePrice) {
        if (side == OrderFlow.Side.SELL) {
            for (OrderTicket order : new ArrayList<>(state.openBids.values())) {
                if (order == null || order.getType() != Type.LIMIT || order.getLimitPrice() == null) {
                    continue;
                }
                double limitPrice = order.getLimitPrice().doubleValue();
                if (tradePrice <= limitPrice) {
                    fillOrder(state, order, limitPrice);
                }
            }
        } else if (side == OrderFlow.Side.BUY) {
            for (OrderTicket order : new ArrayList<>(state.openAsks.values())) {
                if (order == null || order.getType() != Type.LIMIT || order.getLimitPrice() == null) {
                    continue;
                }
                double limitPrice = order.getLimitPrice().doubleValue();
                if (tradePrice >= limitPrice) {
                    fillOrder(state, order, limitPrice);
                }
            }
        }
    }

    protected double clamp(double value, double minValue, double maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    // ========================================================================
    // Cancel internals
    // ========================================================================

    protected void cancelOrderInternal(TickerState state, String orderId, String clientOrderId, CancelReason reason) {
        try {
            OrderTicket order = openOrders.remove(orderId);

            state.openBids.remove(orderId);
            state.openAsks.remove(orderId);

            OrderStatus orderStatus = new OrderStatus(OrderStatus.Status.CANCELED, orderId, orderId, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, state.ticker, getCurrentTime());
            orderStatus.setClientOrderId(clientOrderId);
            orderStatus.setCancelReason(reason);
            fireOrderStatusUpdate(new OrderEvent(order, orderStatus));
        } catch (Exception e) {
            logger.error("Error cancelling order {}: {}", orderId, e.getMessage(), e);
        }
    }

    // ========================================================================
    // Event firing
    // ========================================================================

    protected void fireOrderStatusUpdate(OrderEvent event) {
        executorService.submit(() -> {
            try {
                delayWebSocketCall();
                super.fireOrderEvent(event);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    protected void fireFillUpdate(Fill fill) {
        executorService.submit(() -> {
            try {
                delayWebSocketCall();
                super.fireFillEvent(fill);
            } catch (Exception e) {
                logger.error(e.getLocalizedMessage(), e);
            }
        });
    }

    protected void fireAccountUpdate() {
        executorService.submit(() -> {
            try {
                delayWebSocketCall();
                super.fireAccountEquityUpdated(getNetAccountValue());
                super.fireAvailableFundsUpdated(currentAccountBalance);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    // ========================================================================
    // Query methods
    // ========================================================================

    @Override
    public List<OrderTicket> getOpenOrders() {
        delayRestCall();
        List<OrderTicket> result = new ArrayList<>();
        for (TickerState state : tickerStates.values()) {
            result.addAll(state.openBids.values());
            result.addAll(state.openAsks.values());
        }
        return result;
    }

    public List<OrderTicket> getOpenOrders(Ticker ticker) {
        delayRestCall();
        TickerState state = tickerStates.get(ticker.getSymbol());
        if (state == null) {
            return new ArrayList<>();
        }
        List<OrderTicket> result = new ArrayList<>();
        result.addAll(state.openBids.values());
        result.addAll(state.openAsks.values());
        return result;
    }

    @Override
    public List<Position> getAllPositions() {
        delayRestCall();
        List<Position> positions = new ArrayList<>();
        for (TickerState state : tickerStates.values()) {
            if (!state.currentPosition.equals(BigDecimal.ZERO)) {
                Side side = state.currentPosition.compareTo(BigDecimal.ZERO) > 0 ? Side.LONG : Side.SHORT;
                positions.add(new Position(state.ticker, side, state.currentPosition,
                        BigDecimal.valueOf(state.averageEntryPrice), Position.Status.OPEN));
            }
        }
        return positions;
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        delayRestCall();
        return openOrders.get(orderId);
    }

    public double getUnrealizedPnL() {
        double total = 0.0;
        for (TickerState state : tickerStates.values()) {
            if (state.markPrice != 0 && state.currentPosition.doubleValue() != 0) {
                total += (state.markPrice - state.averageEntryPrice) * state.currentPosition.doubleValue();
            }
        }
        return total;
    }

    public double getNetAccountValue() {
        return currentAccountBalance + getUnrealizedPnL();
    }

    public Set<String> getActiveTickers() {
        return Collections.unmodifiableSet(tickerStates.keySet());
    }

    @Override
    public String getNextOrderId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public BrokerStatus getBrokerStatus() {
        return BrokerStatus.OK;
    }

    // ========================================================================
    // Latency simulation
    // ========================================================================

    protected void delayRestCall() {
        try {
            long latency = latencyModel.getRestLatencyMsMin() + (long) (Math.random()
                    * (latencyModel.getRestLatencyMsMax() - latencyModel.getRestLatencyMsMin()));
            if (latency > 0) {
                Thread.sleep(latency);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void delayWebSocketCall() {
        try {
            long latency = latencyModel.getWsLatencyMsMin()
                    + (long) (Math.random() * (latencyModel.getWsLatencyMsMax() - latencyModel.getWsLatencyMsMin()));
            if (latency > 0) {
                Thread.sleep(latency);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========================================================================
    // File I/O
    // ========================================================================

    private void writeCurrentBalanceToFile(String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(String.valueOf(getNetAccountValue()));
        } catch (IOException e) {
            logger.error("Failed to write current balance to file.", e);
        }
    }

    protected void writeTradeToCsv(OrderTicket order, double price, double fee) {
        executorService.submit(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFilePath, true))) {
                if (!firstTradeWrittenToFile) {
                    writer.write("####");
                    writer.newLine();
                    firstTradeWrittenToFile = true;
                }
                String asset = order.getTicker() != null ? order.getTicker().getSymbol() : "UNKNOWN";
                String csvLine = String.format("%s,%s,%s,%.5f,%s,%s,%s,%.5f,%.8f,%d", asset, order.getOrderId(),
                        order.getDirection(), order.getSize(), order.getType(),
                        order.getOrderEntryTime().format(DateTimeFormatter.ISO_INSTANT),
                        order.getOrderFilledTime().format(DateTimeFormatter.ISO_INSTANT), price, fee,
                        System.currentTimeMillis());
                writer.write(csvLine);
                writer.newLine();
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage(), e);
            }
        });
    }

    protected String generateCsvFilename() {
        ZonedDateTime now = getCurrentTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
        String timestamp = now.format(formatter);
        String filename = String.format("%s-MultiTicker-%s-Trades.csv", timestamp,
                sanitizeFilenameComponent(quoteEngine.getDataProviderName()));
        return prependOutputDir(filename);
    }

    protected String generateBalanceFilename() {
        String filename = String.format("MultiTicker-%s-paperbroker-startingbalance.txt",
                sanitizeFilenameComponent(quoteEngine.getDataProviderName()));
        return prependOutputDir(filename);
    }

    private String prependOutputDir(String filename) {
        if (outputDir == null || outputDir.isBlank()) {
            return filename;
        }
        java.nio.file.Path outputPath = Paths.get(outputDir);
        if (Files.exists(outputPath) && !Files.isDirectory(outputPath)) {
            throw new IllegalStateException("Output path exists but is not a directory: " + outputDir);
        }
        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create output directory: " + outputDir, e);
        }
        return outputPath.resolve(filename).toString();
    }

    protected String sanitizeFilenameComponent(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    // ========================================================================
    // Unsupported operations (matching PaperBroker)
    // ========================================================================

    @Override
    public void addBrokerErrorListener(BrokerErrorListener listener) {
        throw new UnsupportedOperationException("Not yet supported in MultiTickerPaperBroker");
    }

    @Override
    public void addTimeUpdateListener(TimeUpdatedListener listener) {
        throw new UnsupportedOperationException("Not yet supported in MultiTickerPaperBroker");
    }

    @Override
    public ComboTicker buildComboTicker(Ticker ticker1, Ticker ticker2) {
        throw new UnsupportedOperationException("Not yet supported in MultiTickerPaperBroker");
    }

    @Override
    public ComboTicker buildComboTicker(Ticker ticker1, int ratio1, Ticker ticker2, int ratio2) {
        throw new UnsupportedOperationException("Not yet supported in MultiTickerPaperBroker");
    }
}
