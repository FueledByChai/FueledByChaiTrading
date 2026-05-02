package com.fueledbychai.broker.hibachi;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    protected final Map<String, PendingPlace> pendingPlaces = new ConcurrentHashMap<>();
    /**
     * How long {@link #placeOrder} waits after the WS gateway ack for a risk-engine rejection
     * to arrive on the account WS. Rejections in practice land within ~10ms; we give a
     * generous margin without adding noticeable latency to successful places.
     */
    protected static final long PLACE_REJECTION_WAIT_MILLIS = 500L;
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
        this.tradeWs.setConnectionStateListener(this::onWsStateChanged);
        this.accountWs.setConnectionStateListener(this::onWsStateChanged);
    }

    protected void onWsStateChanged(boolean ignored) {
        // We don't keep a per-WS flag here; isConnected() polls the WS clients directly.
        // The hook exists so subclasses (or future logic) can react to up/down transitions.
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
            tradeWs.setRawMessageListener(node -> logger.info("Hibachi trade WS raw <- {}", node));
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
        return connected && tradeWs.isConnected() && accountWs.isConnected();
    }

    @Override
    public BrokerStatus getBrokerStatus() {
        return isConnected() ? BrokerStatus.OK : BrokerStatus.UNKNOWN;
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
            logger.info("Hibachi placeOrder response: {}", response);
            BrokerRequestResult result = interpretResponse(response, "placeOrder");
            if (!result.isSuccess()) {
                return result;
            }
            String exchangeOrderId = response.path("result").path("orderId").asText(null);
            if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
                return new BrokerRequestResult(false, true, response.toString(),
                        BrokerRequestResult.FailureType.UNKNOWN);
            }
            order.setOrderId(exchangeOrderId);
            // Hibachi's WS gateway returns 200 + orderId before its risk engine validates
            // the order. Rejections (e.g. TooSmallNotionalValue) arrive ~10ms later as an
            // {"event":"order_request_rejected"} frame on the account WS. Wait briefly for
            // one before declaring the place a success.
            BrokerRequestResult asyncRejection = awaitAccountRejection(exchangeOrderId, order);
            if (asyncRejection != null) {
                return asyncRejection;
            }
            // Fall back to the REST verify in case the rejection arrived in some other shape
            // we haven't observed yet.
            BrokerRequestResult restRejection = verifyOrderLanded(exchangeOrderId);
            if (restRejection != null) {
                return restRejection;
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
        // Hibachi reports order failures in several shapes — check all of them, otherwise
        // a notional-too-small / risk-rejected order silently looks like a success.
        if (response.has("error") && !response.path("error").isNull() && !response.path("error").isMissingNode()) {
            return new BrokerRequestResult(false, true, response.path("error").toString(),
                    BrokerRequestResult.FailureType.UNKNOWN);
        }
        if (isFailedStatus(response) || isNonZeroErrorCode(response)) {
            return new BrokerRequestResult(false, true, response.toString(),
                    BrokerRequestResult.FailureType.UNKNOWN);
        }
        JsonNode result = response.path("result");
        if (!result.isMissingNode() && !result.isNull()
                && (isFailedStatus(result) || isNonZeroErrorCode(result))) {
            return new BrokerRequestResult(false, true, result.toString(),
                    BrokerRequestResult.FailureType.UNKNOWN);
        }
        return new BrokerRequestResult();
    }

    private static boolean isFailedStatus(JsonNode node) {
        JsonNode status = node.path("status");
        if (status.isMissingNode() || status.isNull()) {
            return false;
        }
        String s = status.asText("");
        return s.equalsIgnoreCase("failed") || s.equalsIgnoreCase("rejected") || s.equalsIgnoreCase("error");
    }

    /**
     * Waits up to {@link #PLACE_REJECTION_WAIT_MILLIS} for an {@code order_request_rejected}
     * frame on the account WS keyed to {@code exchangeOrderId}. Returns a failure result if
     * one arrives, {@code null} if the wait times out (i.e. the order is presumed live).
     */
    protected BrokerRequestResult awaitAccountRejection(String exchangeOrderId, OrderTicket order) {
        // Atomic install: if a rejection already arrived (listener stashed an "early" slot),
        // pick it up immediately without waiting; otherwise install our waiter so a rejection
        // arriving later wakes us up.
        PendingPlace pending = pendingPlaces.compute(exchangeOrderId, (k, existing) -> {
            if (existing != null) {
                existing.order = order;
                return existing;
            }
            return new PendingPlace(order);
        });
        if (pending.earlyRejection != null) {
            try {
                fireRejectedEvent(order, exchangeOrderId, pending.earlyRejection.reason);
                return new BrokerRequestResult(false, true, pending.earlyRejection.reason,
                        classifyRejection(pending.earlyRejection.reason));
            } finally {
                pendingPlaces.remove(exchangeOrderId);
            }
        }
        try {
            RejectionInfo info = pending.future.get(PLACE_REJECTION_WAIT_MILLIS, TimeUnit.MILLISECONDS);
            return new BrokerRequestResult(false, true, info.reason,
                    classifyRejection(info.reason));
        } catch (TimeoutException te) {
            return null;
        } catch (Exception e) {
            logger.debug("awaitAccountRejection({}) interrupted", exchangeOrderId, e);
            return null;
        } finally {
            pendingPlaces.remove(exchangeOrderId);
        }
    }

    /** Tracks an in-flight place-order so the account-WS rejection handler can find both
     *  the waiter and the original {@link OrderTicket}. {@code earlyRejection} carries a
     *  rejection that arrived before {@link #placeOrder} got around to installing the waiter
     *  (the trade-WS ack and account-WS rejection can interleave on different threads). */
    protected static final class PendingPlace {
        volatile OrderTicket order;
        final CompletableFuture<RejectionInfo> future = new CompletableFuture<>();
        volatile RejectionInfo earlyRejection;
        PendingPlace(OrderTicket order) { this.order = order; }
        PendingPlace() {}
    }

    private static BrokerRequestResult.FailureType classifyRejection(String reason) {
        if (reason == null) {
            return BrokerRequestResult.FailureType.UNKNOWN;
        }
        String r = reason.toLowerCase();
        // Check post-only first — Hibachi's "PostOnlyOrderDoesNotAddLiquidity" contains
        // "liquidity" which would otherwise match nothing meaningful.
        if (r.contains("postonly") || r.contains("post_only") || r.contains("liquidity")
                || r.contains("wouldcross") || r.contains("would_cross")) {
            return BrokerRequestResult.FailureType.POST_ONLY_REJECTED;
        }
        if (r.startsWith("toosmall") || r.contains("notional") || r.contains("size")
                || r.contains("quantity")) {
            return BrokerRequestResult.FailureType.INVALID_SIZE;
        }
        if (r.contains("price") || r.contains("tick")) {
            return BrokerRequestResult.FailureType.INVALID_PRICE;
        }
        // RiskLimitExceeded / margin / balance / position-too-large all map to "you don't
        // have the funds for this" from the caller's perspective.
        if (r.contains("margin") || r.contains("balance") || r.contains("fund")
                || r.contains("collateral") || r.contains("risk") || r.contains("limit")
                || r.contains("exceed") || r.contains("leverage")) {
            return BrokerRequestResult.FailureType.INSUFFICIENT_FUNDS;
        }
        return BrokerRequestResult.FailureType.UNKNOWN;
    }

    private static OrderStatus.CancelReason classifyCancelReason(String reason) {
        if (reason == null) {
            return OrderStatus.CancelReason.UNKNOWN;
        }
        String r = reason.toLowerCase();
        if (r.contains("postonly") || r.contains("post_only") || r.contains("liquidity")
                || r.contains("wouldcross") || r.contains("would_cross")) {
            return OrderStatus.CancelReason.POST_ONLY_WOULD_CROSS;
        }
        return OrderStatus.CancelReason.UNKNOWN;
    }

    /** Carries an account-WS rejection back to a waiting {@link #placeOrder}. */
    protected static final class RejectionInfo {
        final String reason;
        final JsonNode frame;
        RejectionInfo(String reason, JsonNode frame) {
            this.reason = reason;
            this.frame = frame;
        }
    }

    /**
     * After a successful WS place ack, asks the REST API for the order to confirm it's
     * actually on the book. Returns a failure {@link BrokerRequestResult} if Hibachi
     * reports the order as not-found (post-ack risk rejection) or {@code null} if the
     * order is live or the verification can't be performed.
     */
    protected BrokerRequestResult verifyOrderLanded(String orderId) {
        try {
            JsonNode verify = restApi.getOrder(orderId);
            if (isNotFoundResponse(verify)) {
                logger.warn("Hibachi rejected order {} after WS ack: {}", orderId, verify);
                return new BrokerRequestResult(false, true, verify.toString(),
                        BrokerRequestResult.FailureType.UNKNOWN);
            }
        } catch (Exception e) {
            logger.debug("Post-place verification for {} failed; assuming order is live", orderId, e);
        }
        return null;
    }

    private static boolean isNotFoundResponse(JsonNode response) {
        if (response == null || response.isNull() || response.isMissingNode()) {
            return false;
        }
        if (isFailedStatus(response)) {
            JsonNode code = response.path("errorCode");
            if (code.isNumber() && code.asInt() == 3) {
                return true;
            }
            String msg = response.path("message").asText("");
            return msg.toLowerCase().contains("not found");
        }
        return false;
    }

    private static boolean isNonZeroErrorCode(JsonNode node) {
        JsonNode code = node.path("errorCode");
        if (code.isMissingNode() || code.isNull()) {
            return false;
        }
        if (code.isNumber()) {
            return code.asLong() != 0L;
        }
        String s = code.asText("");
        return !s.isBlank() && !s.equals("0");
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

        @Override
        public void onOrderRejected(String orderId, String reason, JsonNode frame) {
            if (orderId == null || orderId.isBlank()) {
                logger.warn("Hibachi rejection frame with no orderId: {}", frame);
                return;
            }
            RejectionInfo info = new RejectionInfo(reason, frame);
            // Atomic: either complete an existing waiter, or stash the rejection for a
            // waiter that hasn't registered yet (race when the rejection arrives on the
            // account WS before placeOrder has installed its PendingPlace).
            PendingPlace pending = pendingPlaces.compute(orderId, (k, existing) -> {
                if (existing == null) {
                    PendingPlace pp = new PendingPlace();
                    pp.earlyRejection = info;
                    return pp;
                }
                existing.future.complete(info);
                return existing;
            });
            OrderTicket order = pending.order != null ? pending.order : orderRegistry.getOrderById(orderId);
            if (order == null) {
                // Place hasn't registered yet — the OrderEvent will be fired when placeOrder
                // picks up the early rejection and we have the OrderTicket reference.
                return;
            }
            fireRejectedEvent(order, orderId, reason);
        }
    }

    private void fireRejectedEvent(OrderTicket order, String orderId, String reason) {
        order.setCurrentStatus(OrderStatus.Status.REJECTED);
        OrderStatus status = new OrderStatus(OrderStatus.Status.REJECTED, orderId,
                BigDecimal.ZERO, order.getSize(), null, order.getTicker(),
                ZonedDateTime.now(ZoneOffset.UTC));
        status.setClientOrderId(order.getClientOrderId());
        status.setCancelReason(classifyCancelReason(reason));
        fireOrderEvent(new OrderEvent(order, status));
        orderRegistry.addCompletedOrder(order);
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
