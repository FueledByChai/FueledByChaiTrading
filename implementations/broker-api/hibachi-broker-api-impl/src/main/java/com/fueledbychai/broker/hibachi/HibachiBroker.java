package com.fueledbychai.broker.hibachi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.hibachi.common.api.HibachiConfiguration;
import com.fueledbychai.hibachi.common.api.HibachiContract;
import com.fueledbychai.hibachi.common.api.IHibachiRestApi;
import com.fueledbychai.hibachi.common.api.signer.HibachiSignerFactory;
import com.fueledbychai.hibachi.common.api.signer.IHibachiSigner;
import com.fueledbychai.util.ExchangeRestApiFactory;

public class HibachiBroker extends AbstractBasicBroker {

    private static final Logger logger = LoggerFactory.getLogger(HibachiBroker.class);

    protected final IHibachiRestApi restApi;
    protected final HibachiConfiguration config;
    protected final HibachiTranslator translator;
    protected final IHibachiSigner signer;
    protected final long accountId;

    protected final HibachiTradeWebSocketClient tradeWs;
    protected final HibachiAccountStreamClient accountWs;
    protected final AtomicLong nextClientOrderId = new AtomicLong(System.currentTimeMillis());
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
        try {
            String symbol = order.getTicker().getSymbol();
            HibachiContract contract = restApi.getContract(symbol);
            if (contract == null) {
                return new BrokerRequestResult(false, true, "Unknown contract: " + symbol,
                        BrokerRequestResult.FailureType.VALIDATION_FAILED);
            }
            long nonce = nextNonce();
            HibachiTranslator.SignedRequest request = translator.translatePlace(
                    order, contract, accountId, nonce, config.getOrderMaxFeesPercent(), signer);
            JsonNode response = tradeWs.placeOrder(request.params, request.signature);
            return interpretResponse(response, "placeOrder");
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
        try {
            String symbol = order.getTicker().getSymbol();
            HibachiContract contract = restApi.getContract(symbol);
            if (contract == null) {
                return new BrokerRequestResult(false, true, "Unknown contract: " + symbol,
                        BrokerRequestResult.FailureType.VALIDATION_FAILED);
            }
            long nonce = nextNonce();
            HibachiTranslator.SignedRequest request = translator.translateModify(
                    order, contract, accountId, nonce, config.getOrderMaxFeesPercent(), signer);
            JsonNode response = tradeWs.modifyOrder(request.params, request.signature);
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
        try {
            HibachiTranslator.SignedRequest request = translator.translateCancel(
                    order, accountId, nextNonce(), signer);
            JsonNode response = tradeWs.cancelOrder(request.params, request.signature);
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
        return orderRegistry.getOrderById(orderId);
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
        return new ArrayList<>();
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        cancelOrder(originalOrderId);
        placeOrder(newOrder);
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        checkConnected();
        try {
            long nonce = nextNonce();
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
        // Hibachi does not expose a per-symbol cancel-all on the trade WS — fall back to the
        // global cancel; callers needing per-symbol must iterate open orders themselves.
        return cancelAllOrders();
    }

    @Override
    public BrokerRequestResult cancelOrders(java.util.List<OrderTicket> orders) {
        throw new UnsupportedOperationException("Batch cancel not supported by HibachiBroker");
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
}
