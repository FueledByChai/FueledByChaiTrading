package com.fueledbychai.qfex.common.api.ws;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Public market data ({@code wss://mds.qfex.com}). Subscriptions are
 * remembered and replayed after every reconnect. Messages are delivered
 * as-is; each carries a {@code type} (bbo, trade, mark_price, level2, ...).
 *
 * <p>Subscribe shape: {@code {"type":"subscribe","channels":[...],"symbols":[...]}}
 * (channels and symbols at the top level, unlike the trade socket).
 */
public class QfexMarketDataStream extends QfexStream {

    private final String url;
    /** channel -> symbols */
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final List<Consumer<JsonNode>> listeners = new CopyOnWriteArrayList<>();

    public QfexMarketDataStream(String url) {
        super("mds", Duration.ofSeconds(60));
        this.url = url;
    }

    public void addListener(Consumer<JsonNode> listener) {
        listeners.add(listener);
    }

    /** Subscribes (and remembers) {@code channels} for {@code symbol}. */
    public void subscribe(String symbol, String... channels) {
        for (String ch : channels) {
            subscriptions.computeIfAbsent(ch, k -> ConcurrentHashMap.newKeySet()).add(symbol);
        }
        if (isOpen()) {
            send(subscribeMessage(List.of(channels), List.of(symbol)));
        }
    }

    public void clearSubscriptions() {
        subscriptions.clear();
    }

    static ObjectNode subscribeMessage(List<String> channels, List<String> symbols) {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "subscribe");
        ArrayNode ch = msg.putArray("channels");
        channels.forEach(ch::add);
        ArrayNode sy = msg.putArray("symbols");
        symbols.forEach(sy::add);
        return msg;
    }

    @Override
    protected String connectUrl() {
        return url;
    }

    @Override
    protected void onOpened() {
        subscriptions.forEach((channel, symbols) -> {
            if (!symbols.isEmpty()) {
                send(subscribeMessage(List.of(channel), List.copyOf(symbols)));
            }
        });
    }

    @Override
    protected void onClosed() {
    }

    @Override
    protected void onMessage(JsonNode message) {
        for (Consumer<JsonNode> l : listeners) {
            l.accept(message);
        }
    }
}
