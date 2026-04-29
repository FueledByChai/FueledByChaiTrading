package com.fueledbychai.broker.hibachi;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;
import com.fueledbychai.hibachi.common.api.HibachiConfiguration;
import com.fueledbychai.hibachi.common.api.HibachiContract;
import com.fueledbychai.hibachi.common.api.IHibachiRestApi;
import com.fueledbychai.hibachi.common.api.signer.HibachiSignerFactory;
import com.fueledbychai.hibachi.common.api.signer.IHibachiSigner;
import com.fueledbychai.time.Span;
import com.fueledbychai.util.ExchangeRestApiFactory;

public class HibachiBroker extends AbstractBasicBroker {

    private static final Logger logger = LoggerFactory.getLogger(HibachiBroker.class);
    protected static final String LATENCY_LOGGER = "latency.hibachi";

    protected final IHibachiRestApi restApi;
    protected final HibachiConfiguration config;
    protected final HibachiTranslator translator;
    protected final IHibachiSigner signer;
    protected final long accountId;

    protected final HibachiTradeWebSocketClient tradeWs;
    protected final HibachiAccountStreamClient accountWs;
    protected final AtomicLong nextClientOrderId = new AtomicLong(System.currentTimeMillis());
    protected final Map<String, Position> positionsCache = new ConcurrentHashMap<>();
    protected volatile boolean connected;

    public HibachiBroker() {
        this(ExchangeRestApiFactory.getPrivateApi(Exchange.HIBACHI, IHibachiRestApi.class),
                HibachiConfiguration.getInstance(),
                new HibachiTranslator(),
                HibachiSignerFactory.create());
    }

    public HibachiBroker(IHibachiRestApi restApi, HibachiConfiguration config,
                         HibachiTranslator translator, IHibachiSigner signer) {
        if (restApi == null) throw new IllegalArgumentException("restApi is required");
        if (config == null) throw new IllegalArgumentException("config is required");
        if (translator == null) throw new IllegalArgumentException("translator is required");
        if (signer == null) throw new IllegalArgumentException("signer is required");
        this.restApi = restApi;
        this.config = config;
        this.translator = translator;
        this.signer = signer;
        this.accountId = parseAccountId(config.getAccountId());
        this.tradeWs = new HibachiTradeWebSocketClient(config, accountId, config.getApiKey());
        this.accountWs = new HibachiAccountStreamClient(config, accountId, config.getApiKey());
        this.accountWs.setEventListener(new AccountListener());
    }

    @Override
    public String getBrokerName() {
        return "Hibachi";
    }

    @Override
    public void connect() {
        if (connected) {
            return;
        }
        try {
            tradeWs.connect();
            accountWs.connect();
            // Eagerly warm contract cache; the trade-WS signer needs contractIds.
            restApi.getAllContracts();
            connected = true;
            logger.info("Hibachi broker connected for accountId={}", accountId);
        } catch (Exception e) {
            connected = false;
            try { tradeWs.disconnect(); } catch (Exception ignored) {}
            try { accountWs.disconnect(); } catch (Exception ignored) {}
            throw new IllegalStateException("Failed to connect Hibachi broker", e);
        }
    }

    @Override
    protected void onDisconnect() {
        connected = false;
        try { tradeWs.disconnect(); } catch (Exception ignored) {}
        try { accountWs.disconnect(); } catch (Exception ignored) {}
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
    public BrokerRequestResult placeOrder(OrderTicket order) {
        checkConnected();
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        try (var s = Span.start("HB_PLACE_ORDER", order.getClientOrderId(), LATENCY_LOGGER)) {
            String symbol = order.getTicker().getSymbol();
            HibachiContract contract = restApi.getContract(symbol);
            if (contract == null) {
                return new BrokerRequestResult(false, true, "Unknown contract: " + symbol,
                        BrokerRequestResult.FailureType.VALIDATION_FAILED);
            }
            long nonce = nextNonce();
            HibachiTranslator.SignedRequest request = translator.translatePlace(
                    order, contract, accountId, nonce, config.getOrderMaxFeesPercent(), signer);
            orderRegistry.addOpenOrder(order);
            JsonNode response = tradeWs.placeOrder(request.params, request.signature, order.getClientOrderId());
            BrokerRequestResult result = interpretResponse(response, "placeOrder");
            if (result.isSuccess()) {
                String exchangeOrderId = response.path("result").path("orderId").asText(null);
                if (exchangeOrderId != null && !exchangeOrderId.isBlank()) {
                    order.setOrderId(exchangeOrderId);
                    orderRegistry.addOpenOrder(order);
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Hibachi placeOrder failed", e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        checkConnected();
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        try (var s = Span.start("HB_MODIFY_ORDER", order.getClientOrderId(), LATENCY_LOGGER)) {
            String symbol = order.getTicker().getSymbol();
            HibachiContract contract = restApi.getContract(symbol);
            if (contract == null) {
                return new BrokerRequestResult(false, true, "Unknown contract: " + symbol,
                        BrokerRequestResult.FailureType.VALIDATION_FAILED);
            }
            long nonce = nextNonce();
            HibachiTranslator.SignedRequest request = translator.translateModify(
                    order, contract, accountId, nonce, config.getOrderMaxFeesPercent(), signer);
            JsonNode response = tradeWs.modifyOrder(request.params, request.signature, order.getClientOrderId());
            return interpretResponse(response, "modifyOrder");
        } catch (Exception e) {
            logger.error("Hibachi modifyOrder failed", e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        checkConnected();
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        String traceId = order.getOrderId() != null ? order.getOrderId() : order.getClientOrderId();
        try (var s = Span.start("HB_CANCEL_ORDER", traceId, LATENCY_LOGGER)) {
            HibachiTranslator.SignedRequest request = translator.translateCancel(
                    order, accountId, nextNonce(), signer);
            JsonNode response = tradeWs.cancelOrder(request.params, request.signature, traceId);
            return interpretResponse(response, "cancelOrder");
        } catch (Exception e) {
            logger.error("Hibachi cancelOrder failed", e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        if (id == null || id.isBlank()) {
            return new BrokerRequestResult(false, true, "id is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        OrderTicket existing = orderRegistry.getOrderById(id);
        if (existing == null) {
            return new BrokerRequestResult(false, true, "Order not found: " + id,
                    BrokerRequestResult.FailureType.ORDER_NOT_FOUND);
        }
        return cancelOrder(existing);
    }

    @Override
    public String getNextOrderId() {
        return String.valueOf(nextClientOrderId.incrementAndGet());
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        OrderTicket cached = orderRegistry.getOrderById(orderId);
        if (!isConnected()) {
            return cached;
        }
        try (var s = Span.start("HB_REQUEST_ORDER_STATUS", orderId, LATENCY_LOGGER)) {
            JsonNode response = restApi.getOrder(orderId);
            if (response == null || response.isNull() || response.isMissingNode()) {
                return cached;
            }
            applyOrderUpdateToRegistry(response);
            return orderRegistry.getOrderById(orderId);
        } catch (Exception e) {
            logger.warn("Hibachi requestOrderStatus({}) failed; returning cached", orderId, e);
            return cached;
        }
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        // Best-effort: rely on registry. A production implementation should reconcile
        // against restApi.getOpenOrders() periodically.
        List<OrderTicket> open = new ArrayList<>();
        if (orderRegistry != null) {
            orderRegistry.getOpenOrders().forEach(open::add);
        }
        return open;
    }

    @Override
    public List<Position> getAllPositions() {
        return new ArrayList<>(positionsCache.values());
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        cancelOrder(originalOrderId);
        placeOrder(newOrder);
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        checkConnected();
        long nonce = nextNonce();
        try (var s = Span.start("HB_CANCEL_ALL_ORDERS", String.valueOf(nonce), LATENCY_LOGGER)) {
            byte[] bytes = com.fueledbychai.hibachi.common.api.signer.HibachiPayloadPacker
                    .packCancelAll(nonce);
            String signature = signer.sign(bytes);
            JsonNode response = tradeWs.cancelAll(nonce, signature);
            return interpretResponse(response, "cancelAllOrders");
        } catch (Exception e) {
            logger.error("Hibachi cancelAllOrders failed", e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        checkConnected();
        if (ticker == null) {
            return cancelAllOrders();
        }
        String symbol = ticker.getSymbol();
        List<OrderTicket> toCancel = new ArrayList<>();
        for (OrderTicket open : getOpenOrders()) {
            if (open != null && open.getTicker() != null
                    && symbol.equals(open.getTicker().getSymbol())) {
                toCancel.add(open);
            }
        }
        if (toCancel.isEmpty()) {
            return new BrokerRequestResult();
        }
        return cancelOrders(toCancel);
    }

    @Override
    public BrokerRequestResult cancelOrders(java.util.List<OrderTicket> orders) {
        checkConnected();
        if (orders == null || orders.isEmpty()) {
            return new BrokerRequestResult();
        }

        List<OrderTicket> targets = new ArrayList<>(orders.size());
        for (OrderTicket o : orders) {
            if (o == null) {
                continue;
            }
            if ((o.getOrderId() == null || o.getOrderId().isBlank())
                    && (o.getClientOrderId() == null || o.getClientOrderId().isBlank())) {
                logger.warn("Skipping order with no orderId or clientOrderId: {}", o);
                continue;
            }
            targets.add(o);
        }
        if (targets.isEmpty()) {
            return new BrokerRequestResult(false, true, "No identifiable orders to cancel",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        logger.info("Hibachi batch cancel: fanning out {} cancels in parallel", targets.size());
        try (var s = Span.start("HB_CANCEL_ORDERS_BATCH", String.valueOf(targets.size()), LATENCY_LOGGER);
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<BrokerRequestResult>> futures = new ArrayList<>(targets.size());
            for (OrderTicket order : targets) {
                futures.add(executor.submit(() -> cancelOrder(order)));
            }

            int failures = 0;
            String firstFailure = null;
            for (int i = 0; i < futures.size(); i++) {
                BrokerRequestResult r;
                try {
                    r = futures.get(i).get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new BrokerRequestResult(false, true, "Interrupted while batch-canceling",
                            BrokerRequestResult.FailureType.UNKNOWN);
                } catch (ExecutionException ee) {
                    failures++;
                    if (firstFailure == null) {
                        firstFailure = ee.getCause() != null ? ee.getCause().getMessage() : ee.getMessage();
                    }
                    continue;
                }
                if (!r.isSuccess()) {
                    failures++;
                    if (firstFailure == null) {
                        firstFailure = r.getMessage();
                    }
                }
            }

            if (failures == 0) {
                return new BrokerRequestResult();
            }
            return new BrokerRequestResult(false, true,
                    failures + "/" + targets.size() + " cancels failed; first error: " + firstFailure,
                    BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    protected BrokerRequestResult interpretResponse(JsonNode response, String op) {
        if (response == null) {
            return new BrokerRequestResult(false, true, op + " returned null", BrokerRequestResult.FailureType.UNKNOWN);
        }
        if (response.has("error") && !response.path("error").isNull()) {
            String msg = response.path("error").toString();
            return new BrokerRequestResult(false, true, msg, BrokerRequestResult.FailureType.UNKNOWN);
        }
        return new BrokerRequestResult();
    }

    protected long nextNonce() {
        // Microseconds since epoch, matches Python SDK (api.py:1315 etc.).
        return System.currentTimeMillis() * 1_000L;
    }

    protected void checkConnected() {
        if (!connected) {
            throw new IllegalStateException("Hibachi broker is not connected");
        }
    }

    private static long parseAccountId(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + HibachiConfiguration.HIBACHI_ACCOUNT_ID + ": " + value, e);
        }
    }

    /** Bridges parsed account-WS frames into the broker's registry, listeners, and cache. */
    protected class AccountListener implements HibachiAccountEventListener {

        @Override
        public void onAccountSnapshot(JsonNode snapshot) {
            if (snapshot == null || snapshot.isMissingNode() || snapshot.isNull()) {
                return;
            }
            JsonNode positions = snapshot.path("positions");
            if (positions.isArray()) {
                positionsCache.clear();
                for (JsonNode p : positions) {
                    Position pos = parsePosition(p);
                    if (pos != null && pos.getTicker() != null) {
                        positionsCache.put(pos.getTicker().getSymbol(), pos);
                    }
                }
            }
            applyBalance(snapshot);
        }

        @Override
        public void onPositionUpdate(JsonNode frame) {
            JsonNode body = bodyOf(frame);
            Position pos = parsePosition(body);
            if (pos == null || pos.getTicker() == null) {
                return;
            }
            BigDecimal size = pos.getSize();
            if (size == null || size.signum() == 0) {
                positionsCache.remove(pos.getTicker().getSymbol());
            } else {
                positionsCache.put(pos.getTicker().getSymbol(), pos);
            }
        }

        @Override
        public void onBalanceUpdate(JsonNode frame) {
            applyBalance(bodyOf(frame));
        }

        @Override
        public void onOrderUpdate(JsonNode frame) {
            applyOrderUpdateToRegistry(bodyOf(frame));
        }

        @Override
        public void onFill(JsonNode frame) {
            Fill fill = parseFill(bodyOf(frame));
            if (fill == null) {
                return;
            }
            fireFillEvent(fill);
        }
    }

    protected void applyBalance(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return;
        }
        BigDecimal equity = readDecimal(body, "equity", "totalEquity", "accountEquity");
        if (equity != null) {
            fireAccountEquityUpdated(equity.doubleValue());
        }
        BigDecimal available = readDecimal(body, "availableFunds", "availableBalance", "balance", "freeBalance");
        if (available != null) {
            fireAvailableFundsUpdated(available.doubleValue());
        }
    }

    protected void applyOrderUpdateToRegistry(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return;
        }
        String exchangeOrderId = textOrNull(body, "orderId", "id");
        String clientOrderId = textOrNull(body, "clientOrderId", "clientId", "nonce");
        OrderTicket order = null;
        if (exchangeOrderId != null) {
            order = orderRegistry.getOrderById(exchangeOrderId);
        }
        if (order == null && clientOrderId != null) {
            order = orderRegistry.getOrderByClientId(clientOrderId);
        }
        if (order == null) {
            logger.debug("Hibachi order update for unknown id={} clientId={} body={}",
                    exchangeOrderId, clientOrderId, body);
            return;
        }
        if (exchangeOrderId != null && order.getOrderId() == null) {
            order.setOrderId(exchangeOrderId);
        }

        BigDecimal filled = readDecimal(body, "filledQuantity", "executedQuantity", "filled");
        BigDecimal remaining = readDecimal(body, "remainingQuantity", "remaining");
        if (filled == null) {
            filled = order.getFilledSize() != null ? order.getFilledSize() : BigDecimal.ZERO;
        }
        if (remaining == null && order.getSize() != null) {
            remaining = order.getSize().subtract(filled);
        }
        BigDecimal fillPrice = readDecimal(body, "averagePrice", "avgFillPrice", "price");

        OrderStatus.Status status = parseOrderStatus(textOrNull(body, "status", "orderStatus"));
        if (status == null) {
            return;
        }
        order.setCurrentStatus(status);
        if (filled != null) {
            order.setFilledSize(filled);
        }
        if (fillPrice != null) {
            order.setFilledPrice(fillPrice);
        }
        OrderStatus s = new OrderStatus(status, order.getOrderId(), filled, remaining,
                fillPrice, order.getTicker(), ZonedDateTime.now(ZoneOffset.UTC));
        s.setClientOrderId(order.getClientOrderId());
        fireOrderEvent(new OrderEvent(order, s));
    }

    protected Fill parseFill(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return null;
        }
        String symbol = textOrNull(body, "symbol");
        Ticker ticker = lookupTicker(symbol);
        if (ticker == null) {
            return null;
        }
        Fill fill = new Fill();
        fill.setTicker(ticker);
        fill.setOrderId(textOrNull(body, "orderId"));
        fill.setClientOrderId(textOrNull(body, "clientOrderId", "clientId"));
        fill.setFillId(textOrNull(body, "fillId", "tradeId", "id"));
        fill.setPrice(readDecimal(body, "price", "fillPrice"));
        fill.setSize(readDecimal(body, "quantity", "size", "filledQuantity"));
        fill.setCommission(readDecimal(body, "fee", "commission"));
        String side = textOrNull(body, "side", "direction");
        if (side != null) {
            String s = side.toUpperCase();
            if (s.equals("BID") || s.equals("BUY") || s.equals("LONG")) {
                fill.setSide(TradeDirection.BUY);
            } else if (s.equals("ASK") || s.equals("SELL") || s.equals("SHORT")) {
                fill.setSide(TradeDirection.SELL);
            }
        }
        Long ts = readLong(body, "timestamp", "time", "tradeTime");
        if (ts != null) {
            // Heuristic: Hibachi nonces are microseconds; trade timestamps appear in millis.
            long millis = ts > 1_000_000_000_000_000L ? ts / 1_000L : ts;
            fill.setTime(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneOffset.UTC));
        } else {
            fill.setTime(ZonedDateTime.now(ZoneOffset.UTC));
        }
        return fill;
    }

    protected Position parsePosition(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return null;
        }
        String symbol = textOrNull(body, "symbol");
        Ticker ticker = lookupTicker(symbol);
        if (ticker == null) {
            return null;
        }
        Position pos = new Position(ticker);
        BigDecimal size = readDecimal(body, "quantity", "size");
        if (size != null) {
            pos.setSize(size.abs());
        }
        BigDecimal avgPrice = readDecimal(body, "openPrice", "averagePrice", "entryPrice");
        if (avgPrice != null) {
            pos.setAverageCost(avgPrice);
        }
        String direction = textOrNull(body, "direction", "side");
        if (direction != null) {
            String d = direction.toUpperCase();
            if (d.equals("LONG") || d.equals("BUY") || d.equals("BID")) {
                pos.setSide(Side.LONG);
            } else if (d.equals("SHORT") || d.equals("SELL") || d.equals("ASK")) {
                pos.setSide(Side.SHORT);
            }
        } else if (size != null && size.signum() < 0) {
            pos.setSide(Side.SHORT);
        }
        return pos;
    }

    protected Ticker lookupTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.HIBACHI);
        Ticker t = registry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (t == null) {
            t = registry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        }
        return t;
    }

    protected static OrderStatus.Status parseOrderStatus(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.toUpperCase()) {
            case "NEW":
            case "PLACED":
            case "OPEN":
            case "ACCEPTED":
                return OrderStatus.Status.NEW;
            case "PARTIALLY_FILLED":
            case "PARTIAL_FILL":
            case "PARTIAL":
                return OrderStatus.Status.PARTIAL_FILL;
            case "FILLED":
            case "FULLY_FILLED":
                return OrderStatus.Status.FILLED;
            case "CANCELED":
            case "CANCELLED":
                return OrderStatus.Status.CANCELED;
            case "PENDING_CANCEL":
                return OrderStatus.Status.PENDING_CANCEL;
            case "REJECTED":
                return OrderStatus.Status.REJECTED;
            case "REPLACED":
            case "MODIFIED":
                return OrderStatus.Status.REPLACED;
            default:
                return OrderStatus.Status.UNKNOWN;
        }
    }

    protected static JsonNode bodyOf(JsonNode frame) {
        if (frame == null) {
            return null;
        }
        if (frame.has("data") && !frame.path("data").isMissingNode()) {
            return frame.path("data");
        }
        if (frame.has("params") && !frame.path("params").isMissingNode()) {
            return frame.path("params");
        }
        if (frame.has("result") && !frame.path("result").isMissingNode()) {
            return frame.path("result");
        }
        return frame;
    }

    protected static String textOrNull(JsonNode body, String... fields) {
        if (body == null) {
            return null;
        }
        for (String f : fields) {
            JsonNode v = body.path(f);
            if (!v.isMissingNode() && !v.isNull()) {
                String s = v.asText(null);
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    protected static BigDecimal readDecimal(JsonNode body, String... fields) {
        if (body == null) {
            return null;
        }
        for (String f : fields) {
            JsonNode v = body.path(f);
            if (v.isMissingNode() || v.isNull()) continue;
            if (v.isNumber()) {
                return v.decimalValue();
            }
            String s = v.asText(null);
            if (s != null && !s.isBlank()) {
                try {
                    return new BigDecimal(s);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    protected static Long readLong(JsonNode body, String... fields) {
        if (body == null) {
            return null;
        }
        for (String f : fields) {
            JsonNode v = body.path(f);
            if (v.isMissingNode() || v.isNull()) continue;
            if (v.canConvertToLong()) {
                return v.asLong();
            }
            String s = v.asText(null);
            if (s != null && !s.isBlank()) {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
