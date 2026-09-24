package com.fueledbychai.broker.qfex;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.qfex.common.api.IQfexRestApi;
import com.fueledbychai.qfex.common.api.IQfexWebSocketApi;
import com.fueledbychai.qfex.common.api.QfexConfiguration;
import com.fueledbychai.qfex.common.api.ws.QfexTradeListener;
import com.fueledbychai.qfex.common.api.ws.QfexTradeStream;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

/**
 * QFEX broker. All order entry goes over the authenticated trade socket;
 * every call blocks until the venue answers (or the configured timeout).
 *
 * <p>Venue behaviours callers should know about:
 * <ul>
 * <li>A successful modify returns a <b>new</b> order id; the ticket is updated
 * in place. Partially filled orders cannot be modified.</li>
 * <li>Stops ({@code STOP_LOSS}) are cancelled by {@code stop_order_id}, which
 * is the order id returned on placement.</li>
 * <li>{@code cancelAllOrders} is fire-and-forget: the venue answers with one
 * CANCELLED order_response per order, delivered as order events.</li>
 * </ul>
 */
public class QfexBroker extends AbstractBasicBroker implements QfexTradeListener {

    private static final Logger log = LoggerFactory.getLogger(QfexBroker.class);
    private static final long CONNECT_TIMEOUT_MS = 15_000;

    protected final IQfexRestApi restApi;
    protected final IQfexWebSocketApi webSocketApi;
    protected final QfexTradeStream trade;
    protected final long timeoutMillis;
    protected final AtomicLong nextClientOrderId = new AtomicLong(System.currentTimeMillis());
    /** Venue symbol -> position, fed by position_response. */
    protected final Map<String, Position> positions = new ConcurrentHashMap<>();
    protected final Map<String, Ticker> tickers = new ConcurrentHashMap<>();
    private volatile CountDownLatch authLatch = new CountDownLatch(1);

    public QfexBroker() {
        this(ExchangeRestApiFactory.getApi(Exchange.QFEX, IQfexRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.QFEX, IQfexWebSocketApi.class),
                QfexConfiguration.getInstance().getOrderTimeoutMillis());
    }

    protected QfexBroker(IQfexRestApi restApi, IQfexWebSocketApi webSocketApi, long timeoutMillis) {
        if (restApi == null || webSocketApi == null) {
            throw new IllegalArgumentException("restApi and webSocketApi are required");
        }
        if (webSocketApi.trade() == null) {
            throw new IllegalStateException("QFEX broker requires " + QfexConfiguration.QFEX_API_PUBLIC_KEY + " and "
                    + QfexConfiguration.QFEX_API_SECRET_KEY);
        }
        this.restApi = restApi;
        this.webSocketApi = webSocketApi;
        this.trade = webSocketApi.trade();
        this.timeoutMillis = timeoutMillis;
        this.trade.addListener(this);
    }

    @Override
    public String getBrokerName() {
        return "QFEX";
    }

    @Override
    public void connect() {
        authLatch = new CountDownLatch(1);
        loadPositions();
        webSocketApi.connectOrderEntryWebSocket();
        try {
            if (!authLatch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("QFEX trade socket not authenticated after {} ms; will keep retrying", CONNECT_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void onDisconnect() {
        trade.stop();
    }

    @Override
    public boolean isConnected() {
        return trade.isAuthenticated();
    }

    @Override
    public BrokerStatus getBrokerStatus() {
        return isConnected() ? BrokerStatus.OK : BrokerStatus.DOWN;
    }

    @Override
    public String getNextOrderId() {
        return String.valueOf(nextClientOrderId.incrementAndGet());
    }

    // ------------------------------------------------------------------
    // Order entry
    // ------------------------------------------------------------------

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        if (order == null || order.getTicker() == null) {
            return invalid("order with ticker is required");
        }
        // OrderTicket defaults the client id to "", not null.
        if (order.getClientOrderId() == null || order.getClientOrderId().isBlank()) {
            order.setClientOrderId(getNextOrderId());
        }
        ObjectNode msg;
        try {
            msg = QfexTranslator.addOrder(order);
        } catch (IllegalArgumentException e) {
            return invalid(e.getMessage());
        }
        tickers.put(order.getTicker().getSymbol(), order.getTicker());
        orderRegistry.addOpenOrder(order);
        JsonNode params = msg.get("params");
        JsonNode reply = await(trade.request(msg, QfexTradeStream.ORDER_RESPONSE, matchesAdd(params)));
        if (reply == null) {
            return new BrokerRequestResult(false, true, "no response from QFEX", BrokerRequestResult.FailureType.UNKNOWN);
        }
        if (reply.has("err")) {
            return rejected(reply.get("err").path("error_code").asText("err") + ": "
                    + reply.get("err").path("message").asText(""));
        }
        String status = reply.path("status").asText("");
        order.setOrderId(reply.path("order_id").asText(null));
        orderRegistry.addOpenOrder(order);
        if (!QfexTranslator.isAccepted(status)) {
            orderRegistry.addCompletedOrder(order);
            return rejected(status);
        }
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        if (order == null || order.getOrderId() == null) {
            return invalid("order with orderId is required");
        }
        if (QfexTranslator.isStop(order)) {
            // Stops use a separate modify message whose reply is not
            // documented; cancel and re-place instead.
            BrokerRequestResult cancel = cancelOrder(order);
            if (!cancel.isSuccess()) {
                return cancel;
            }
            order.setOrderId(null);
            order.setClientOrderId(getNextOrderId());
            return placeOrder(order);
        }
        String oldId = order.getOrderId();
        ObjectNode msg = QfexTranslator.modifyOrder(order);
        JsonNode params = msg.get("params");
        Predicate<JsonNode> matcher = r -> oldId.equals(r.path("order_id").asText())
                || ("MODIFIED".equals(r.path("status").asText())
                        && sameOrderShape(r, params));
        JsonNode reply = await(trade.request(msg, QfexTradeStream.ORDER_RESPONSE, matcher));
        if (reply == null) {
            return new BrokerRequestResult(false, true, "no response from QFEX", BrokerRequestResult.FailureType.UNKNOWN);
        }
        if (reply.has("err")) {
            return rejected(reply.get("err").path("error_code").asText("err"));
        }
        String status = reply.path("status").asText("");
        if (!"MODIFIED".equals(status)) {
            return rejected(status);
        }
        order.setOrderId(reply.path("order_id").asText(oldId));
        orderRegistry.addOpenOrder(order);
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        if (order == null || order.getOrderId() == null || order.getTicker() == null) {
            return new BrokerRequestResult(false, false, "order with orderId and ticker is required",
                    BrokerRequestResult.FailureType.ORDER_NOT_FOUND);
        }
        String id = order.getOrderId();
        JsonNode reply = await(trade.request(QfexTranslator.cancelOrder(order), QfexTradeStream.ORDER_RESPONSE,
                r -> id.equals(r.path("order_id").asText())));
        if (reply == null) {
            return new BrokerRequestResult(false, true, "no response from QFEX", BrokerRequestResult.FailureType.UNKNOWN);
        }
        if (reply.has("err")) {
            return new BrokerRequestResult(false, true, reply.get("err").toString(),
                    BrokerRequestResult.FailureType.UNKNOWN);
        }
        String status = reply.path("status").asText("");
        if ("CANCELLED".equals(status)) {
            order.setCurrentStatus(OrderStatus.Status.CANCELED);
            orderRegistry.addCompletedOrder(order);
            return new BrokerRequestResult();
        }
        if ("FILLED".equals(status)) {
            return new BrokerRequestResult(false, false, status, BrokerRequestResult.FailureType.ORDER_ALREADY_FILLED);
        }
        return new BrokerRequestResult(false, false, status, BrokerRequestResult.FailureType.ORDER_NOT_OPEN);
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        OrderTicket order = orderRegistry.getOrderById(id);
        return order == null
                ? new BrokerRequestResult(false, false, "unknown order " + id, BrokerRequestResult.FailureType.ORDER_NOT_FOUND)
                : cancelOrder(order);
    }

    @Override
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        OrderTicket order = orderRegistry.getOrderByClientId(clientOrderId);
        return order == null
                ? new BrokerRequestResult(false, false, "unknown client order " + clientOrderId,
                        BrokerRequestResult.FailureType.ORDER_NOT_FOUND)
                : cancelOrder(order);
    }

    @Override
    public BrokerRequestResult cancelOrders(List<OrderTicket> orders) {
        BrokerRequestResult last = new BrokerRequestResult();
        for (OrderTicket o : orders) {
            BrokerRequestResult r = cancelOrder(o);
            if (!r.isSuccess()) {
                last = r;
            }
        }
        return last;
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        return trade.fire(QfexTranslator.cancelAll(null)) ? new BrokerRequestResult()
                : new BrokerRequestResult(false, true, "not connected", BrokerRequestResult.FailureType.UNKNOWN);
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        return trade.fire(QfexTranslator.cancelAll(ticker.getSymbol())) ? new BrokerRequestResult()
                : new BrokerRequestResult(false, true, "not connected", BrokerRequestResult.FailureType.UNKNOWN);
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        cancelOrder(originalOrderId);
        placeOrder(newOrder);
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        return orderRegistry.getOrderById(orderId);
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        return orderRegistry.getOrderByClientId(clientOrderId);
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        JsonNode reply = await(trade.request(QfexTranslator.getUserOrders(500, 0),
                QfexTradeStream.ALL_ORDERS_RESPONSE, r -> true));
        List<OrderTicket> out = new ArrayList<>();
        if (reply == null || reply.has("err")) {
            return out;
        }
        for (JsonNode n : reply.path("orders")) {
            out.add(QfexTranslator.toTicket(n, ticker(n.path("symbol").asText())));
        }
        return out;
    }

    @Override
    public List<Position> getAllPositions() {
        return new ArrayList<>(positions.values());
    }

    // ------------------------------------------------------------------
    // Trade socket callbacks
    // ------------------------------------------------------------------

    @Override
    public void onAuthenticated() {
        authLatch.countDown();
    }

    @Override
    public void onOrderResponse(JsonNode r) {
        String orderId = r.path("order_id").asText(null);
        String clientId = r.path("client_order_id").asText("");
        OrderTicket order = orderId == null ? null : orderRegistry.getOrderById(orderId);
        if (order == null && !clientId.isBlank()) {
            order = orderRegistry.getOrderByClientId(clientId);
        }
        BigDecimal remaining = QfexTranslator.decimal(r, "quantity_remaining");
        BigDecimal quantity = QfexTranslator.decimal(r, "quantity");
        OrderStatus.Status status = QfexTranslator.toStatus(r.path("status").asText(null), remaining);
        Ticker ticker = order != null ? order.getTicker() : ticker(r.path("symbol").asText());
        if (order != null) {
            order.setCurrentStatus(status);
            if (status == OrderStatus.Status.FILLED || status == OrderStatus.Status.CANCELED
                    || status == OrderStatus.Status.REJECTED) {
                orderRegistry.addCompletedOrder(order);
            }
        }
        BigDecimal filled = quantity != null && remaining != null ? quantity.subtract(remaining).max(BigDecimal.ZERO) : null;
        OrderStatus s = new OrderStatus(status, orderId, filled, remaining, QfexTranslator.decimal(r, "price"), ticker,
                ZonedDateTime.now(ZoneOffset.UTC));
        s.setClientOrderId(clientId.isBlank() ? (order == null ? null : order.getClientOrderId()) : clientId);
        fireOrderEvent(new OrderEvent(order, s));
    }

    @Override
    public void onFill(JsonNode f) {
        fireFillEvent(QfexTranslator.toFill(f, ticker(f.path("symbol").asText())));
    }

    @Override
    public void onPosition(JsonNode p) {
        String symbol = p.path("symbol").asText(null);
        if (symbol == null) {
            return;
        }
        BigDecimal signed = QfexTranslator.decimal(p, "position");
        if (signed == null || signed.signum() == 0) {
            positions.remove(symbol);
            return;
        }
        Position pos = new Position(ticker(symbol), signed.signum() > 0 ? Side.LONG : Side.SHORT, signed.abs(),
                QfexTranslator.decimal(p, "average_price"), Position.Status.OPEN);
        positions.put(symbol, pos);
    }

    @Override
    public void onBalance(JsonNode b) {
        BigDecimal available = QfexTranslator.decimal(b, "available_balance");
        if (available != null) {
            fireAvailableFundsUpdated(available.doubleValue());
        }
        BigDecimal equity = sum(b, "deposit", "realised_pnl", "unrealised_pnl", "net_funding");
        if (equity != null) {
            fireAccountEquityUpdated(equity.doubleValue());
        }
    }

    // ------------------------------------------------------------------

    private void loadPositions() {
        if (restApi.isPublicApiOnly()) {
            return;
        }
        try {
            JsonNode root = restApi.getPositions();
            for (JsonNode p : root.path("positions")) {
                onPosition(p);
            }
        } catch (RuntimeException e) {
            log.warn("QFEX initial position load failed: {}", e.getMessage());
        }
    }

    private Ticker ticker(String symbol) {
        return tickers.computeIfAbsent(symbol, s -> new Ticker(s).setExchange(Exchange.QFEX));
    }

    /**
     * Matches the reply to an add_order: by client order id, or — for replies
     * that omit it, as the venue's stop-order example does — by the order's
     * symbol, side, type, price and quantity.
     */
    static Predicate<JsonNode> matchesAdd(JsonNode sent) {
        String clientId = sent.path("client_order_id").asText("");
        return r -> {
            String rc = r.path("client_order_id").asText("");
            if (!rc.isBlank()) {
                return rc.equals(clientId);
            }
            return sent.path("order_type").asText().equals(r.path("type").asText())
                    && sameOrderShape(r, sent);
        };
    }

    static boolean sameOrderShape(JsonNode reply, JsonNode sent) {
        return sent.path("symbol").asText().equals(reply.path("symbol").asText())
                && sent.path("side").asText().equals(reply.path("side").asText())
                && sameNumber(sent.path("price"), reply.path("price"))
                && sameNumber(sent.path("quantity"), reply.path("quantity"));
    }

    private static boolean sameNumber(JsonNode a, JsonNode b) {
        try {
            return new BigDecimal(a.asText()).compareTo(new BigDecimal(b.asText())) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static BigDecimal sum(JsonNode n, String... fields) {
        BigDecimal total = null;
        for (String f : fields) {
            BigDecimal v = QfexTranslator.decimal(n, f);
            if (v != null) {
                total = total == null ? v : total.add(v);
            }
        }
        return total;
    }

    private JsonNode await(CompletableFuture<JsonNode> future) {
        try {
            return future.get(timeoutMillis + 1_000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("QFEX request failed: {}", e.getMessage());
            return null;
        }
    }

    private static BrokerRequestResult invalid(String message) {
        return new BrokerRequestResult(false, false, message, BrokerRequestResult.FailureType.VALIDATION_FAILED);
    }

    private static BrokerRequestResult rejected(String status) {
        return new BrokerRequestResult(false, false, status, BrokerRequestResult.FailureType.VALIDATION_FAILED);
    }
}
