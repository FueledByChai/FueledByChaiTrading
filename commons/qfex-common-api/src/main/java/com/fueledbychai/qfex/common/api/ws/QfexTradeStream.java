package com.fueledbychai.qfex.common.api.ws;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueledbychai.qfex.common.api.QfexHmacSigner;

/**
 * The authenticated order-entry socket ({@code wss://trade.qfex.com?api_key=PUB}).
 *
 * <p>Lifecycle per connection: open, send {@code auth} (HMAC; the venue drops
 * unauthenticated sockets after one minute), then subscribe to
 * order_responses, fills, positions and balances, and optionally re-arm
 * cancel-on-disconnect (it is per connection).
 *
 * <p>QFEX responses carry no request id. {@link #request} registers a
 * matcher before sending; each reply goes to the first pending request of the
 * expected kind whose matcher accepts it. Errors are matched through the
 * {@code incoming_message} the venue echoes back, falling back to the oldest
 * pending request. The auth reply is accepted in both documented shapes:
 * {@code {"authenticated":true}} and {@code {"type":"auth","result":"success"}}.
 */
public class QfexTradeStream extends QfexStream {

    public static final String ORDER_RESPONSE = "order_response";
    public static final String ALL_ORDERS_RESPONSE = "all_orders_response";
    public static final String ACK = "ack";

    private final String baseUrl;
    private final QfexHmacSigner signer;
    private final String accountId;
    private final boolean cancelOnDisconnect;
    private final long timeoutMillis;
    private final List<QfexTradeListener> listeners = new CopyOnWriteArrayList<>();
    private final List<Pending> pending = new CopyOnWriteArrayList<>();
    private volatile boolean authenticated;

    public QfexTradeStream(String baseUrl, QfexHmacSigner signer, String accountId, boolean cancelOnDisconnect,
            long timeoutMillis) {
        super("trade", Duration.ofSeconds(120));
        this.baseUrl = baseUrl;
        this.signer = signer;
        this.accountId = accountId;
        this.cancelOnDisconnect = cancelOnDisconnect;
        this.timeoutMillis = timeoutMillis;
    }

    public void addListener(QfexTradeListener listener) {
        listeners.add(listener);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Sends {@code message} and completes with the first reply under
     * {@code responseKey} that {@code matcher} accepts, or with an
     * {@code {"err":{...}}} node if the venue rejected the request.
     * Completes exceptionally on timeout or disconnect.
     */
    public CompletableFuture<JsonNode> request(ObjectNode message, String responseKey, Predicate<JsonNode> matcher) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (!authenticated) {
            future.completeExceptionally(new IllegalStateException("QFEX trade socket not authenticated"));
            return future;
        }
        Pending p = new Pending(responseKey, matcher, message, future);
        pending.add(p);
        scheduler.schedule(() -> {
            if (pending.remove(p)) {
                future.completeExceptionally(new IllegalStateException(
                        "QFEX: no " + responseKey + " within " + timeoutMillis + "ms for " + message.path("type").asText()));
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        if (!send(message)) {
            pending.remove(p);
            future.completeExceptionally(new IllegalStateException("QFEX trade socket not open"));
        }
        return future;
    }

    /** Sends without waiting for a reply. */
    public boolean fire(ObjectNode message) {
        return authenticated && send(message);
    }

    @Override
    protected String connectUrl() {
        String pub = signer == null ? "" : signer.sign().publicKey();
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "api_key="
                + URLEncoder.encode(pub, StandardCharsets.UTF_8);
    }

    @Override
    protected void onOpened() {
        send(authMessage());
    }

    @Override
    protected void onClosed() {
        authenticated = false;
        for (Pending p : pending) {
            p.future.completeExceptionally(new IllegalStateException("QFEX trade socket closed"));
        }
        pending.clear();
        listeners.forEach(QfexTradeListener::onDisconnected);
    }

    ObjectNode authMessage() {
        QfexHmacSigner.Credentials c = signer.sign();
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "auth");
        ObjectNode params = msg.putObject("params");
        ObjectNode hmac = params.putObject("hmac");
        hmac.put("public_key", c.publicKey());
        hmac.put("nonce", c.nonce());
        hmac.put("unix_ts", c.unixTs());
        hmac.put("signature", c.signature());
        if (accountId != null && !accountId.isBlank()) {
            params.put("account_id", accountId);
        }
        return msg;
    }

    @Override
    protected void onMessage(JsonNode msg) {
        if (isAuthReply(msg)) {
            if (isAuthSuccess(msg)) {
                onAuthSucceeded();
            } else {
                logger.error("QFEX trade socket authentication failed: {}", msg);
            }
            return;
        }
        JsonNode err = errorBody(msg);
        if (err != null) {
            handleError(err);
            return;
        }
        if (msg.has(ORDER_RESPONSE)) {
            JsonNode body = msg.get(ORDER_RESPONSE);
            listeners.forEach(l -> l.onOrderResponse(body));
            resolve(ORDER_RESPONSE, body);
        } else if (msg.has("fill_response")) {
            JsonNode body = msg.get("fill_response");
            listeners.forEach(l -> l.onFill(body));
        } else if (msg.has("position_response")) {
            JsonNode body = msg.get("position_response");
            listeners.forEach(l -> l.onPosition(body));
        } else if (msg.has("balance_response")) {
            JsonNode body = msg.get("balance_response");
            listeners.forEach(l -> l.onBalance(body));
        } else if (msg.has(ALL_ORDERS_RESPONSE)) {
            resolve(ALL_ORDERS_RESPONSE, msg.get(ALL_ORDERS_RESPONSE));
        } else if (msg.has("stop_order_response")) {
            resolve("stop_order_response", msg.get("stop_order_response"));
        } else if (msg.has(ACK)) {
            resolve(ACK, msg.get(ACK));
        }
    }

    private void onAuthSucceeded() {
        if (authenticated) {
            return;
        }
        authenticated = true;
        logger.info("QFEX trade socket authenticated");
        for (String ch : new String[] { "order_responses", "fills", "positions", "balances" }) {
            ObjectNode sub = MAPPER.createObjectNode();
            sub.put("type", "subscribe");
            sub.putObject("params").putArray("channels").add(ch);
            send(sub);
        }
        if (cancelOnDisconnect) {
            ObjectNode cod = MAPPER.createObjectNode();
            cod.put("type", "cancel_on_disconnect");
            cod.putObject("params").put("cancel_on_disconnect", true);
            send(cod);
        }
        listeners.forEach(QfexTradeListener::onAuthenticated);
    }

    static boolean isAuthReply(JsonNode msg) {
        return msg.has("authenticated") || "auth".equals(msg.path("type").asText(null));
    }

    static boolean isAuthSuccess(JsonNode msg) {
        if (msg.has("authenticated")) {
            return msg.path("authenticated").asBoolean(false);
        }
        return "success".equalsIgnoreCase(msg.path("result").asText(""));
    }

    /** Both documented error shapes: {@code {"err":{...}}} and flat {@code {"type":"Err",...}}. */
    static JsonNode errorBody(JsonNode msg) {
        if (msg.has("err")) {
            return msg.get("err");
        }
        if ("err".equalsIgnoreCase(msg.path("type").asText(""))) {
            return msg;
        }
        return null;
    }

    private void handleError(JsonNode err) {
        JsonNode incoming = err.path("incoming_message");
        JsonNode params = incoming.path("params");
        String clientId = params.path("client_order_id").asText(null);
        String orderId = params.hasNonNull("order_id") ? params.get("order_id").asText()
                : params.path("stop_order_id").asText(null);
        ObjectNode wrapped = MAPPER.createObjectNode();
        wrapped.set("err", err);

        Pending target = null;
        for (Pending p : pending) {
            JsonNode sentParams = p.message.path("params");
            if (!incoming.isMissingNode() && p.message.path("type").asText("").equals(incoming.path("type").asText(""))
                    && (clientId == null || clientId.equals(sentParams.path("client_order_id").asText(null)))
                    && (orderId == null || orderId.equals(sentParams.path("order_id").asText(null))
                            || orderId.equals(sentParams.path("stop_order_id").asText(null)))) {
                target = p;
                break;
            }
        }
        if (target == null && incoming.isMissingNode() && !pending.isEmpty()) {
            target = pending.get(0);
        }
        if (target != null && pending.remove(target)) {
            target.future.complete(wrapped);
        } else {
            logger.warn("QFEX error not matched to a request: {}", err);
            listeners.forEach(l -> l.onError(err));
        }
    }

    private void resolve(String key, JsonNode body) {
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.key.equals(key) && p.matcher.test(body)) {
                if (pending.remove(p)) {
                    p.future.complete(body);
                }
                return;
            }
        }
    }

    private record Pending(String key, Predicate<JsonNode> matcher, ObjectNode message,
            CompletableFuture<JsonNode> future) {
    }
}
