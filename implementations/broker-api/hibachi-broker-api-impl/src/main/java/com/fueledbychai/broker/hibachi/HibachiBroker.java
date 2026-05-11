package com.fueledbychai.broker.hibachi;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.fueledbychai.hibachi.common.api.HibachiConfiguration;
import com.fueledbychai.hibachi.common.api.HibachiContract;
import com.fueledbychai.hibachi.common.api.IHibachiRestApi;
import com.fueledbychai.hibachi.common.api.signer.HibachiSignerFactory;
import com.fueledbychai.hibachi.common.api.signer.IHibachiSigner;
import com.fueledbychai.time.Span;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

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
    /**
     * Strictly-monotonic nonce source. Hibachi rejects any nonce it has
     * already seen; the previous implementation returned
     * {@code System.currentTimeMillis() * 1000} which collided whenever
     * two place/modify/cancel calls fired in the same millisecond — a
     * common case when chaiwala emits BUY+SELL L0 in the same quote
     * cycle. We seed with the same epoch-microseconds form for wire-level
     * compatibility with the Python SDK, then incrementAndGet so every
     * caller gets a unique value regardless of clock granularity. If
     * wall-clock ever advances past our counter, we jump forward to it
     * (preserves "nonces look like timestamps" — useful for debugging).
     */
    protected final AtomicLong nonceCounter =
            new AtomicLong(System.currentTimeMillis() * 1_000L);
    protected final Map<String, Position> positionsCache = new ConcurrentHashMap<>();
    protected final Map<String, PendingPlace> pendingPlaces = new ConcurrentHashMap<>();
    /**
     * How long {@link #placeOrder} waits after the WS gateway ack for a risk-engine rejection
     * to arrive on the account WS. Rejections in practice land within ~10ms; we give a
     * generous margin without adding noticeable latency to successful places.
     */
    protected static final long PLACE_REJECTION_WAIT_MILLIS = 500L;
    /**
     * TTL for the REST open-orders snapshot. {@link #getOpenOrders()} pulls from the
     * venue (not the local registry) so the reconcile loop sees orders that the
     * account WS may have missed (modify async-rejected, dropped frames, etc.). A
     * short cache keeps multiple callers in the same reconcile cycle from each
     * firing their own REST round-trip.
     */
    protected static final long OPEN_ORDERS_CACHE_TTL_MILLIS = 1500L;
    protected volatile boolean connected;
    protected final Object openOrdersCacheLock = new Object();
    protected volatile List<OrderTicket> openOrdersCache;
    protected volatile long openOrdersCacheStamp;

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
        boolean viaWs = config.isPlaceViaWebSocket();
        try (var s = Span.start("HB_PLACE_ORDER", order.getClientOrderId(), LATENCY_LOGGER)) {
            String symbol = order.getTicker().getSymbol();
            HibachiContract contract = restApi.getContract(symbol);
            if (contract == null) {
                return new BrokerRequestResult(false, true, "Unknown contract: " + symbol,
                        BrokerRequestResult.FailureType.VALIDATION_FAILED);
            }
            long nonce = nextNonce();
            // creationDeadline is microseconds since epoch (same unit as nonce);
            // the venue compares it to its process_time in micros and rejects with
            // "Creation deadline exceeded" if it's smaller. We previously sent
            // seconds, which the venue treats as a tiny number → instant rejection.
            long deadline = nonce + config.getOrderDeadlineSeconds() * 1_000_000L;
            HibachiTranslator.SignedRequest request = translator.translatePlace(
                    order, contract, accountId, nonce, deadline, config.getOrderMaxFeesPercent(), signer);
            orderRegistry.addOpenOrder(order);
            String transport = viaWs ? "WS" : "REST";
            logger.info("HB_LIFECYCLE place SEND ({}) clientId={} symbol={} side={} price={} size={} nonce={}",
                    transport, order.getClientOrderId(), symbol, order.getTradeDirection(),
                    order.getLimitPrice(), order.getSize(), nonce);
            JsonNode response;
            if (viaWs) {
                response = tradeWs.placeOrder(request.params, request.signature, order.getClientOrderId());
            } else {
                // translatePlace already embeds `signature` in params, so the WS map
                // doubles as the REST body without further transformation.
                response = restApi.placeOrder(request.params);
            }
            logger.info("HB_LIFECYCLE place RECV ({}) clientId={} response={}",
                    transport, order.getClientOrderId(), response);
            BrokerRequestResult result = interpretResponse(response, "placeOrder");
            if (!result.isSuccess()) {
                return result;
            }
            // WS shape: {"result":{"orderId":"…"}}; REST shape: {"orderId":"…"}.
            String exchangeOrderId = response.path("result").path("orderId").asText(null);
            if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
                exchangeOrderId = response.path("orderId").asText(null);
            }
            if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
                return new BrokerRequestResult(false, true, response.toString(),
                        BrokerRequestResult.FailureType.UNKNOWN);
            }
            order.setOrderId(exchangeOrderId);
            // Re-register so the registry's orderId-keyed map is populated.
            // The earlier addOpenOrder() before the WS round-trip skipped
            // orderId indexing because exchangeOrderId was still null at
            // that point — and getOpenOrders() reads only the orderId-keyed
            // map. Without this re-register, the registry knows the order
            // exists by clientOrderId but getOpenOrders() returns empty,
            // which is what made chaiwala's reconcile loop drop active
            // orders that were still alive at the exchange.
            orderRegistry.addOpenOrder(order);
            invalidateOpenOrdersCache();
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
        // Modify path is config-gated. Default is REST (PUT /trade/order)
        // because the venue's WS `order.modify` historically did not respond.
        // Set hibachi.modify.via.ws=true to route through the trade WS for
        // testing — useful when REST is the rate-limit bottleneck (the REST
        // global aperture is 350 req / 10s; WS modify is not subject to it).
        boolean viaWs = config.isModifyViaWebSocket();
        try (var s = Span.start("HB_MODIFY_ORDER", order.getClientOrderId(), LATENCY_LOGGER)) {
            String symbol = order.getTicker().getSymbol();
            HibachiContract contract = restApi.getContract(symbol);
            if (contract == null) {
                return new BrokerRequestResult(false, true, "Unknown contract: " + symbol,
                        BrokerRequestResult.FailureType.VALIDATION_FAILED);
            }
            long nonce = nextNonce();
            // creationDeadline is microseconds since epoch (same unit as nonce);
            // the venue compares it to its process_time in micros and rejects with
            // "Creation deadline exceeded" if it's smaller. We previously sent
            // seconds, which the venue treats as a tiny number → instant rejection.
            long deadline = nonce + config.getOrderDeadlineSeconds() * 1_000_000L;
            HibachiTranslator.SignedRequest request = translator.translateModify(
                    order, contract, accountId, nonce, deadline, config.getOrderMaxFeesPercent(), signer);
            String transport = viaWs ? "WS" : "REST";
            logger.info("HB_LIFECYCLE modify SEND ({}) clientId={} orderId={} symbol={} side={} newPrice={} newSize={} nonce={}",
                    transport, order.getClientOrderId(), order.getOrderId(), symbol, order.getTradeDirection(),
                    order.getLimitPrice(), order.getSize(), nonce);
            // Install a waiter BEFORE the call so an order_request_rejected
            // frame that races the success ack doesn't get stashed as an
            // "early" rejection that the next caller picks up by mistake.
            String exchangeOrderId = order.getOrderId();
            if (exchangeOrderId != null && !exchangeOrderId.isBlank()) {
                pendingPlaces.compute(exchangeOrderId, (k, existing) -> {
                    if (existing != null) {
                        existing.order = order;
                        return existing;
                    }
                    return new PendingPlace(order);
                });
            }
            JsonNode response;
            if (viaWs) {
                // REST and WS modify endpoints disagree on field names:
                //   REST `PUT /trade/order` → updatedQuantity / updatedPrice
                //   WS `order.modify`       → quantity / price (matches order.place)
                // Sending updatedQuantity over WS gets back
                //   {"error":"Invalid params: missing field `quantity`"}
                // with no id, which the await() can't correlate → 10s timeout.
                // Verified empirically 2026-05-08 22:51 in chaiwala prod.
                // The translator emits REST-style names (what the documented
                // REST body needs); we transform here for the WS branch only
                // so the working REST path is untouched. Signature is computed
                // over a binary pack of values, not field names, so renaming
                // the JSON keys doesn't invalidate it.
                Map<String, Object> wsParams = new LinkedHashMap<>(request.params);
                Object updatedQty = wsParams.remove("updatedQuantity");
                if (updatedQty != null) {
                    wsParams.put("quantity", updatedQty);
                }
                Object updatedPx = wsParams.remove("updatedPrice");
                if (updatedPx != null) {
                    wsParams.put("price", updatedPx);
                }
                response = tradeWs.modifyOrder(wsParams, request.signature, order.getClientOrderId());
            } else {
                // REST requires `signature` to ride inside the JSON body.
                Map<String, Object> body = new LinkedHashMap<>(request.params);
                body.putIfAbsent("signature", request.signature);
                response = restApi.modifyOrder(body);
            }
            logger.info("HB_LIFECYCLE modify RECV ({}) clientId={} orderId={} response={}",
                    transport, order.getClientOrderId(), order.getOrderId(), response);
            BrokerRequestResult result = interpretResponse(response, "modifyOrder");
            // Invalidate regardless of outcome: the venue may have moved
            // even on a failure (e.g. risk-engine partial-match path).
            invalidateOpenOrdersCache();
            if (!result.isSuccess()) {
                if (exchangeOrderId != null && !exchangeOrderId.isBlank()) {
                    pendingPlaces.remove(exchangeOrderId);
                }
                return result;
            }
            // Success ack returned — but Hibachi still validates the modify
            // on its risk engine and may emit {"event":"order_request_rejected",
            // "data":{"requestType":"Update",...}} a few ms later. If we
            // return success without waiting, chaiwala runs
            // applyDesiredStateToTrackedOrder and its local limitPrice
            // diverges from the venue's actual price (UI looks like the
            // order is updating; exchange shows it stuck at the prior level).
            if (exchangeOrderId != null && !exchangeOrderId.isBlank()) {
                BrokerRequestResult asyncRejection = awaitAccountRejection(exchangeOrderId, order);
                if (asyncRejection != null) {
                    logger.info("HB_LIFECYCLE modify async-rejected clientId={} orderId={} reason={}",
                            order.getClientOrderId(), exchangeOrderId, asyncRejection.getMessage());
                    return asyncRejection;
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Hibachi modifyOrder failed", e);
            // Run the error message through classifyRejection so callers
            // can see ORDER_NOT_FOUND / ORDER_ALREADY_COMPLETE / etc.
            // (e.g. {"errorCode":3,"message":"Not found: Order ID …"}) and
            // route to placeFresh / no-op instead of a blind cancel+replace.
            BrokerRequestResult.FailureType ft = classifyRejection(e.getMessage());
            return new BrokerRequestResult(false, true, e.getMessage(), ft);
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
            logger.info("HB_LIFECYCLE cancel SEND clientId={} orderId={} symbol={}",
                    order.getClientOrderId(), order.getOrderId(),
                    order.getTicker() != null ? order.getTicker().getSymbol() : null);
            JsonNode response = tradeWs.cancelOrder(request.params, request.signature, traceId);
            logger.info("HB_LIFECYCLE cancel RECV clientId={} orderId={} response={}",
                    order.getClientOrderId(), order.getOrderId(), response);
            invalidateOpenOrdersCache();
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
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return new BrokerRequestResult(false, true, "clientOrderId is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        OrderTicket existing = orderRegistry.getOrderByClientId(clientOrderId);
        if (existing == null) {
            return new BrokerRequestResult(false, true, "Order not found: " + clientOrderId,
                    BrokerRequestResult.FailureType.ORDER_NOT_FOUND);
        }
        return cancelOrder(existing);
    }

    @Override
    public String getNextOrderId() {
        return String.valueOf(nextClientOrderId.incrementAndGet());
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return null;
        }
        OrderTicket cached = orderRegistry.getOrderByClientId(clientOrderId);
        if (!isConnected()) {
            return cached;
        }
        try (var s = Span.start("HB_REQUEST_ORDER_STATUS_BY_CLOID", clientOrderId, LATENCY_LOGGER)) {
            JsonNode response = restApi.getOrderByClientId(clientOrderId);
            if (response == null || response.isNull() || response.isMissingNode()) {
                return cached;
            }
            applyOrderUpdateToRegistry(response);
            // Prefer the now-current registry entry (resolved by either id),
            // falling back to the cached one if the apply path didn't match.
            OrderTicket refreshed = orderRegistry.getOrderByClientId(clientOrderId);
            return refreshed != null ? refreshed : cached;
        } catch (Exception e) {
            logger.warn("Hibachi requestOrderStatusByClientOrderId({}) failed; returning cached",
                    clientOrderId, e);
            return cached;
        }
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
        // Pull from the venue, not the local registry. The registry is fed by
        // place/modify acks + account WS events, both of which can drop orders:
        // a modify can be REST-200'd then async-rejected (order stays alive at
        // the prior price), and WS frames occasionally arrive with a key the
        // listener can't match (logged as "event_orphan"). When that happens
        // the local registry shows N open orders while the venue holds N+1 —
        // chaiwala's reconcile loop never sees the leak because it's comparing
        // two views with the same blind spot. Reading directly from
        // /trade/orders makes the venue the source of truth.
        long now = System.currentTimeMillis();
        List<OrderTicket> cached = openOrdersCache;
        if (cached != null && (now - openOrdersCacheStamp) < OPEN_ORDERS_CACHE_TTL_MILLIS) {
            return new ArrayList<>(cached);
        }
        synchronized (openOrdersCacheLock) {
            cached = openOrdersCache;
            now = System.currentTimeMillis();
            if (cached != null && (now - openOrdersCacheStamp) < OPEN_ORDERS_CACHE_TTL_MILLIS) {
                return new ArrayList<>(cached);
            }
            List<OrderTicket> fresh = fetchOpenOrdersFromRest();
            if (fresh != null) {
                openOrdersCache = fresh;
                openOrdersCacheStamp = System.currentTimeMillis();
                return new ArrayList<>(fresh);
            }
            // REST failed — fall back to registry so the reconcile loop has
            // something to work with. Don't cache the fallback; we want the
            // next call to retry REST.
            List<OrderTicket> fallback = new ArrayList<>();
            if (orderRegistry != null) {
                orderRegistry.getOpenOrders().forEach(fallback::add);
            }
            return fallback;
        }
    }

    /**
     * Fetches /trade/orders, parses each entry into an {@link OrderTicket}, and
     * returns the list. Returns {@code null} on any error so callers can fall
     * back to the registry.
     */
    protected List<OrderTicket> fetchOpenOrdersFromRest() {
        try {
            JsonNode response = restApi.getOpenOrders();
            if (response == null || response.isMissingNode() || response.isNull()) {
                logger.warn("HB_LIFECYCLE getOpenOrders REST returned null/missing");
                return null;
            }
            JsonNode arr = response;
            if (response.isObject()) {
                // Hibachi has shipped both a bare array and {"orders":[...]} /
                // {"data":[...]} shapes — accept either.
                if (response.has("orders") && response.path("orders").isArray()) {
                    arr = response.path("orders");
                } else if (response.has("data") && response.path("data").isArray()) {
                    arr = response.path("data");
                } else if (response.has("result") && response.path("result").isArray()) {
                    arr = response.path("result");
                }
            }
            if (!arr.isArray()) {
                logger.warn("HB_LIFECYCLE getOpenOrders REST unexpected shape: {}", response);
                return null;
            }
            List<OrderTicket> out = new ArrayList<>(arr.size());
            for (JsonNode node : arr) {
                OrderTicket parsed = parseRestOrder(node);
                if (parsed != null) {
                    out.add(parsed);
                }
            }
            return out;
        } catch (Exception e) {
            logger.warn("HB_LIFECYCLE getOpenOrders REST failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Builds an {@link OrderTicket} from a single Hibachi REST open-order entry.
     * Prefers the registry's existing OrderTicket instance when the orderId or
     * clientOrderId matches so caller-side state (modifiers, originalEntryTime,
     * etc.) carries through; falls back to a freshly-built ticket for orders
     * the registry has never seen (the orphan-leak case this fix targets).
     */
    protected OrderTicket parseRestOrder(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String orderId = textOrNull(node, "orderId", "id");
        String clientOrderId = textOrNull(node, "clientOrderId", "clientId");
        BigDecimal price = readDecimal(node, "price", "limitPrice");
        // Hibachi REST /trade/orders shape: totalQuantity = original size,
        // availableQuantity = unfilled remaining. There is no explicit
        // "filledQuantity" field — derive it.
        BigDecimal totalQty = readDecimal(node, "totalQuantity", "quantity", "size", "originalQuantity");
        BigDecimal availableQty = readDecimal(node, "availableQuantity", "remainingQuantity", "remaining");
        BigDecimal filled = readDecimal(node, "filledQuantity", "executedQuantity",
                "executed_quantity", "filled", "matchedQuantity");
        if (filled == null && totalQty != null && availableQty != null) {
            filled = totalQty.subtract(availableQty);
            if (filled.signum() < 0) {
                filled = BigDecimal.ZERO;
            }
        }
        String symbol = textOrNull(node, "symbol", "contract");
        String sideRaw = textOrNull(node, "side", "direction");
        Ticker ticker = lookupTicker(symbol);

        OrderTicket existing = null;
        if (orderRegistry != null) {
            if (orderId != null) {
                existing = orderRegistry.getOrderById(orderId);
            }
            if (existing == null && clientOrderId != null) {
                existing = orderRegistry.getOrderByClientId(clientOrderId);
            }
        }

        OrderTicket out = existing != null ? existing : new OrderTicket();
        if (orderId != null) {
            out.setOrderId(orderId);
        }
        if (clientOrderId != null) {
            out.setClientOrderId(clientOrderId);
        }
        if (ticker != null) {
            out.setTicker(ticker);
        }
        if (price != null) {
            // Refresh from venue truth — fixes the "UI shows the new price but
            // exchange has the old one" symptom from a modify that REST-200'd
            // and was then async-rejected.
            out.setLimitPrice(price);
        }
        if (totalQty != null) {
            out.setSize(totalQty);
        }
        if (filled != null) {
            out.setFilledSize(filled);
        }
        if (sideRaw != null && out.getTradeDirection() == null) {
            String s = sideRaw.toUpperCase();
            if (s.equals("BID") || s.equals("BUY") || s.equals("LONG")) {
                out.setTradeDirection(TradeDirection.BUY);
            } else if (s.equals("ASK") || s.equals("SELL") || s.equals("SHORT")) {
                out.setTradeDirection(TradeDirection.SELL);
            }
        }
        return out;
    }

    /**
     * Drops the cached open-orders snapshot. Call after place/modify/cancel
     * acks so the next reconcile sees the venue-truth view including any side
     * effects (especially modify rejections that leave the prior order alive).
     */
    protected void invalidateOpenOrdersCache() {
        openOrdersCache = null;
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
            invalidateOpenOrdersCache();
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
        // "UpdatingPartiallyMatchedOrder": Hibachi forbids modify on a
        // partially-filled order. The order remains alive at the original
        // price with reduced qty — chaiwala's correct response is cancel
        // the remainder and place fresh, which is what the default
        // (UNKNOWN → cancel+replace) branch does. We map it explicitly
        // anyway so audit logs / dashboards can surface the reason.
        if (r.contains("partiallymatched") || r.contains("partially_matched")
                || r.contains("partiallyfilled") || r.contains("partially_filled")) {
            return BrokerRequestResult.FailureType.ORDER_ALREADY_COMPLETE;
        }
        // Hibachi REST returns errorCode:3 with message "Not found: Order ID …"
        // when the order has been fully filled / canceled / never existed.
        if (r.contains("not found") || r.contains("notfound") || r.contains("errorcode\":3")) {
            return BrokerRequestResult.FailureType.ORDER_NOT_FOUND;
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
        // Strictly monotonic: if wall-clock advanced past our counter, jump
        // to wall-clock; otherwise advance the counter by 1us. Either way
        // every call returns a unique, strictly-increasing value — Hibachi
        // rejects duplicates with "Invalid input: Nonce already used".
        long wallNow = System.currentTimeMillis() * 1_000L;
        return nonceCounter.updateAndGet(prev -> prev < wallNow ? wallNow : prev + 1L);
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
            // First log the raw snapshot — this is the one-shot frame
            // Hibachi sends right after the listenKey is established, and
            // it's where the initial balance / equity / positions / open
            // orders should arrive. If this never fires, dashboard balance
            // will stay at $0 until a subsequent balance event lands.
            logger.info("HB_LIFECYCLE ws_event onAccountSnapshot frame={}", snapshot);
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
            logger.info("HB_LIFECYCLE ws_event onPositionUpdate frame={}", frame);
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
            logger.info("HB_LIFECYCLE ws_event onBalanceUpdate frame={}", frame);
            applyBalance(bodyOf(frame));
        }

        @Override
        public void onOrderUpdate(JsonNode frame) {
            logger.info("HB_LIFECYCLE ws_event onOrderUpdate frame={}", frame);
            // Pass the full frame so applyOrderUpdateToRegistry can read the
            // top-level "event" field (order_creation / order_matched /
            // order_cancellation). Hibachi puts the lifecycle status in the
            // event name, not as a "status" field on the body — without
            // this the body has no status and the update is silently
            // dropped, leaving chaiwala unaware its orders even reached
            // the exchange.
            applyOrderUpdateToRegistry(frame);
        }

        @Override
        public void onFill(JsonNode frame) {
            logger.info("HB_LIFECYCLE ws_event onFill frame={}", frame);
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
            // Hibachi distinguishes between a rejected New (place) and a rejected
            // Update (modify) via data.requestType. Both flow through the same
            // pendingPlaces waiter map so place/modify callers can react —
            // but the post-await behaviour differs:
            //   - "New" rejected: order never opened. Fire REJECTED event so
            //     chaiwala drops the level and the place caller returns failure.
            //   - "Update" rejected: original order is still alive on the book.
            //     We MUST NOT fire REJECTED (would move the order to the
            //     completed map and have chaiwala stack a duplicate order at
            //     the next cycle's price). Just unblock the modify waiter so
            //     it can return failure, and chaiwala's handleSpecModifyFailure
            //     will avoid running applyDesiredStateToTrackedOrder — keeping
            //     the locally-tracked limitPrice consistent with what Hibachi
            //     actually has on the book.
            String requestType = frame.path("data").path("requestType").asText("");
            boolean isUpdate = "Update".equalsIgnoreCase(requestType);
            RejectionInfo info = new RejectionInfo(reason, frame);
            // Atomic: either complete an existing waiter, or stash the rejection for a
            // waiter that hasn't registered yet (race when the rejection arrives on the
            // account WS before placeOrder/modifyOrder has installed its PendingPlace).
            PendingPlace pending = pendingPlaces.compute(orderId, (k, existing) -> {
                if (existing == null) {
                    PendingPlace pp = new PendingPlace();
                    pp.earlyRejection = info;
                    return pp;
                }
                existing.future.complete(info);
                return existing;
            });
            if (isUpdate) {
                OrderTicket order = pending.order != null ? pending.order : orderRegistry.getOrderById(orderId);
                logger.warn("Hibachi modify request rejected (order remains alive) "
                                + "orderId={} clientId={} reason={}",
                        orderId,
                        order != null ? order.getClientOrderId() : null,
                        reason);
                return;
            }
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
            logger.info("HB_LIFECYCLE balance applyBalance no-op (null body)");
            return;
        }
        // The initial onAccountSnapshot frame puts collateral under "balance";
        // subsequent balance_update events use "updatedCollateralBalance".
        // Read both so the dashboard balance keeps tracking after fills.
        BigDecimal available = readDecimal(body, "balance", "updatedCollateralBalance");
        // INFO so we see EXACTLY which keys hit and which didn't. If both
        // are null, dump the body so we can spot the actual field names
        // Hibachi is sending — that's the diagnostic for "$0 in UI even
        // though the account has money".


        //For Hibachi, available and equity are the same thing, they just use "balance" as the key. So we read "balance" into both variables to keep the logging and event-firing logic consistent with other brokers that have separate equity vs. available keys.
        BigDecimal equity = available;
        if (equity == null && available == null) {
            logger.info("HB_LIFECYCLE balance applyBalance found neither equity-style nor available-style key. body={}", body);
        } else {
            logger.info("HB_LIFECYCLE balance applyBalance equity={} available={}", equity, available);
        }

        //For Hibachi, they actually only return 

        if (equity != null) {
            fireAccountEquityUpdated(equity.doubleValue());
        }
        if (available != null) {
            fireAvailableFundsUpdated(available.doubleValue());
        }
    }

    protected void applyOrderUpdateToRegistry(JsonNode frame) {
        if (frame == null || frame.isMissingNode() || frame.isNull()) {
            return;
        }
        // Any order WS event means the venue's open-orders set may have moved.
        // Drop the cache so the next reconcile sees the post-event view.
        invalidateOpenOrdersCache();
        // Frame may be either the wrapped form ({"event":"...","data":{...}})
        // or a raw body. Tolerate both.
        String eventName = textOrNull(frame, "event");
        JsonNode body = bodyOf(frame);
        if (body == null || body.isMissingNode() || body.isNull()) {
            body = frame;
        }
        String exchangeOrderId = textOrNull(body, "orderId", "id");
        String clientOrderId = textOrNull(body, "clientOrderId", "clientId");
        OrderTicket order = null;
        if (exchangeOrderId != null) {
            order = orderRegistry.getOrderById(exchangeOrderId);
        }
        if (order == null && clientOrderId != null) {
            order = orderRegistry.getOrderByClientId(clientOrderId);
        }
        if (order == null) {
            // INFO not DEBUG: silently dropping order updates is the failure
            // mode behind "10 orders piled up at exchange but reconcile sees
            // 0". When this fires, either the exchange-orderId from this
            // event doesn't match what placeOrder() stored, or the WS frame
            // arrived before the registry write. Either way we want to see
            // it without enabling broad-package DEBUG.
            logger.info("HB_LIFECYCLE event_orphan event={} exchangeOrderId={} clientId={} body={}",
                    eventName, exchangeOrderId, clientOrderId, body);
            return;
        }
        logger.info("HB_LIFECYCLE event_match event={} exchangeOrderId={} clientId={} filled={} remaining={}",
                eventName, exchangeOrderId, clientOrderId,
                readDecimal(body, "filledQuantity", "executedQuantity", "executed_quantity", "filled", "matchedQuantity"),
                readDecimal(body, "remainingQuantity", "remaining"));
        if (exchangeOrderId != null && order.getOrderId() == null) {
            order.setOrderId(exchangeOrderId);
            // Same reason as in placeOrder() — re-register so the registry's
            // orderId-keyed map is populated. The WS event can race ahead of
            // the place-RECV path on a fast venue.
            orderRegistry.addOpenOrder(order);
        }

        BigDecimal filled = readDecimal(body, "filledQuantity", "executedQuantity", "executed_quantity", "filled", "matchedQuantity");
        BigDecimal remaining = readDecimal(body, "remainingQuantity", "remaining");
        if (filled == null) {
            filled = order.getFilledSize() != null ? order.getFilledSize() : BigDecimal.ZERO;
        }
        if (remaining == null && order.getSize() != null) {
            remaining = order.getSize().subtract(filled);
        }
        BigDecimal fillPrice = readDecimal(body, "averagePrice", "avgFillPrice", "price");

        // Hibachi reports lifecycle via top-level event name, not a status
        // field on body. Fall back to body.status for forward compatibility
        // with any future shape change.
        OrderStatus.Status status = parseHibachiEventStatus(eventName, filled, remaining);
        if (status == null) {
            status = parseOrderStatus(textOrNull(body, "status", "orderStatus"));
        }
        if (status == null) {
            logger.info("HB_LIFECYCLE event_unknown_status event={} clientId={} skipping (registry retains entry)",
                    eventName, clientOrderId);
            return;
        }
        order.setCurrentStatus(status);
        if (filled != null) {
            order.setFilledSize(filled);
        }
        if (fillPrice != null) {
            order.setFilledPrice(fillPrice);
        }
        // Move terminal-status orders out of the open-orders map so
        // getOpenOrders() doesn't return them. Without this, a FILLED or
        // CANCELED order sticks in the registry forever — chaiwala's
        // reconcile loop then sees it as a "live" orphan, fires modifies
        // against it, and Hibachi answers "Not found: Order ID …".
        if (status == OrderStatus.Status.FILLED
                || status == OrderStatus.Status.CANCELED
                || status == OrderStatus.Status.REJECTED) {
            orderRegistry.addCompletedOrder(order);
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
            // Hibachi's trade_update frames carry "timestamp" as 10-digit
            // seconds-since-epoch with the sub-second part separately in
            // "timestampNsPartial" (nanoseconds-within-the-second). Other
            // Hibachi endpoints have surfaced millis and microseconds, so
            // cover all three magnitudes.
            // The previous heuristic only handled micros (>1e15) and treated
            // seconds as if they were millis — fills landed at 1970-01-21,
            // making the inventory-holding-time UI show ~493,000h ages.
            long millis;
            if (ts < 1_000_000_000_000L) {           // ≤ 12 digits → seconds
                millis = ts * 1_000L;
                // Recover sub-second precision from the partial field. Without
                // this, every Hibachi fill lands at HH:mm:ss.000 — the UI's
                // "no milliseconds on Hibachi fills" symptom.
                Long nsPartial = readLong(body, "timestampNsPartial", "nsPartial", "nanoseconds");
                if (nsPartial != null && nsPartial >= 0 && nsPartial < 1_000_000_000L) {
                    millis += nsPartial / 1_000_000L;
                }
            } else if (ts < 1_000_000_000_000_000L) { // ≤ 15 digits → millis
                millis = ts;
            } else {                                  // ≥ 16 digits → micros
                millis = ts / 1_000L;
            }
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

    /**
     * Maps Hibachi's account-WS event names to the broker-API status enum.
     * Hibachi puts lifecycle in the top-level {@code event} field rather
     * than a body-level status, so direct {@link #parseOrderStatus} doesn't
     * apply. {@code order_matched} resolves to FILLED when remaining quantity
     * is zero, otherwise PARTIAL_FILL — the same semantics other brokers
     * deliver via FULLY_FILLED / PARTIAL_FILL.
     */
    protected static OrderStatus.Status parseHibachiEventStatus(String eventName,
            BigDecimal filled, BigDecimal remaining) {
        if (eventName == null) {
            return null;
        }
        switch (eventName.toLowerCase()) {
            case "order_creation":
            case "order_placed":
                return OrderStatus.Status.NEW;
            case "order_update":
            case "order_modify":
            case "order_modified":
            case "order_replaced":
                // Hibachi sends "order_update" as the modify-ack event. Body
                // contains the new price/quantity; we already extract those
                // upstream and update the OrderTicket, so REPLACED is the
                // right status to forward to chaiwala.
                return OrderStatus.Status.REPLACED;
            case "order_cancellation":
            case "order_cancel":
            case "order_cancelled":
            case "order_canceled":
                return OrderStatus.Status.CANCELED;
            case "order_rejected":
                return OrderStatus.Status.REJECTED;
            case "order_matched":
            case "order_filled":
            case "trade_update":
                if (remaining != null && remaining.signum() == 0) {
                    return OrderStatus.Status.FILLED;
                }
                if (filled != null && filled.signum() > 0) {
                    return OrderStatus.Status.PARTIAL_FILL;
                }
                return OrderStatus.Status.PARTIAL_FILL;
            default:
                return null;
        }
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
