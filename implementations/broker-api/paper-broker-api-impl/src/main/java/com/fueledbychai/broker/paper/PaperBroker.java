package com.fueledbychai.broker.paper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.fueledbychai.broker.order.OrderStatus.Status;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Modifier;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.ComboTicker;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.time.TimeUpdatedListener;

public class PaperBroker extends AbstractBasicBroker implements Level1QuoteListener, OrderFlowListener {

    protected Logger logger = LoggerFactory.getLogger(PaperBroker.class);

    private final ExecutorService executorService = Executors.newCachedThreadPool(); // Thread pool with cached threads
    protected PaperBrokerLatency latencyModel;

    protected String asset;

    private Timer accountUpdateTimer = new Timer(true); // Timer to schedule account updates
    private volatile boolean connected = false;
    protected double makerFee = 0.005 / 100.0; // 0.005% maker rebate
    protected double takerFee = -0.03 / 100.0; // 0.03% taker fee

    protected double startingAccountBalance = 100000.0; // Starting account balance for paper trading
    protected double currentAccountBalance = startingAccountBalance; // Current account balance

    protected ConcurrentHashMap<String, OrderTicket> openBids = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<String, OrderTicket> openAsks = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<Ticker, InstrumentState> instrumentStates = new ConcurrentHashMap<>();
    protected Set<Ticker> trackedTickers = ConcurrentHashMap.newKeySet();

    protected double bestBidPrice = 0.0; // Best bid price
    protected double bestAskPrice = Double.MAX_VALUE; // Best ask price
    protected double midPrice = 0.0; // Mid price (average of best bid and ask

    protected double markPrice = 0.0;
    protected double fundingRate = 0.0; // Funding rate for the asset
    protected double fundingAccruedOrPaid = 0.0; // Total funding accrued or paid
    protected long lastFundingTimestamp = 0; // Last funding timestamp

    // Separate locks to prevent deadlock and ensure proper order of operations
    private final ReadWriteLock marketDataLock = new ReentrantReadWriteLock();
    private final ReadWriteLock orderOperationsLock = new ReentrantReadWriteLock();

    // Market data processing takes write lock to have exclusive access during fill
    // checking
    // Order operations take read lock on market data to allow concurrent order ops
    // when no fills are being checked

    protected BigDecimal currentPosition = BigDecimal.ZERO; // Current position size
    protected double averageEntryPrice = 0.0; // Average entry price for the current position
    protected double realizedPnL = 0.0; // Closed profit and loss
    protected double unrealizedPnL = 0.0; // Unrealized profit and loss
    protected double totalPnL = 0.0; // Total profit and loss
    protected double totalFees = 0.0; // Total fees paid

    protected boolean firstTradeWrittenToFile = false;

    protected Map<String, OrderTicket> openOrders = new ConcurrentHashMap<>();
    // Map to hold open orders by orderId
    // insertion
    protected final Set<OrderTicket> executedOrders = java.util.Collections
            .synchronizedSet(new TreeSet<OrderTicket>(new java.util.Comparator<OrderTicket>() {
                @Override
                public int compare(OrderTicket o1, OrderTicket o2) {
                    return o2.getOrderEntryTime().compareTo(o1.getOrderEntryTime());
                }
            }));

    // protected IOrderBook orderBook; // Order book for managing market data

    protected IPaperBrokerStatus brokerStatus = new PaperBrokerStatus(); // Broker status for reporting

    // protected ISystemConfig systemConfig; // System configuration for the broker
    protected Ticker ticker;

    protected QuoteEngine quoteEngine;

    protected int totalOrdersPlaced = 0; // Total number of orders placed
    protected double dollarVolume = 0.0; // Total value of orders placed
    protected String csvFilePath = null;

    private final long timeWindowMillis = 6000; // 6 seconds
    private final double dislocationMultiplier = 7.5; // Multiplier for dislocation threshold

    public PaperBroker(QuoteEngine quoteEngine, Ticker ticker, PaperBrokerCommission commission,
            PaperBrokerLatency latencyModel, double startingBalance) {
        this(quoteEngine, Collections.singleton(ticker), commission, latencyModel, startingBalance);
    }

    public PaperBroker(QuoteEngine quoteEngine, Collection<Ticker> tickers, PaperBrokerCommission commission,
            PaperBrokerLatency latencyModel, double startingBalance) {
        if (tickers == null || tickers.isEmpty()) {
            throw new IllegalArgumentException("At least 1 ticker is required");
        }
        this.latencyModel = Objects.requireNonNull(latencyModel, "latencyModel is required");
        this.quoteEngine = Objects.requireNonNull(quoteEngine, "quoteEngine is required");
        this.makerFee = commission.getMakerFeeBps() / 10000.0; // Convert bps to decimal
        this.takerFee = commission.getTakerFeeBps() / 10000.0; // Convert bps to decimal
        this.startingAccountBalance = startingBalance;
        this.currentAccountBalance = startingBalance;

        Ticker primaryTicker = null;
        for (Ticker trackedTicker : tickers) {
            if (trackedTicker == null) {
                continue;
            }
            if (primaryTicker == null) {
                primaryTicker = trackedTicker;
            }
            registerTrackedTicker(trackedTicker);
        }
        if (primaryTicker == null) {
            throw new IllegalArgumentException("At least 1 non-null ticker is required");
        }
        this.ticker = primaryTicker;
        InstrumentState primaryState = getOrCreateState(primaryTicker);
        syncLegacyFieldsFromState(primaryTicker, primaryState);
    }

    private static class SpreadEntry {
        double spread;
        long timestamp;

        SpreadEntry(double spread, long timestamp) {
            this.spread = spread;
            this.timestamp = timestamp;
        }
    }

    private static class InstrumentState {
        private double bestBidPrice = 0.0;
        private double bestAskPrice = Double.MAX_VALUE;
        private double midPrice = 0.0;
        private double markPrice = 0.0;
        private double fundingRate = 0.0;
        private double fundingAccruedOrPaid = 0.0;
        private long lastFundingTimestamp = 0;
        private BigDecimal currentPosition = BigDecimal.ZERO;
        private double averageEntryPrice = 0.0;
        private double realizedPnL = 0.0;
        private final Deque<SpreadEntry> spreadHistory = new ArrayDeque<>();
    }

    protected void registerTrackedTicker(Ticker trackedTicker) {
        getOrCreateState(trackedTicker);
        trackedTickers.add(trackedTicker);
    }

    protected InstrumentState getOrCreateState(Ticker trackedTicker) {
        if (trackedTicker == null) {
            return instrumentStates.computeIfAbsent(this.ticker, key -> new InstrumentState());
        }
        return instrumentStates.computeIfAbsent(trackedTicker, key -> new InstrumentState());
    }

    protected InstrumentState getPrimaryState() {
        return getOrCreateState(ticker);
    }

    protected void syncLegacyFieldsFromState(Ticker updatedTicker, InstrumentState state) {
        if (state == null || ticker == null || !ticker.equals(updatedTicker)) {
            return;
        }
        bestBidPrice = state.bestBidPrice;
        bestAskPrice = state.bestAskPrice;
        midPrice = state.midPrice;
        markPrice = state.markPrice;
        fundingRate = state.fundingRate;
        fundingAccruedOrPaid = state.fundingAccruedOrPaid;
        lastFundingTimestamp = state.lastFundingTimestamp;
        currentPosition = state.currentPosition;
        averageEntryPrice = state.averageEntryPrice;
        realizedPnL = state.realizedPnL;
    }

    protected void ensureTickerSubscription(Ticker trackedTicker) {
        if (trackedTicker == null) {
            return;
        }
        getOrCreateState(trackedTicker);
        boolean isNewTicker = trackedTickers.add(trackedTicker);
        if (!connected || !isNewTicker) {
            return;
        }
        quoteEngine.subscribeLevel1(trackedTicker, this);
        quoteEngine.subscribeOrderFlow(trackedTicker, this);
    }

    protected Ticker resolveTickerBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return ticker;
        }
        for (Ticker trackedTicker : trackedTickers) {
            if (trackedTicker != null && symbol.equals(trackedTicker.getSymbol())) {
                return trackedTicker;
            }
        }
        return ticker;
    }

    @Override
    public String getBrokerName() {
        return "PaperBroker-" + quoteEngine.getDataProviderName();
    }

    protected void startAccountUpdateTask() {
        if (connected) {
            logger.info("PaperBroker already connected, skipping duplicate connect.");
            return;
        }
        if (accountUpdateTimer != null) {
            accountUpdateTimer.cancel();
        }
        accountUpdateTimer = new Timer(true);
        logger.warn("PaperBroker @PostConstruct startAccountUpdateTask called: {}", System.identityHashCode(this));
        String symbolForFiles = trackedTickers.size() > 1 ? "MULTI" : ticker.getSymbol();
        String exchangeName = (ticker.getExchange() == null) ? quoteEngine.getDataProviderName()
                : ticker.getExchange().getExchangeName();
        // Read starting balance from file
        String balanceFilePath = generateBalanceFilename(symbolForFiles, exchangeName);
        File balanceFile = new File(balanceFilePath);
        if (balanceFile.exists()) {
            try {
                String balanceContent = new String(Files.readAllBytes(Paths.get(balanceFilePath)));
                startingAccountBalance = Double.parseDouble(balanceContent.trim());
                currentAccountBalance = startingAccountBalance;
                logger.info("Starting account balance read from file: {}", startingAccountBalance);
            } catch (Exception e) {
                logger.error("Failed to read starting balance from file. Using default value." + startingAccountBalance,
                        e);
            }
        }

        csvFilePath = generateCsvFilename(symbolForFiles, exchangeName);
        asset = symbolForFiles;
        brokerStatus.setAsset(asset);
        brokerStatus.setOpenOrders(openOrders.values()); // Set the open orders in the broker status
        brokerStatus.setExecutedOrders(executedOrders);

        accountUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    fireAccountUpdate(); // Call the method once per second
                } catch (Exception e) {
                    logger.error("Error during account update: {}", e.getMessage(), e);
                }

                try {
                    updateStatus(); // Update the broker status every second
                } catch (Exception e) {
                    logger.error("Error updating broker status: {}", e.getMessage(), e);
                }

                try {
                    writeCurrentBalanceToFile(balanceFilePath); // Write the current balance to the file
                } catch (Exception e) {
                    logger.error("Error writing current balance to file: {}", e.getMessage(), e);
                }
            }
        }, 0, 1000); // Schedule with a delay of 0 and period of 1000ms (1 second)
        for (Ticker trackedTicker : new HashSet<>(trackedTickers)) {
            quoteEngine.subscribeLevel1(trackedTicker, this);
            quoteEngine.subscribeOrderFlow(trackedTicker, this);
        }
        connected = true;
    }

    private void writeCurrentBalanceToFile(String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(String.valueOf(getNetAccountValue()));
        } catch (IOException e) {
            logger.error("Failed to write current balance to file.", e);
        }
    }

    public void setBrokerStatus(IPaperBrokerStatus brokerStatus) {
        this.brokerStatus = brokerStatus;
    }

    public void markPriceUpdated(String symbol, BigDecimal markPrice, ZonedDateTime timestamp) {
        markPriceUpdated(resolveTickerBySymbol(symbol), markPrice, timestamp);
    }

    public void markPriceUpdated(Ticker trackedTicker, BigDecimal markPrice, ZonedDateTime timestamp) {
        if (trackedTicker == null || markPrice == null) {
            return;
        }
        logger.info("Mark Price Updated: {} - {} at {}", trackedTicker.getSymbol(), markPrice, timestamp);
        InstrumentState state = getOrCreateState(trackedTicker);
        state.markPrice = markPrice.doubleValue();
        syncLegacyFieldsFromState(trackedTicker, state);
    }

    public void fundingRateUpdated(String symbol, BigDecimal rate, ZonedDateTime timestamp) {
        fundingRateUpdated(resolveTickerBySymbol(symbol), rate, timestamp);
    }

    public void fundingRateUpdated(Ticker trackedTicker, BigDecimal rate, ZonedDateTime timestamp) {
        if (trackedTicker == null || rate == null || timestamp == null) {
            return;
        }
        InstrumentState state = getOrCreateState(trackedTicker);
        logger.info("Funding Rate Updated: {} - {} at {}", trackedTicker.getSymbol(), state.fundingRate, timestamp);
        double annualizedFundingRate = rate.doubleValue();
        state.fundingRate = annualizedFundingRate / (365 * 24 * 100); // Convert APR to hourly rate
        long currentTimestamp = timestamp.toInstant().toEpochMilli();
        if (state.lastFundingTimestamp > 0 && currentTimestamp > state.lastFundingTimestamp) {
            double hours = (currentTimestamp - state.lastFundingTimestamp) / 3600000.0;
            // Funding is paid/collected continuously: funding = size * fundingRate * hours
            double fundingThisPeriod = (state.markPrice * state.currentPosition.doubleValue()) * (-state.fundingRate) * hours;
            state.fundingAccruedOrPaid += fundingThisPeriod;
            logger.info("Funding accrued/paid this period: {} total funding: {}", fundingThisPeriod,
                    state.fundingAccruedOrPaid);
            currentAccountBalance += fundingThisPeriod; // Update account balance with funding
        }
        state.lastFundingTimestamp = currentTimestamp;
        syncLegacyFieldsFromState(trackedTicker, state);
    }

    @Override
    protected void onDisconnect() {
        connected = false;
        try {
            if (accountUpdateTimer != null) {
                accountUpdateTimer.cancel();
            }
        } catch (Exception e) {
            logger.error("Error cancelling account update timer", e);
        }
        for (Ticker trackedTicker : new HashSet<>(trackedTickers)) {
            try {
                quoteEngine.unsubscribeLevel1(trackedTicker, this);
                quoteEngine.unsubscribeOrderFlow(trackedTicker, this);
            } catch (Exception e) {
                logger.warn("Error unsubscribing from market data for {}", trackedTicker, e);
            }
        }
        executorService.shutdownNow();
    }

    @Override
    public synchronized BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        String orderIdToCancel = null;
        for (OrderTicket order : openOrders.values()) {
            if (clientOrderId.equals(order.getClientOrderId())) {
                orderIdToCancel = order.getOrderId();
                break;
            }
        }
        if (orderIdToCancel == null) {
            logger.warn("No order found with client order ID: {}", clientOrderId);
            return new BrokerRequestResult(false, true, "404 Order not found for client order ID: " + clientOrderId);
        } else {
            cancelOrderSubmitWithDelay(orderIdToCancel, true); // Call the method with delay
            return new BrokerRequestResult();
        }
    }

    @Override
    public synchronized BrokerRequestResult cancelAllOrders(Ticker ticker) {
        delayRestCall(); // Simulate network delay
        Set<String> orderIdsToCancel = new HashSet<>();
        for (OrderTicket order : openBids.values()) {
            if (ticker == null || ticker.equals(order.getTicker())) {
                orderIdsToCancel.add(order.getOrderId());
            }
        }
        for (OrderTicket order : openAsks.values()) {
            if (ticker == null || ticker.equals(order.getTicker())) {
                orderIdsToCancel.add(order.getOrderId());
            }
        }
        for (String orderId : orderIdsToCancel) {
            try {
                cancelOrderSubmitWithDelay(orderId, false);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        return new BrokerRequestResult();
    }

    @Override
    public synchronized BrokerRequestResult cancelOrder(String orderId) {

        cancelOrderSubmitWithDelay(orderId, true); // Call the method with no delay
        return new BrokerRequestResult();
    }

    public void cancelOrderSubmitWithDelay(String orderId, boolean shouldDelay) {

        if (shouldDelay) {
            delayRestCall(); // Simulate network delay
        }
        // Take read lock on market data (allows concurrent cancels, but waits for fill
        // processing)
        marketDataLock.readLock().lock();
        try {
            orderOperationsLock.writeLock().lock();
            try {
                if (openBids.containsKey(orderId)) {
                    OrderTicket order = openBids.remove(orderId); // Remove the order from open bids
                    if (order != null) {
                        cancelOrder(orderId, order.getClientOrderId(), CancelReason.USER_CANCELED); // Notify listeners
                                                                                                    // of cancellation
                    }
                    logger.info("Order {} cancelled from bids.", orderId);
                } else if (openAsks.containsKey(orderId)) {
                    OrderTicket order = openAsks.remove(orderId); // Remove the order from open asks
                    if (order != null) {
                        cancelOrder(orderId, order.getClientOrderId(), CancelReason.USER_CANCELED); // Notify listeners
                                                                                                    // of cancellation
                    }
                    logger.info("Order {} cancelled from asks.", orderId);
                } else {
                    logger.warn("Order {} not found in either bids or asks.", orderId);
                }
            } finally {
                orderOperationsLock.writeLock().unlock();
            }
        } finally {
            marketDataLock.readLock().unlock();
        }
    }

    public List<OrderTicket> getOpenOrders(Ticker ticker) {
        delayRestCall(); // Simulate network delay
        List<OrderTicket> openOrders = new ArrayList<>(); // List to hold open orders
        // Add all open bids to the list
        for (OrderTicket order : openBids.values()) {
            if (ticker == null || order.getTicker().equals(ticker)) {
                openOrders.add(order);
            }
        }
        // Add all open asks to the list
        for (OrderTicket order : openAsks.values()) {
            if (ticker == null || order.getTicker().equals(ticker)) {
                openOrders.add(order);
            }
        }

        return openOrders; // Return the list of open orders
    }

    @Override
    public List<Position> getAllPositions() {
        delayRestCall(); // Simulate network delay
        List<Position> positions = new ArrayList<>(); // List to hold position info
        for (Map.Entry<Ticker, InstrumentState> entry : instrumentStates.entrySet()) {
            InstrumentState state = entry.getValue();
            if (state.currentPosition.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Side side = state.currentPosition.compareTo(BigDecimal.ZERO) > 0 ? Side.LONG : Side.SHORT;
            Position position = new Position(entry.getKey(), side, state.currentPosition,
                    BigDecimal.valueOf(state.averageEntryPrice), Position.Status.OPEN);
            positions.add(position);
        }

        return positions; // Return the list of positions

    }

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        if (order == null || order.getOrderId() == null || order.getOrderId().isBlank()) {
            return new BrokerRequestResult(false, true, "400 Invalid order");
        }
        if (order.getTicker() == null) {
            return new BrokerRequestResult(false, true, "400 Order ticker is required");
        }
        ensureTickerSubscription(order.getTicker());
        InstrumentState state = getOrCreateState(order.getTicker());

        order.setOrderEntryTime(getCurrentTime());
        delayRestCall(); // Simulate network delay

        // Take read lock on market data (allows concurrent modifies, but waits for
        // fill processing)
        marketDataLock.readLock().lock();
        try {
            orderOperationsLock.writeLock().lock();
            try {
                String orderId = order.getOrderId();
                if (order.getType() == Type.LIMIT) {
                    if (order.getLimitPrice() == null) {
                        return new BrokerRequestResult(false, true, "400 Limit price is required");
                    }
                    if (order.getDirection() == TradeDirection.BUY) {
                        if (order.containsModifier(Modifier.POST_ONLY)
                                && order.getLimitPrice().doubleValue() >= state.bestAskPrice) {
                            logger.warn("Limit buy order would cross the best ask price. Cancelling order.");
                            openBids.remove(orderId);
                            cancelOrder(orderId, order.getClientOrderId(), CancelReason.POST_ONLY_WOULD_CROSS);
                            return new BrokerRequestResult(false, true,
                                    "Post-only buy would cross best ask; order canceled");
                        }
                        OrderTicket removed = openBids.remove(orderId); // Remove existing order
                        if (removed == null) {
                            logger.error("No existing buy order found with ID: {} to modify.", orderId);
                            return new BrokerRequestResult(false, true, "404 Order not found: " + orderId);
                        }
                        logger.info("Limit buy order modified: {}", order);
                        openBids.put(orderId, order);
                        openOrders.put(orderId, order);
                        OrderStatus status = new OrderStatus(Status.REPLACED, orderId, orderId, order.getTicker(),
                                getCurrentTime());
                        OrderEvent event = new OrderEvent(order, status);
                        fireOrderStatusUpdate(event);
                    } else if (order.getDirection() == TradeDirection.SELL) {
                        if (order.containsModifier(Modifier.POST_ONLY)
                                && order.getLimitPrice().doubleValue() <= state.bestBidPrice) {
                            logger.warn("Limit sell order would cross the best bid price. Cancelling order.");
                            openAsks.remove(orderId);
                            cancelOrder(orderId, order.getClientOrderId(), CancelReason.POST_ONLY_WOULD_CROSS);
                            return new BrokerRequestResult(false, true,
                                    "Post-only sell would cross best bid; order canceled");
                        }
                        OrderTicket removed = openAsks.remove(orderId); // Remove existing order
                        if (removed == null) {
                            logger.error("No existing sell order found with ID: {} to modify.", orderId);
                            return new BrokerRequestResult(false, true, "404Order not found: " + orderId);
                        }
                        logger.info("Limit sell order modified: {}", order);
                        openAsks.put(orderId, order);
                        openOrders.put(orderId, order);
                        OrderStatus status = new OrderStatus(Status.REPLACED, orderId, orderId, order.getTicker(),
                                getCurrentTime());
                        OrderEvent event = new OrderEvent(order, status);
                        fireOrderStatusUpdate(event);
                    }
                } else {
                    return new BrokerRequestResult(false, true, "400 Only LIMIT orders can be modified");
                }
            } finally {
                orderOperationsLock.writeLock().unlock();
            }
        } finally {
            marketDataLock.readLock().unlock();
        }

        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        if (order == null) {
            return new BrokerRequestResult(false, true, "400 Order cannot be null");
        }
        if (order.getTicker() == null) {
            return new BrokerRequestResult(false, true, "400 Order ticker is required");
        }
        ensureTickerSubscription(order.getTicker());
        InstrumentState state = getOrCreateState(order.getTicker());
        order.setOrderEntryTime(getCurrentTime());
        delayRestCall(); // Simulate network delay
        String orderId = System.currentTimeMillis() + "-" + (int) (Math.random() * 10000); // Generate a unique order ID
        order.setOrderId(orderId); // Set the generated order ID
        openOrders.put(orderId, order); // Add the order to open orders
        // Take read lock on market data (allows concurrent orders, but waits for fill
        // processing)
        marketDataLock.readLock().lock();
        try {
            orderOperationsLock.writeLock().lock();
            try {
                if (order.getType() == Type.LIMIT) {
                    if (order.getLimitPrice() == null) {
                        cancelOrder(orderId, order.getClientOrderId(), CancelReason.UNKNOWN);
                        return new BrokerRequestResult(false, true, "400 Limit price is required");
                    }
                    if (order.getDirection() == TradeDirection.BUY) {
                        if (order.containsModifier(Modifier.POST_ONLY)
                                && order.getLimitPrice().doubleValue() >= state.bestAskPrice) {
                            logger.warn("Limit buy order would cross the best ask price. Cancelling order.");
                            cancelOrder(orderId, order.getClientOrderId(), CancelReason.POST_ONLY_WOULD_CROSS);
                            return new BrokerRequestResult(false, true, "Post-only buy would cross best ask");
                        }
                        openBids.put(orderId, order);
                    } else if (order.getDirection() == TradeDirection.SELL) {
                        if (order.containsModifier(Modifier.POST_ONLY)
                                && order.getLimitPrice().doubleValue() <= state.bestBidPrice) {
                            logger.warn("Limit sell order would cross the best bid price. Cancelling order.");
                            cancelOrder(orderId, order.getClientOrderId(), CancelReason.POST_ONLY_WOULD_CROSS);
                            return new BrokerRequestResult(false, true, "Post-only sell would cross best bid");
                        }
                        openAsks.put(orderId, order);
                    } else {
                        cancelOrder(orderId, order.getClientOrderId(), CancelReason.UNKNOWN);
                        return new BrokerRequestResult(false, true, "400 Unsupported trade direction");
                    }

                    OrderStatus status = new OrderStatus(Status.NEW, orderId, orderId, order.getTicker(),
                            getCurrentTime());
                    status.setClientOrderId(order.getClientOrderId());
                    OrderEvent event = new OrderEvent(order, status);
                    fireOrderStatusUpdate(event);
                    logger.info("Limit order placed: {}", order);
                } else if (order.getType() == Type.MARKET) {
                    double executionPrice;
                    if (order.getDirection() == TradeDirection.BUY) {
                        executionPrice = state.bestAskPrice;
                    } else if (order.getDirection() == TradeDirection.SELL) {
                        executionPrice = state.bestBidPrice;
                    } else {
                        cancelOrder(orderId, order.getClientOrderId(), CancelReason.UNKNOWN);
                        return new BrokerRequestResult(false, true, "400 Unsupported trade direction");
                    }
                    if (!Double.isFinite(executionPrice) || executionPrice <= 0 || executionPrice == Double.MAX_VALUE) {
                        cancelOrder(orderId, order.getClientOrderId(), CancelReason.UNKNOWN);
                        return new BrokerRequestResult(false, true, "503 Market data unavailable for market order");
                    }
                    fillOrder(order, executionPrice, order.getSize(), false);
                    logger.info("Market order executed: {}", order);
                } else {
                    cancelOrder(orderId, order.getClientOrderId(), CancelReason.UNKNOWN);
                    return new BrokerRequestResult(false, true, "400 Unsupported order type");
                }
            } finally {
                orderOperationsLock.writeLock().unlock();
            }
        } finally {
            marketDataLock.readLock().unlock();
        }

        return new BrokerRequestResult();

    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        return cancelAllOrders((Ticker) null);
    }

    public double getUnrealizedPnL() {
        double totalUnrealized = 0.0;
        for (InstrumentState state : instrumentStates.values()) {
            if (state.markPrice == 0 || state.currentPosition.doubleValue() == 0) {
                continue;
            }
            totalUnrealized += (state.markPrice - state.averageEntryPrice) * state.currentPosition.doubleValue();
        }
        return totalUnrealized;
    }

    public double getNetAccountValue() {
        return currentAccountBalance + getUnrealizedPnL(); // Calculate net account value
    }

    protected boolean isSpreadDislocated() {
        InstrumentState state = getPrimaryState();
        double spread = state.bestAskPrice - state.bestBidPrice;
        if (state.midPrice == 0) {
            return false; // Avoid division by zero if midPrice is not initialized
        }

        long currentTime = System.currentTimeMillis();

        synchronized (state.spreadHistory) {
            // Remove outdated entries from the spread history, but keep a minimum of 20
            // items
            while (state.spreadHistory.size() > 20 && !state.spreadHistory.isEmpty()
                    && (currentTime - state.spreadHistory.peekFirst().timestamp > timeWindowMillis)) {
                state.spreadHistory.removeFirst();
            }

            // Add the current spread to the history
            state.spreadHistory.addLast(new SpreadEntry(spread, currentTime));

            // Calculate the moving average of spreads within the time window
            double movingAverageSpread = state.spreadHistory.stream().filter(entry -> entry != null)
                    .mapToDouble(entry -> entry.spread).average().orElse(0.0);

            // Determine if the current spread exceeds the dislocation threshold
            double spreadThreshold = movingAverageSpread * dislocationMultiplier;

            if (spread > spreadThreshold) {
                logger.warn("Spread dislocation detected: {} > {}", spread, spreadThreshold);
                logger.warn("Best Bid: {}, Best Ask: {}, Mid Price: {}", state.bestBidPrice, state.bestAskPrice,
                        state.midPrice);
            }

            return spread > spreadThreshold;
        }
    }

    public void askUpdated(BigDecimal newAsk, boolean isTrade) {
        askUpdated(ticker, newAsk, isTrade, null);
    }

    public void askUpdated(Ticker trackedTicker, BigDecimal newAsk, boolean isTrade, BigDecimal tradeSize) {
        if (trackedTicker == null || newAsk == null) {
            return;
        }
        ensureTickerSubscription(trackedTicker);
        if (!isTrade) {
            InstrumentState state = getOrCreateState(trackedTicker);
            state.bestAskPrice = newAsk.doubleValue();
            state.midPrice = (state.bestBidPrice + state.bestAskPrice) / 2.0;
            syncLegacyFieldsFromState(trackedTicker, state);
        }
        checkBidsFills(trackedTicker, newAsk.doubleValue(), isTrade, tradeSize);
    }

    public void bidUpdated(BigDecimal newBid, boolean isTrade) {
        bidUpdated(ticker, newBid, isTrade, null);
    }

    public void bidUpdated(Ticker trackedTicker, BigDecimal newBid, boolean isTrade, BigDecimal tradeSize) {
        if (trackedTicker == null || newBid == null) {
            return;
        }
        ensureTickerSubscription(trackedTicker);
        if (!isTrade) {
            InstrumentState state = getOrCreateState(trackedTicker);
            state.bestBidPrice = newBid.doubleValue();
            state.midPrice = (state.bestBidPrice + state.bestAskPrice) / 2.0;
            syncLegacyFieldsFromState(trackedTicker, state);
        }
        checkAsksFills(trackedTicker, newBid.doubleValue(), isTrade, tradeSize);
    }

    protected List<OrderTicket> checkAsksFills(double bestBid, boolean isTrade) {
        return checkAsksFills(ticker, bestBid, isTrade, null);
    }

    protected List<OrderTicket> checkAsksFills(Ticker trackedTicker, double bestBid, boolean isTrade,
            BigDecimal tradeSize) {
        List<OrderTicket> filledOrders = new ArrayList<>();
        BigDecimal remainingTradeSize = (tradeSize != null && tradeSize.compareTo(BigDecimal.ZERO) > 0) ? tradeSize
                : null;
        // Take write lock on market data for exclusive access during fill processing
        marketDataLock.writeLock().lock();
        try {
            InstrumentState state = getOrCreateState(trackedTicker);
            if (!isTrade) {
                state.bestBidPrice = bestBid;
                state.midPrice = (state.bestBidPrice + state.bestAskPrice) / 2.0;
                syncLegacyFieldsFromState(trackedTicker, state);
            }

            for (Iterator<Map.Entry<String, OrderTicket>> it = openAsks.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, OrderTicket> entry = it.next();
                OrderTicket order = entry.getValue();
                if (order.getTicker() == null || !order.getTicker().equals(trackedTicker)) {
                    continue;
                }
                if (order.getLimitPrice() == null) {
                    continue;
                }
                boolean filled = order.getLimitPrice().doubleValue() <= bestBid;
                if (!filled) {
                    continue;
                }

                BigDecimal fillSize = determineFillSize(order, remainingTradeSize);
                if (fillSize.compareTo(BigDecimal.ZERO) > 0) {
                    boolean fullyFilled = fillOrder(order, order.getLimitPrice().doubleValue(), fillSize, true);
                    if (fullyFilled) {
                        it.remove();
                    }
                    filledOrders.add(order);
                    if (remainingTradeSize != null) {
                        remainingTradeSize = remainingTradeSize.subtract(fillSize);
                    }
                }
                if (remainingTradeSize != null && remainingTradeSize.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
            }
        } finally {
            marketDataLock.writeLock().unlock(); // Ensure the lock is released after processing
        }

        return filledOrders;
    }

    protected List<OrderTicket> checkBidsFills(double askPrice, boolean isTrade) {
        return checkBidsFills(ticker, askPrice, isTrade, null);
    }

    protected List<OrderTicket> checkBidsFills(Ticker trackedTicker, double askPrice, boolean isTrade,
            BigDecimal tradeSize) {
        List<OrderTicket> filledOrders = new ArrayList<>();
        BigDecimal remainingTradeSize = (tradeSize != null && tradeSize.compareTo(BigDecimal.ZERO) > 0) ? tradeSize
                : null;
        // Take write lock on market data for exclusive access during fill processing
        marketDataLock.writeLock().lock();
        try {
            InstrumentState state = getOrCreateState(trackedTicker);
            if (!isTrade) {
                state.bestAskPrice = askPrice;
                state.midPrice = (state.bestBidPrice + state.bestAskPrice) / 2.0;
                syncLegacyFieldsFromState(trackedTicker, state);
            }

            for (Iterator<Map.Entry<String, OrderTicket>> it = openBids.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, OrderTicket> entry = it.next();
                OrderTicket order = entry.getValue();
                if (order.getTicker() == null || !order.getTicker().equals(trackedTicker)) {
                    continue;
                }
                if (order.getLimitPrice() == null) {
                    continue;
                }
                boolean filled = order.getLimitPrice().doubleValue() >= askPrice;
                if (!filled) {
                    continue;
                }

                BigDecimal fillSize = determineFillSize(order, remainingTradeSize);
                if (fillSize.compareTo(BigDecimal.ZERO) > 0) {
                    boolean fullyFilled = fillOrder(order, order.getLimitPrice().doubleValue(), fillSize, true);
                    if (fullyFilled) {
                        it.remove();
                    }
                    filledOrders.add(order);
                    if (remainingTradeSize != null) {
                        remainingTradeSize = remainingTradeSize.subtract(fillSize);
                    }
                }
                if (remainingTradeSize != null && remainingTradeSize.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
            }
        } finally {
            marketDataLock.writeLock().unlock(); // Ensure the lock is released after processing
        }

        return filledOrders;
    }

    protected void updateStatus() {
        if (brokerStatus == null) {
            logger.warn("Broker status is not set, cannot update status.");
            return;
        }
        InstrumentState primaryState = getPrimaryState();
        double totalRealizedPnL = 0.0;
        double totalFunding = 0.0;
        for (InstrumentState state : instrumentStates.values()) {
            totalRealizedPnL += state.realizedPnL;
            totalFunding += state.fundingAccruedOrPaid;
        }
        double unrealizedPnL = getUnrealizedPnL();
        realizedPnL = totalRealizedPnL;
        fundingAccruedOrPaid = totalFunding;
        syncLegacyFieldsFromState(ticker, primaryState);

        synchronized (brokerStatus) { // Ensure thread-safe access
            brokerStatus.setCurrentPosition(primaryState.currentPosition.doubleValue());
            brokerStatus.setAccountValue(getNetAccountValue()); // Update account value
            brokerStatus.setDollarVolume(dollarVolume); // Calculate dollar volume
            brokerStatus.setFeesCollectedOrPaid(totalFees); // Update total fees paidasdfasdf
            brokerStatus.setTotalTrades(totalOrdersPlaced); // Update total trades
            brokerStatus.setBestAsk(primaryState.bestAskPrice); // Update best ask price
            brokerStatus.setBestBid(primaryState.bestBidPrice); // Update best bid price
            brokerStatus.setMidPoint(primaryState.midPrice); // Update mid price
            // brokerStatus.setVWAPMidpoint(orderBook.getVWAPMidpoint(30).doubleValue()); //
            // Update VWAP midpoint from
            // brokerStatus.setCOGMidpoint(orderBook.getCenterOfGravityMidpoint(30).doubleValue());
            // // Update COG midpoint
            // from order
            brokerStatus.setRealizedPnL(totalRealizedPnL);
            brokerStatus.setUnrealizedPnL(unrealizedPnL);
            brokerStatus.setTotalPnL(totalRealizedPnL + unrealizedPnL);
            brokerStatus.setPnlWithFees(totalRealizedPnL + unrealizedPnL + totalFees);
            brokerStatus.setPnlWithFeesAndFunding(totalFunding + totalRealizedPnL + unrealizedPnL + totalFees);
            brokerStatus.setOpenOrdersCount(openBids.size() + openAsks.size()); // Update the count of open orders
            brokerStatus.setFundingAccruedOrPaid(totalFunding); // Update total funding paid or collected
            brokerStatus.setFundingRateAnnualized(((primaryState.fundingRate / 8.0) * 24.0 * 365.0) * 100.0);

            logger.info("Broker status updated: {}", brokerStatus); // Log the updated
            // status
        }
    }

    protected void fillOrder(OrderTicket order, double price) {
        fillOrder(order, price, order.getRemainingSize(), order.getType() != Type.MARKET);
    }

    protected BigDecimal determineFillSize(OrderTicket order, BigDecimal remainingTradeSize) {
        BigDecimal orderRemaining = order.getRemainingSize();
        if (orderRemaining == null || orderRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (remainingTradeSize == null) {
            return orderRemaining;
        }
        return orderRemaining.min(remainingTradeSize);
    }

    protected BigDecimal weightedAveragePrice(BigDecimal previousAverage, BigDecimal previousSize, BigDecimal newPrice,
            BigDecimal newSize) {
        if (newSize == null || newSize.compareTo(BigDecimal.ZERO) <= 0) {
            return previousAverage == null ? BigDecimal.ZERO : previousAverage;
        }
        if (previousSize == null || previousSize.compareTo(BigDecimal.ZERO) <= 0 || previousAverage == null) {
            return newPrice;
        }
        BigDecimal totalSize = previousSize.add(newSize);
        if (totalSize.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalNotional = previousAverage.multiply(previousSize).add(newPrice.multiply(newSize));
        return totalNotional.divide(totalSize, 10, RoundingMode.HALF_UP);
    }

    protected boolean fillOrder(OrderTicket order, double price, BigDecimal fillSize, boolean isMaker) {
        if (order == null || fillSize == null || fillSize.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        String orderId = order.getOrderId();
        BigDecimal orderTotalSize = order.getSize();
        BigDecimal previousFilled = order.getFilledSize();
        BigDecimal remainingBeforeFill = orderTotalSize.subtract(previousFilled);
        if (remainingBeforeFill.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal executedSize = fillSize.min(remainingBeforeFill);
        if (executedSize.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal fillPrice = BigDecimal.valueOf(price);
        BigDecimal newFilledSize = previousFilled.add(executedSize);
        BigDecimal remainingAfterFill = orderTotalSize.subtract(newFilledSize);
        boolean fullyFilled = remainingAfterFill.compareTo(BigDecimal.ZERO) <= 0;

        order.setFilledPrice(weightedAveragePrice(order.getFilledPrice(), previousFilled, fillPrice, executedSize));
        order.setFilledSize(newFilledSize);
        order.setCurrentStatus(fullyFilled ? OrderStatus.Status.FILLED : OrderStatus.Status.PARTIAL_FILL);
        if (fullyFilled) {
            order.setOrderFilledTime(getCurrentTime());
            openOrders.remove(order.getOrderId());
        }

        logger.info("Filling order: {} at price: {} with size: {}", order, price, executedSize);

        try {
            updatePosition(order.getTradeDirection(), executedSize, price, isMaker, order.getTicker());
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
        }

        OrderStatus.Status status = fullyFilled ? OrderStatus.Status.FILLED : OrderStatus.Status.PARTIAL_FILL;
        OrderStatus orderStatus = new OrderStatus(status, orderId, orderId, newFilledSize,
                remainingAfterFill.max(BigDecimal.ZERO), fillPrice, order.getTicker(), getCurrentTime());
        orderStatus.setClientOrderId(order.getClientOrderId());
        OrderEvent event = new OrderEvent(order, orderStatus);

        logger.debug("Created OrderStatus for filled order: {}", orderStatus);

        if (fullyFilled) {
            executedOrders.add(order);
        }

        double fee = calcFee(price, executedSize.doubleValue(), isMaker);
        logger.debug("Calculated fee for order {}: {}", orderId, fee);
        BigDecimal currentCommission = order.getCommission() == null ? BigDecimal.ZERO : order.getCommission();
        order.setCommission(currentCommission.add(BigDecimal.valueOf(fee)));

        Fill fill = new Fill();
        fill.setCommission(BigDecimal.valueOf(fee));
        fill.setFillId(System.currentTimeMillis() + "-" + (int) (Math.random() * 10000));
        fill.setOrderId(orderId);
        fill.setClientOrderId(order.getClientOrderId());
        fill.setPrice(fillPrice);
        fill.setSide(order.getTradeDirection());
        fill.setSize(executedSize);
        fill.setTaker(!isMaker);
        fill.setTicker(order.getTicker());
        fill.setTime(getCurrentTime());
        order.addFill(fill);

        fireFillUpdate(fill); // Notify listeners of the fill event

        fireOrderStatusUpdate(event); // Notify listeners of the order status update

        writeTradeToCsv(order, price, fee, executedSize);
        return fullyFilled;
    }

    protected void cancelOrder(String orderId, String clientOrderId, CancelReason reason) {
        // This method is called from within already locked contexts, so no additional
        // locking needed
        // but we need to ensure we don't have the marketDataLock when calling this
        try {
            logger.debug("Attempting to remove order with ID: {} from openOrders", orderId);
            OrderTicket order = openOrders.remove(orderId);
            openBids.remove(orderId);
            openAsks.remove(orderId);
            BigDecimal remainingSize = BigDecimal.ZERO;

            OrderStatus.Status status = OrderStatus.Status.CANCELED;
            BigDecimal averageFillPrice = BigDecimal.ZERO; // No fill price since it's cancelled

            OrderStatus orderStatus = new OrderStatus(status, orderId, orderId, averageFillPrice, remainingSize,
                    averageFillPrice, order != null ? order.getTicker() : ticker, getCurrentTime());
            orderStatus.setClientOrderId(clientOrderId);
            orderStatus.setCancelReason(reason);

            OrderEvent event = new OrderEvent(order, orderStatus);

            fireOrderStatusUpdate(event); // Notify listeners of the cancellation
        } finally {
            // No lock to release - this method is called from within locked contexts
        }
    }

    protected void fireOrderStatusUpdate(OrderEvent orderStatus) {
        executorService.submit(() -> {
            try {
                delayWebSocketCall();
                super.fireOrderEvent(orderStatus); // Notify the listeners with the order status update
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });

    }

    protected void fireFillUpdate(Fill fill) {
        logger.debug("Notifying fill listeners: {}", fill);
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
                super.fireAccountEquityUpdated(getNetAccountValue()); // Notify the listeners with the account update
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    protected void updatePosition(TradeDirection side, BigDecimal orderSize, double price, boolean isMaker) {
        updatePosition(side, orderSize, price, isMaker, ticker);
    }

    protected void updatePosition(TradeDirection side, BigDecimal orderSize, double price, boolean isMaker,
            Ticker trackedTicker) {
        logger.debug("Updating position. Side: {}, Order Size: {}, Price: {}, Is Maker: {}", side, orderSize, price,
                isMaker);
        InstrumentState state = getOrCreateState(trackedTicker);
        BigDecimal size = orderSize; // Use the initial size passed to the method
        double notional = size.doubleValue() * price;
        if (side == TradeDirection.BUY) {
            if (state.currentPosition.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal closingSize = state.currentPosition.negate().min(size);

                double pnl = (state.averageEntryPrice - price) * closingSize.doubleValue();
                state.realizedPnL += pnl;
                currentAccountBalance += pnl; // Add realized PnL to the account balance
                state.currentPosition = state.currentPosition.add(closingSize);
                size = size.subtract(closingSize);

                if (state.currentPosition.compareTo(BigDecimal.ZERO) == 0)
                    state.averageEntryPrice = 0;

                if (size.compareTo(BigDecimal.ZERO) > 0) {
                    state.averageEntryPrice = price;
                    // currentAccountBalance -= size * price;
                    state.currentPosition = state.currentPosition.add(size);
                }
            } else {
                state.averageEntryPrice = ((state.averageEntryPrice * state.currentPosition.doubleValue())
                        + (price * size.doubleValue())) / (state.currentPosition.doubleValue() + size.doubleValue());
                // currentAccountBalance -= size * price;
                state.currentPosition = state.currentPosition.add(size);
            }
        } else { // SELL
            if (state.currentPosition.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal closingSize = state.currentPosition.min(size);
                double pnl = (price - state.averageEntryPrice) * closingSize.doubleValue();
                state.realizedPnL += pnl;
                currentAccountBalance += pnl; // Add realized PnL to the account balance
                state.currentPosition = state.currentPosition.subtract(closingSize);
                size = size.subtract(closingSize);

                if (state.currentPosition.compareTo(BigDecimal.ZERO) == 0)
                    state.averageEntryPrice = 0;

                if (size.compareTo(BigDecimal.ZERO) > 0) {
                    state.averageEntryPrice = price;
                    // currentAccountBalance += size * price;
                    state.currentPosition = state.currentPosition.subtract(size);
                }
            } else {
                state.averageEntryPrice = ((state.averageEntryPrice * -state.currentPosition.doubleValue())
                        + (price * size.doubleValue())) / (-state.currentPosition.doubleValue() + size.doubleValue());
                // currentAccountBalance += size * price;
                state.currentPosition = state.currentPosition.subtract(size);
            }
        }

        // Apply maker or taker fee

        double fee = calcFee(price, orderSize.doubleValue(), isMaker);
        currentAccountBalance += fee;
        totalFees += fee; // Update total fees paid

        dollarVolume += Math.abs(notional);
        brokerStatus.setDollarVolume(dollarVolume); // Update the dollar volume in the broker status
        totalOrdersPlaced++;
        brokerStatus.setTotalTrades(totalOrdersPlaced); // Update total trades in the broker status
        brokerStatus.setFeesCollectedOrPaid(totalFees); // Update total fees in the broker status
        brokerStatus.setAccountValue(getNetAccountValue()); // Update the account value in the broker status
        syncLegacyFieldsFromState(trackedTicker, state);
        double totalRealized = 0.0;
        for (InstrumentState instrumentState : instrumentStates.values()) {
            totalRealized += instrumentState.realizedPnL;
        }
        realizedPnL = totalRealized;

    }

    protected double calcFee(double price, double size, boolean isMaker) {
        double notional = Math.abs(price * size); // Calculate the notional value of the trade
        double feeRate = isMaker ? makerFee : takerFee; // Determine the fee rate based on order type
        return notional * feeRate; // Calculate and return the fee
    }

    protected void writeTradeToCsv(OrderTicket order, double price, double fee) {
        writeTradeToCsv(order, price, fee, order.getSize());
    }

    protected void writeTradeToCsv(OrderTicket order, double price, double fee, BigDecimal fillSize) {
        if (order == null || csvFilePath == null) {
            return;
        }
        executorService.submit(() -> {
            try {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFilePath, true))) {

                    if (!firstTradeWrittenToFile) {
                        // Write the header only once
                        writer.write("####");
                        writer.newLine();
                        firstTradeWrittenToFile = true;
                    }

                    // Prepare the trade details as a CSV line
                    String symbol = order.getTicker() == null ? asset : order.getTicker().getSymbol();
                    ZonedDateTime orderFilledTime = order.getOrderFilledTime() == null ? getCurrentTime()
                            : order.getOrderFilledTime();
                    String csvLine = String.format("%s,%s,%s,%.5f,%s,%s,%s,%.5f,%.5f,%d", symbol, order.getOrderId(),
                            order.getDirection(), // Side (BUY/SELL)
                            fillSize.doubleValue(), // Size
                            order.getType(), order.getOrderEntryTime().format(DateTimeFormatter.ISO_INSTANT), // Submitted
                                                                                                              // Time
                            orderFilledTime.format(DateTimeFormatter.ISO_INSTANT), // Filled Time
                            price, // Price
                            fee, // Fee
                            System.currentTimeMillis()); // Timestamp

                    writer.write(csvLine); // Write the line to the file
                    writer.newLine(); // Add a newline
                } catch (IOException e) {
                    logger.error(e.getLocalizedMessage(), e);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });

    }

    protected String generateCsvFilename(String symbol, String exchange) {
        // Get the current date and time
        ZonedDateTime now = getCurrentTime();

        // Format the date and time as yyyyMMdd-HHmm
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
        String timestamp = now.format(formatter);

        // Construct the filename
        return String.format("%s-%s-%s-Trades.csv", timestamp, sanitizeFilenameComponent(symbol),
                sanitizeFilenameComponent(exchange));
    }

    protected String generateBalanceFilename(String symbol, String exchange) {
        return String.format("%s-%s-paperbroker-startingbalance.txt", sanitizeFilenameComponent(symbol),
                sanitizeFilenameComponent(exchange));
    }

    protected String sanitizeFilenameComponent(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    protected void delayRestCall() {
        // Simulate network delay or processing time
        try {
            long latency = latencyModel.getRestLatencyMsMin() + (long) (Math.random()
                    * (latencyModel.getRestLatencyMsMax() - latencyModel.getRestLatencyMsMin()));
            logger.debug("REST call delay: {} ms", latency);
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            logger.error("Delay interrupted: " + e.getMessage(), e);
        }
    }

    protected void delayWebSocketCall() {
        // Simulate network delay or processing time
        try {
            // sleep time should be a random time betweehn the latencyMsMin and latencyMsMax

            long latency = latencyModel.getWsLatencyMsMin()
                    + (long) (Math.random() * (latencyModel.getWsLatencyMsMax() - latencyModel.getWsLatencyMsMin()));
            logger.debug("WebSocket call delay: {} ms", latency);
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            logger.error("Delay interrupted: " + e.getMessage(), e);
        }
    }

    @Override
    public void addBrokerErrorListener(BrokerErrorListener listener) {
        throw new UnsupportedOperationException("Not yet supported in Paper Broker");

    }

    @Override
    public void addTimeUpdateListener(TimeUpdatedListener listener) {
        throw new UnsupportedOperationException("Not yet supported in Paper Broker");

    }

    @Override
    public void aquireLock() {
        throw new UnsupportedOperationException("aquireLock not supported in PaperBroker");
    }

    @Override
    public ComboTicker buildComboTicker(Ticker ticker1, Ticker ticker2) {
        throw new UnsupportedOperationException("Not yet supported in Paper Broker");
    }

    @Override
    public ComboTicker buildComboTicker(Ticker ticker1, int ratio1, Ticker ticker2, int ratio2) {
        throw new UnsupportedOperationException("Not yet supported in Paper Broker");
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        cancelOrder(originalOrderId);
        placeOrder(newOrder);
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        cancelOrder(order.getOrderId());
        return new BrokerRequestResult();

    }

    @Override
    public void connect() {
        // while (!orderBook.isInitialized()) {
        // try {
        // logger.info("Waiting for order book to initialize...");
        // Thread.sleep(500);
        // } catch (InterruptedException e) {
        // Thread.currentThread().interrupt(); // Restore interrupted status
        // logger.error("Interrupted while waiting for order book to initialize: {}",
        // e.getMessage(), e);
        // }
        // }
        startAccountUpdateTask();

    }

    @Override
    public ZonedDateTime getCurrentTime() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    @Override
    public String getFormattedDate(int hour, int minute, int second) {
        throw new UnsupportedOperationException("getFormattedDate not supported in PaperBroker");
    }

    @Override
    public String getFormattedDate(ZonedDateTime date) {
        throw new UnsupportedOperationException("getFormattedDate not supported in PaperBroker");
    }

    @Override
    public String getNextOrderId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        delayRestCall(); // Simulate network delay
        List<OrderTicket> orders = new ArrayList<>(); // List to hold open orders
        for (OrderTicket order : openBids.values()) {
            orders.add(order);
        }
        for (OrderTicket order : openAsks.values()) {
            orders.add(order);
        }

        return orders; // Return the list of open orders
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void releaseLock() {
        throw new UnsupportedOperationException("releaseLock not supported in PaperBroker");
    }

    @Override
    public void removeBrokerErrorListener(BrokerErrorListener listener) {
        throw new UnsupportedOperationException("removeBrokerErrorListener not supported in PaperBroker");
    }

    @Override
    public void removeTimeUpdateListener(TimeUpdatedListener listener) {
        throw new UnsupportedOperationException("removeTimeUpdateListener not supported in PaperBroker");

    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        throw new UnsupportedOperationException("requestOrderStatus not supported in PaperBroker");
    }

    @Override
    public void quoteRecieved(ILevel1Quote quote) {
        if (quote == null || quote.getTicker() == null) {
            return;
        }
        Ticker quoteTicker = quote.getTicker();
        ensureTickerSubscription(quoteTicker);

        if (quote.containsType(QuoteType.MARK_PRICE)) {
            markPriceUpdated(quoteTicker, quote.getValue(QuoteType.MARK_PRICE), quote.getTimeStamp());
        }

        if (quote.containsType(QuoteType.FUNDING_RATE_APR)) {
            fundingRateUpdated(quoteTicker, quote.getValue(QuoteType.FUNDING_RATE_APR), quote.getTimeStamp());
        }

        if (quote.containsType(QuoteType.BID)) {
            bidUpdated(quoteTicker, quote.getValue(QuoteType.BID), false, null);
        }

        if (quote.containsType(QuoteType.ASK)) {
            askUpdated(quoteTicker, quote.getValue(QuoteType.ASK), false, null);
        }
    }

    @Override
    public void orderflowReceived(OrderFlow orderflow) {
        if (orderflow == null || orderflow.getTicker() == null) {
            return;
        }
        Ticker flowTicker = orderflow.getTicker();
        ensureTickerSubscription(flowTicker);

        if (orderflow.getSide() == OrderFlow.Side.BUY) {
            bidUpdated(flowTicker, orderflow.getPrice(), true, orderflow.getSize());
        } else if (orderflow.getSide() == OrderFlow.Side.SELL) {
            askUpdated(flowTicker, orderflow.getPrice(), true, orderflow.getSize());
        }

    }

    @Override
    public BrokerStatus getBrokerStatus() {
        return BrokerStatus.OK; // For the paper broker, we can assume it's always operational
    }

    public IPaperBrokerStatus getPaperBrokerStatus() {
        return brokerStatus;
    }

}
