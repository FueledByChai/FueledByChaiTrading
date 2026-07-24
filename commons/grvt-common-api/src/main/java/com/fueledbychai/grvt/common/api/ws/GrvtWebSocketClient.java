package com.fueledbychai.grvt.common.api.ws;

import java.net.Proxy;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueledbychai.websocket.ProxyConfig;

/**
 * WebSocket client for a single GRVT {@code /ws/full} JSON-RPC endpoint (market-data or trade-data).
 * <p>
 * Beyond a plain stream client it provides two things GRVT needs:
 * <ul>
 *   <li><b>RPC correlation</b> &mdash; {@link #rpc(String, JsonNode)} sends a JSON-RPC request with a
 *       unique id and returns a {@link CompletableFuture} completed when the matching response
 *       arrives. This backs WS order entry.</li>
 *   <li><b>Feed dispatch</b> &mdash; {@link #subscribe(String, String, Consumer)} registers a callback
 *       keyed by {@code stream/selector}; inbound {@code feed} messages are routed to it. Stored
 *       subscriptions are replayed on reconnect.</li>
 * </ul>
 * Trade-data sockets pass the session {@code Cookie} and {@code X-Grvt-Account-Id} on the handshake,
 * supplied via the {@code httpHeaders} constructor argument.
 */
public class GrvtWebSocketClient extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(GrvtWebSocketClient.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<JsonNode>> pendingRpc = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonNode>> feedCallbacks = new ConcurrentHashMap<>();
    private final Map<String, String[]> subscriptions = new ConcurrentHashMap<>();
    private final String apiWsVersion;

    private volatile BiConsumer<Integer, String> closeListener;

    public GrvtWebSocketClient(URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders == null ? Map.of() : httpHeaders);
        this.apiWsVersion = "v1";
        Proxy proxy = ProxyConfig.getInstance().getProxy();
        if (proxy != null && proxy != Proxy.NO_PROXY) {
            setProxy(proxy);
        }
    }

    public void setCloseListener(BiConsumer<Integer, String> closeListener) {
        this.closeListener = closeListener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("Connected to GRVT WebSocket {}", getURI());
        // Replay subscriptions after a (re)connect.
        for (String[] streamSelector : subscriptions.values()) {
            sendSubscribe(streamSelector[0], streamSelector[1]);
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            if (node.has("feed")) {
                dispatchFeed(node);
            } else if (node.has("id") && node.get("id").isInt()) {
                CompletableFuture<JsonNode> future = pendingRpc.remove(node.get("id").asInt());
                if (future != null) {
                    future.complete(node);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to handle GRVT websocket message: {}", message, e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("GRVT WebSocket closed {} code={} reason={} remote={}", getURI(), code, reason, remote);
        failPending(new IllegalStateException("GRVT websocket closed: " + reason));
        BiConsumer<Integer, String> listener = closeListener;
        if (listener != null) {
            listener.accept(code, reason);
        }
    }

    @Override
    public void onError(Exception ex) {
        logger.error("GRVT WebSocket error {}", getURI(), ex);
    }

    /**
     * Sends a JSON-RPC request and returns a future completed with the response node. The caller is
     * responsible for applying a timeout.
     */
    public CompletableFuture<JsonNode> rpc(String method, JsonNode params) {
        int id = requestId.incrementAndGet();
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }
        request.put("id", id);

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRpc.put(id, future);
        try {
            send(request.toString());
        } catch (Exception e) {
            pendingRpc.remove(id);
            future.completeExceptionally(e);
        }
        return future;
    }

    /** Registers a feed callback and subscribes (if connected) to {@code stream/selector}. */
    public void subscribe(String stream, String selector, Consumer<JsonNode> callback) {
        String versionedStream = versioned(stream);
        feedCallbacks.put(feedKey(versionedStream, selector), callback);
        subscriptions.put(feedKey(versionedStream, selector), new String[] { stream, selector });
        if (isOpen()) {
            sendSubscribe(stream, selector);
        }
    }

    private void sendSubscribe(String stream, String selector) {
        int id = requestId.incrementAndGet();
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", "subscribe");
        ObjectNode params = request.putObject("params");
        params.put("stream", versioned(stream));
        params.putArray("selectors").add(selector);
        request.put("id", id);
        try {
            send(request.toString());
        } catch (Exception e) {
            logger.warn("Failed to subscribe GRVT stream {} selector {}", stream, selector, e);
        }
    }

    private void dispatchFeed(JsonNode node) {
        String stream = node.path("stream").asText("");
        String selector = node.path("selector").asText("");
        Consumer<JsonNode> callback = feedCallbacks.get(feedKey(stream, selector));
        if (callback != null) {
            callback.accept(node);
        }
    }

    private void failPending(Exception cause) {
        for (CompletableFuture<JsonNode> future : pendingRpc.values()) {
            future.completeExceptionally(cause);
        }
        pendingRpc.clear();
    }

    private String versioned(String stream) {
        return apiWsVersion + "." + stream;
    }

    private static String feedKey(String versionedStream, String selector) {
        return versionedStream + "|" + selector;
    }
}
