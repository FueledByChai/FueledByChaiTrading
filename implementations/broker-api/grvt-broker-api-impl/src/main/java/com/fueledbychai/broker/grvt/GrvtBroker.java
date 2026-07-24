package com.fueledbychai.broker.grvt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.grvt.common.api.GrvtConfiguration;
import com.fueledbychai.grvt.common.api.IGrvtRestApi;
import com.fueledbychai.grvt.common.api.IGrvtWebSocketApi;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

/**
 * GRVT broker. Order entry and cancellation go over the trade-data WebSocket JSON-RPC channel (with
 * a REST fallback when the socket is unavailable); order/fill/position updates arrive over the
 * trade-data feed streams. Account snapshots (open orders, positions) are seeded from REST.
 */
public class GrvtBroker extends AbstractBasicBroker {

    protected static final Logger log = LoggerFactory.getLogger(GrvtBroker.class);
    protected static final long RPC_TIMEOUT_SECONDS = 10L;
    protected static final long ACCOUNT_SNAPSHOT_POLL_SECONDS = 5L;
    protected static final ZoneId UTC = ZoneId.of("UTC");

    protected final IGrvtRestApi restApi;
    protected final IGrvtWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;
    protected final IGrvtTranslator translator;
    protected final String subAccountId;
    protected final AtomicLong nextClientOrderId = new AtomicLong(System.currentTimeMillis());
    protected final Map<String, Position> positionsByKey = new ConcurrentHashMap<>();
    protected final java.util.Set<String> processedFillIds = ConcurrentHashMap.newKeySet();

    protected volatile boolean connected = false;
    protected volatile ScheduledExecutorService accountSnapshotExecutor;

    public GrvtBroker() {
        this(ExchangeRestApiFactory.getPrivateApi(Exchange.GRVT, IGrvtRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.GRVT, IGrvtWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.GRVT), GrvtTranslator.getInstance(),
                GrvtConfiguration.getInstance().getSubAccountId());
    }

    protected GrvtBroker(IGrvtRestApi restApi, IGrvtWebSocketApi webSocketApi, ITickerRegistry tickerRegistry,
            IGrvtTranslator translator, String subAccountId) {
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        if (webSocketApi == null) {
            throw new IllegalArgumentException("webSocketApi is required");
        }
        if (tickerRegistry == null) {
            throw new IllegalArgumentException("tickerRegistry is required");
        }
        this.restApi = restApi;
        this.webSocketApi = webSocketApi;
        this.tickerRegistry = tickerRegistry;
        this.translator = translator == null ? GrvtTranslator.getInstance() : translator;
        this.subAccountId = subAccountId;
    }

    @Override
    public String getBrokerName() {
        return "GRVT";
    }

    @Override
    public synchronized void connect() {
        if (connected) {
            return;
        }
        if (restApi.isPublicApiOnly()) {
            throw new IllegalStateException("GRVT broker requires private api credentials.");
        }

        // Authenticate and prove that private REST state is readable before
        // exposing the broker as connected. Order placement must not proceed
        // with a working trade websocket but a blind account/reconcile path.
        restApi.login();
        refreshOpenOrdersFromRest();
        refreshPositionsFromRest();
        refreshAccountSnapshotFromRest();

        webSocketApi.connectTradeData();
        webSocketApi.subscribeTradeData("order", subAccountId, this::onOrderMessage);
        webSocketApi.subscribeTradeData("fill", subAccountId, this::onFillMessage);
        webSocketApi.subscribeTradeData("position", subAccountId, this::onPositionMessage);
        connected = true;
        startAccountSnapshotPoller();
    }

    @Override
    protected void onDisconnect() {
        connected = false;
        stopAccountSnapshotPoller();
        // Do not tear down public market data when only the authenticated broker
        // session is ending.
        webSocketApi.disconnectTradeData();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public BrokerStatus getBrokerStatus() {
        return connected ? BrokerStatus.OK : BrokerStatus.UNKNOWN;
    }

    @Override
    public String getNextOrderId() {
        return Long.toString(nextClientOrderId.incrementAndGet());
    }

    // --------------------------------------------------------- order entry

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        if (!connected) {
            return notConnected();
        }
        BrokerRequestResult validation = validate(order);
        if (!validation.isSuccess()) {
            return validation;
        }

        if (order.getClientOrderId() == null || order.getClientOrderId().isBlank()) {
            order.setClientOrderId(getNextOrderId());
        }
        order.setOrderEntryTime(getCurrentTime());

        try {
            GrvtOrder grvtOrder = translator.toGrvtOrder(order, subAccountId);
            JsonNode response = submitOrder(grvtOrder);
            JsonNode orderNode = extractOrderNode(response);
            if (orderNode == null || orderNode.isMissingNode() || orderNode.isNull()) {
                return new BrokerRequestResult(false, true, "GRVT order rejected: " + response,
                        BrokerRequestResult.FailureType.UNKNOWN);
            }
            applyOrderSnapshot(orderNode, true);
            return new BrokerRequestResult();
        } catch (Exception e) {
            log.error("Error placing GRVT order", e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    /** Submits over WS-RPC, falling back to REST when the trade socket is unavailable. */
    protected JsonNode submitOrder(GrvtOrder grvtOrder) throws Exception {
        if (webSocketApi.isTradeDataConnected()) {
            try {
                return webSocketApi.rpcCreateOrder(grvtOrder).get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("GRVT WS order entry failed; falling back to REST", e);
            }
        }
        return restApi.createOrder(grvtOrder);
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        if (!connected) {
            return notConnected();
        }
        if (id == null || id.isBlank()) {
            return validationFailure("order id is required");
        }
        OrderTicket open = orderRegistry.getOpenOrderById(id);
        String clientOrderId = open == null ? null : open.getClientOrderId();
        return cancel(id, clientOrderId);
    }

    @Override
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        if (!connected) {
            return notConnected();
        }
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return validationFailure("clientOrderId is required");
        }
        OrderTicket open = orderRegistry.getOpenOrderByClientId(clientOrderId);
        String orderId = open == null ? null : open.getOrderId();
        return cancel(orderId, clientOrderId);
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        if (order == null) {
            return validationFailure("order is required");
        }
        if (order.getOrderId() != null && !order.getOrderId().isBlank()) {
            return cancelOrder(order.getOrderId());
        }
        return cancelOrderByClientOrderId(order.getClientOrderId());
    }

    protected BrokerRequestResult cancel(String orderId, String clientOrderId) {
        try {
            if (webSocketApi.isTradeDataConnected()) {
                try {
                    webSocketApi.rpcCancelOrder(orderId, clientOrderId).get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return new BrokerRequestResult();
                } catch (Exception e) {
                    log.warn("GRVT WS cancel failed; falling back to REST", e);
                }
            }
            restApi.cancelOrder(orderId, clientOrderId);
            return new BrokerRequestResult();
        } catch (Exception e) {
            log.error("Error canceling GRVT order {}/{}", orderId, clientOrderId, e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        if (!connected) {
            return notConnected();
        }
        try {
            if (webSocketApi.isTradeDataConnected()) {
                webSocketApi.rpcCancelAllOrders().get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } else {
                restApi.cancelAllOrders();
            }
            return new BrokerRequestResult();
        } catch (Exception e) {
            log.error("Error canceling all GRVT orders", e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        return cancelAllOrders();
    }

    @Override
    public BrokerRequestResult cancelOrders(List<OrderTicket> orders) {
        throw new UnsupportedOperationException("Batch cancel not supported by GrvtBroker");
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        OrderTicket known = orderRegistry.getOrderById(orderId);
        if (!connected || orderId == null || orderId.isBlank()) {
            return known;
        }
        try {
            JsonNode response = restApi.getOrder(orderId, null);
            JsonNode orderNode = extractOrderNode(response);
            OrderTicket updated = applyOrderSnapshot(orderNode, false);
            return updated == null ? known : updated;
        } catch (Exception e) {
            log.debug("Unable to refresh GRVT order {}", orderId, e);
            return known;
        }
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        OrderTicket known = orderRegistry.getOrderByClientId(clientOrderId);
        if (!connected || clientOrderId == null || clientOrderId.isBlank()) {
            return known;
        }
        try {
            JsonNode response = restApi.getOrder(null, clientOrderId);
            JsonNode orderNode = extractOrderNode(response);
            OrderTicket updated = applyOrderSnapshot(orderNode, false);
            return updated == null ? known : updated;
        } catch (Exception e) {
            log.debug("Unable to refresh GRVT order by client id {}", clientOrderId, e);
            return known;
        }
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        if (connected) {
            try {
                refreshOpenOrdersFromRest();
            } catch (Exception e) {
                log.debug("Unable to refresh GRVT open orders", e);
            }
        }
        return new ArrayList<>(orderRegistry.getOpenOrders());
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        BrokerRequestResult cancelResult = cancelOrder(originalOrderId);
        if (!cancelResult.isSuccess()) {
            log.warn("GRVT cancel-and-replace failed during cancel: {}", cancelResult);
            return;
        }
        placeOrder(newOrder);
    }

    @Override
    public List<Position> getAllPositions() {
        if (connected) {
            try {
                refreshPositionsFromRest();
            } catch (Exception e) {
                log.debug("Unable to refresh GRVT positions", e);
            }
        }
        return new ArrayList<>(positionsByKey.values());
    }

    // ------------------------------------------------------- feed handlers

    protected void onOrderMessage(JsonNode message) {
        for (JsonNode orderNode : feedItems(message)) {
            try {
                applyOrderSnapshot(orderNode, true);
            } catch (Exception e) {
                log.warn("Failed to process GRVT order update", e);
            }
        }
    }

    protected void onFillMessage(JsonNode message) {
        for (JsonNode fillNode : feedItems(message)) {
            try {
                Fill fill = toFill(fillNode);
                if (fill != null && processedFillIds.add(fillKey(fill))) {
                    OrderTicket order = findTrackedOrder(fill.getOrderId(), fill.getClientOrderId());
                    if (order != null) {
                        order.addFill(fill);
                        order.setFilledSize(order.getFilledSizeFromFills());
                        order.setFilledPrice(order.getAverageFillPriceFromFills());
                    }
                    fireFillEvent(fill);
                }
            } catch (Exception e) {
                log.warn("Failed to process GRVT fill", e);
            }
        }
    }

    protected void onPositionMessage(JsonNode message) {
        for (JsonNode positionNode : feedItems(message)) {
            try {
                Position position = toPosition(positionNode);
                if (position == null) {
                    continue;
                }
                String key = positionKey(position.getTicker(), position.getSide());
                if (position.getStatus() == Position.Status.CLOSED) {
                    positionsByKey.remove(key);
                } else {
                    positionsByKey.put(key, position);
                }
            } catch (Exception e) {
                log.warn("Failed to process GRVT position", e);
            }
        }
    }

    // --------------------------------------------------------- REST seeding

    protected void refreshOpenOrdersFromRest() {
        JsonNode response = restApi.getOpenOrders();
        List<OrderTicket> openOrders = new ArrayList<>();
        Set<Ticker> affectedTickers = ConcurrentHashMap.newKeySet();
        for (OrderTicket existing : orderRegistry.getOpenOrders()) {
            if (existing != null && existing.getTicker() != null) {
                affectedTickers.add(existing.getTicker());
            }
        }
        if (response != null && response.isArray()) {
            for (JsonNode node : response) {
                OrderTicket order = applyOrderSnapshot(node, false);
                if (order != null && !isTerminal(order.getCurrentStatus())) {
                    openOrders.add(order);
                    if (order.getTicker() != null) {
                        affectedTickers.add(order.getTicker());
                    }
                }
            }
        }
        // BrokerOrderRegistry.replaceOpenOrders(List) is a no-op for an empty
        // list, so reconcile each known ticker explicitly. Otherwise an empty
        // GRVT snapshot leaves every previously tracked order alive forever.
        for (Ticker ticker : affectedTickers) {
            List<OrderTicket> tickerOrders = openOrders.stream()
                    .filter(order -> ticker.equals(order.getTicker()))
                    .toList();
            orderRegistry.replaceOpenOrders(ticker, tickerOrders);
        }
    }

    protected void refreshPositionsFromRest() {
        JsonNode response = restApi.getPositions();
        positionsByKey.clear();
        if (response != null && response.isArray()) {
            for (JsonNode node : response) {
                Position position = toPosition(node);
                if (position != null && position.getStatus() == Position.Status.OPEN) {
                    positionsByKey.put(positionKey(position.getTicker(), position.getSide()), position);
                }
            }
        }
    }

    protected void refreshAccountSnapshotFromRest() {
        JsonNode response = restApi.getAccountSummary();
        if (response == null || response.isNull() || response.isMissingNode()) {
            return;
        }

        // The full endpoint uses snake_case while GRVT's lite schema uses short
        // field names. Accept both so a future endpoint-mode change cannot silently
        // reset the dashboard to its default $0 balance.
        BigDecimal accountEquity = firstNonNull(decimalOrNull(response, "total_equity"),
                decimalOrNull(response, "totalEquity"), decimalOrNull(response, "te"));
        BigDecimal availableFunds = firstNonNull(decimalOrNull(response, "available_balance"),
                decimalOrNull(response, "availableBalance"), decimalOrNull(response, "ab"));

        if (accountEquity == null && availableFunds == null) {
            log.warn("GRVT account summary did not contain equity or available-balance fields");
            return;
        }
        if (accountEquity != null) {
            fireAccountEquityUpdated(accountEquity.doubleValue());
        }
        if (availableFunds != null) {
            fireAvailableFundsUpdated(availableFunds.doubleValue());
        }
    }

    protected synchronized void startAccountSnapshotPoller() {
        stopAccountSnapshotPoller();
        accountSnapshotExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "grvt-account-snapshot-poll");
            thread.setDaemon(true);
            return thread;
        });
        accountSnapshotExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (connected) {
                    refreshAccountSnapshotFromRest();
                }
            } catch (Exception e) {
                log.warn("Failed to refresh GRVT account snapshot", e);
            }
        }, ACCOUNT_SNAPSHOT_POLL_SECONDS, ACCOUNT_SNAPSHOT_POLL_SECONDS, TimeUnit.SECONDS);
    }

    protected synchronized void stopAccountSnapshotPoller() {
        if (accountSnapshotExecutor != null) {
            accountSnapshotExecutor.shutdownNow();
            accountSnapshotExecutor = null;
        }
    }

    // ------------------------------------------------------------- parsing

    protected OrderTicket applyOrderSnapshot(JsonNode source, boolean fireEvent) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return null;
        }

        String orderId = source.path("order_id").asText("");
        if (isPlaceholderOrderId(orderId)) {
            orderId = "";
        }
        String clientOrderId = source.path("metadata").path("client_order_id").asText("");
        JsonNode firstLeg = source.path("legs").path(0);
        String instrument = firstLeg.path("instrument").asText("");

        JsonNode state = source.path("state");
        String grvtStatus = state.path("status").asText("");
        BigDecimal tradedSize = decimalAt(state.path("traded_size"), 0);
        BigDecimal bookSize = decimalAt(state.path("book_size"), 0);
        BigDecimal originalSize = decimalOrNull(firstLeg, "size");
        BigDecimal limitPrice = decimalOrNull(firstLeg, "limit_price");

        OrderTicket order = findTrackedOrder(orderId, clientOrderId);
        OrderStatus.Status previousStatus = order == null ? null : order.getCurrentStatus();
        BigDecimal previousFilled = order == null ? null : order.getFilledSize();
        if (order == null) {
            order = new OrderTicket();
        }

        if (!orderId.isBlank()) {
            order.setOrderId(orderId);
        }
        if (!clientOrderId.isBlank()) {
            order.setClientOrderId(clientOrderId);
        }
        if (!instrument.isBlank()) {
            order.setTicker(resolveTickerBySymbol(instrument));
        }
        if (firstLeg.has("is_buying_asset")) {
            order.setTradeDirection(firstLeg.path("is_buying_asset").asBoolean() ? TradeDirection.BUY
                    : TradeDirection.SELL);
        }
        if (originalSize != null) {
            order.setSize(originalSize);
        }
        if (limitPrice != null && limitPrice.signum() > 0) {
            order.setLimitPrice(limitPrice);
            order.setType(Type.LIMIT);
        } else if (source.path("is_market").asBoolean(false)) {
            order.setType(Type.MARKET);
        }
        if (tradedSize != null) {
            order.setFilledSize(tradedSize);
        }

        OrderStatus.Status status = translator.toOrderStatus(grvtStatus, tradedSize, bookSize);
        order.setCurrentStatus(status);

        if (isTerminal(status)) {
            orderRegistry.addCompletedOrder(order);
        } else {
            orderRegistry.addOpenOrder(order);
        }

        if (fireEvent && hasChanged(previousStatus, previousFilled, order)) {
            ZonedDateTime timestamp = toTimestamp(state.path("update_time").asText(""));
            OrderStatus orderStatus = new OrderStatus(status, order.getOrderId(), order.getFilledSize(),
                    order.getRemainingSize(), order.getFilledPrice(), order.getTicker(), timestamp);
            orderStatus.setClientOrderId(order.getClientOrderId());
            fireOrderEvent(new OrderEvent(order, orderStatus));
        }
        return order;
    }

    protected Fill toFill(JsonNode source) {
        BigDecimal size = decimalOrNull(source, "size");
        if (size == null || size.signum() <= 0) {
            return null;
        }
        String instrument = textOf(source, "instrument");
        Fill fill = new Fill();
        fill.setTicker(resolveTickerBySymbol(instrument));
        fill.setOrderId(textOf(source, "order_id"));
        fill.setClientOrderId(source.path("client_order_id").asText(""));
        fill.setPrice(firstNonNull(decimalOrNull(source, "price"), BigDecimal.ZERO));
        fill.setSize(size);
        fill.setCommission(firstNonNull(decimalOrNull(source, "fee"), BigDecimal.ZERO).abs());
        fill.setFillId(firstNonBlank(textOf(source, "trade_id"), textOf(source, "tx_id")));
        fill.setTime(toTimestamp(textOf(source, "event_time")));
        boolean buyer = source.path("is_buyer").asBoolean(source.path("is_taker_buyer").asBoolean(false));
        fill.setSide(buyer ? TradeDirection.BUY : TradeDirection.SELL);
        fill.setTaker(source.path("is_taker").asBoolean(true));
        return fill;
    }

    protected Position toPosition(JsonNode source) {
        String instrument = textOf(source, "instrument");
        if (instrument.isBlank()) {
            return null;
        }
        BigDecimal size = firstNonNull(decimalOrNull(source, "size"), decimalOrNull(source, "total_size"));
        if (size == null) {
            return null;
        }
        Side side = size.signum() < 0 ? Side.SHORT : Side.LONG;
        BigDecimal magnitude = size.abs();
        BigDecimal entryPrice = firstNonNull(decimalOrNull(source, "entry_price"),
                decimalOrNull(source, "avg_entry_price"), BigDecimal.ZERO);
        Position.Status status = magnitude.signum() == 0 ? Position.Status.CLOSED : Position.Status.OPEN;
        Position position = new Position(resolveTickerBySymbol(instrument), side, magnitude, entryPrice, status);
        position.setLiquidationPrice(decimalOrNull(source, "liquidation_price"));
        return position;
    }

    // ------------------------------------------------------------- helpers

    protected BrokerRequestResult validate(OrderTicket order) {
        if (order == null || order.getTicker() == null || order.getTicker().getSymbol() == null) {
            return validationFailure("order ticker is required");
        }
        if (order.getSize() == null || order.getSize().signum() <= 0) {
            return new BrokerRequestResult(false, true, "order size must be > 0",
                    BrokerRequestResult.FailureType.INVALID_SIZE);
        }
        Type type = order.getType() == null ? Type.MARKET : order.getType();
        if (type == Type.LIMIT && (order.getLimitPrice() == null || order.getLimitPrice().signum() <= 0)) {
            return new BrokerRequestResult(false, true, "limit orders require a positive limit price",
                    BrokerRequestResult.FailureType.INVALID_PRICE);
        }
        if (type != Type.MARKET && type != Type.LIMIT) {
            return validationFailure("GRVT broker supports market and limit orders only");
        }
        return new BrokerRequestResult();
    }

    protected OrderTicket findTrackedOrder(String orderId, String clientOrderId) {
        OrderTicket order = null;
        if (orderId != null && !orderId.isBlank()) {
            order = orderRegistry.getOpenOrderById(orderId);
            if (order == null) {
                order = orderRegistry.getCompletedOrderById(orderId);
            }
        }
        if (order == null && clientOrderId != null && !clientOrderId.isBlank()) {
            order = orderRegistry.getOpenOrderByClientId(clientOrderId);
            if (order == null) {
                order = orderRegistry.getCompletedOrderByClientId(clientOrderId);
            }
        }
        return order;
    }

    protected Ticker resolveTickerBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (ticker != null) {
            return ticker;
        }
        ticker = tickerRegistry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (ticker != null) {
            return ticker;
        }
        return new Ticker(symbol).setExchange(Exchange.GRVT).setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setFundingRateInterval(8);
    }

    protected boolean hasChanged(OrderStatus.Status previousStatus, BigDecimal previousFilled, OrderTicket order) {
        if (previousStatus == null) {
            return true;
        }
        if (!Objects.equals(previousStatus, order.getCurrentStatus())) {
            return true;
        }
        return !Objects.equals(previousFilled, order.getFilledSize());
    }

    protected boolean isTerminal(OrderStatus.Status status) {
        return status == OrderStatus.Status.FILLED || status == OrderStatus.Status.CANCELED
                || status == OrderStatus.Status.REJECTED;
    }

    protected boolean isPlaceholderOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return true;
        }
        String normalized = orderId.trim().toLowerCase(Locale.US);
        return normalized.matches("0x0+");
    }

    protected JsonNode extractOrderNode(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode result = response.path("result");
        if (result.has("result")) {
            return result.path("result");
        }
        if (result.has("order_id") || result.has("legs")) {
            return result;
        }
        return response.has("order_id") || response.has("legs") ? response : result;
    }

    protected String positionKey(Ticker ticker, Side side) {
        String symbol = ticker == null ? "" : ticker.getSymbol();
        return symbol + "|" + (side == null ? Side.LONG : side);
    }

    protected String fillKey(Fill fill) {
        String fillId = fill.getFillId();
        if (fillId != null && !fillId.isBlank()) {
            return fill.getOrderId() + "|" + fillId;
        }
        return fill.getOrderId() + "|" + fill.getTime() + "|" + fill.getPrice() + "|" + fill.getSize();
    }

    protected List<JsonNode> feedItems(JsonNode message) {
        List<JsonNode> items = new ArrayList<>();
        if (message == null) {
            return items;
        }
        JsonNode feed = message.has("feed") ? message.path("feed") : message;
        if (feed.isArray()) {
            feed.forEach(items::add);
        } else if (!feed.isMissingNode() && !feed.isNull()) {
            items.add(feed);
        }
        return items;
    }

    protected BrokerRequestResult notConnected() {
        return new BrokerRequestResult(false, false, "Broker is not connected",
                BrokerRequestResult.FailureType.UNKNOWN);
    }

    protected BrokerRequestResult validationFailure(String message) {
        return new BrokerRequestResult(false, true, message, BrokerRequestResult.FailureType.VALIDATION_FAILED);
    }

    protected BigDecimal decimalOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText("");
        return parseDecimal(value);
    }

    protected BigDecimal decimalAt(JsonNode arrayNode, int index) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.size() <= index) {
            return null;
        }
        return parseDecimal(arrayNode.path(index).asText(""));
    }

    protected BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected String textOf(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    protected String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right == null ? "" : right;
    }

    protected ZonedDateTime toTimestamp(String nanos) {
        if (nanos == null || nanos.isBlank()) {
            return ZonedDateTime.now(UTC);
        }
        try {
            long epochNanos = Long.parseLong(nanos.trim());
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochNanos / 1_000_000L), UTC);
        } catch (NumberFormatException e) {
            return ZonedDateTime.now(UTC);
        }
    }

    @SuppressWarnings("unused")
    private static String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.US);
    }
}
