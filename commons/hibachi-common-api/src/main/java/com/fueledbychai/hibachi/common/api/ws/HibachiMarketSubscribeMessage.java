package com.fueledbychai.hibachi.common.api.ws;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Builder for Hibachi market WebSocket subscribe / unsubscribe messages.
 *
 * <p>Note the outer key is {@code "parameters"} (not {@code "params"}) — this is unique to
 * the market WS. See {@code hibachi_xyz/api_ws_market.py:95-129} in the Python SDK.
 *
 * <p>Example output:
 * <pre>
 * {"method":"subscribe","parameters":{"subscriptions":[{"symbol":"BTC/USDT-P","topic":"mark_price"}]}}
 * </pre>
 */
public final class HibachiMarketSubscribeMessage {

    public static final String METHOD_SUBSCRIBE = "subscribe";
    public static final String METHOD_UNSUBSCRIBE = "unsubscribe";

    private HibachiMarketSubscribeMessage() {
    }

    public static String subscribe(String symbol, String topic) {
        return build(METHOD_SUBSCRIBE, List.of(new Subscription(symbol, topic)));
    }

    public static String subscribe(Collection<Subscription> subscriptions) {
        return build(METHOD_SUBSCRIBE, subscriptions);
    }

    public static String unsubscribe(String symbol, String topic) {
        return build(METHOD_UNSUBSCRIBE, List.of(new Subscription(symbol, topic)));
    }

    public static String unsubscribe(Collection<Subscription> subscriptions) {
        return build(METHOD_UNSUBSCRIBE, subscriptions);
    }

    private static String build(String method, Collection<Subscription> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            throw new IllegalArgumentException("subscriptions must be non-empty");
        }
        JSONArray subs = new JSONArray();
        for (Subscription s : new ArrayList<>(subscriptions)) {
            JSONObject sub = new JSONObject();
            sub.put("symbol", s.symbol);
            sub.put("topic", s.topic);
            subs.put(sub);
        }
        JSONObject parameters = new JSONObject();
        parameters.put("subscriptions", subs);
        JSONObject root = new JSONObject();
        root.put("method", method);
        root.put("parameters", parameters);
        return root.toString();
    }

    public static final class Subscription {
        public final String symbol;
        public final String topic;

        public Subscription(String symbol, String topic) {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException("symbol is required");
            }
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("topic is required");
            }
            this.symbol = symbol;
            this.topic = topic;
        }
    }
}
