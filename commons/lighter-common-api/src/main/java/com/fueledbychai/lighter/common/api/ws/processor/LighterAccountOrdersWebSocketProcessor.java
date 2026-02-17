package com.fueledbychai.lighter.common.api.ws.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.lighter.common.api.ws.client.LighterWSClientBuilder;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrdersUpdate;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class LighterAccountOrdersWebSocketProcessor extends AbstractWebSocketProcessor<LighterOrdersUpdate> {

    private static final Logger logger = LoggerFactory.getLogger(LighterAccountOrdersWebSocketProcessor.class);

    public LighterAccountOrdersWebSocketProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected LighterOrdersUpdate parseMessage(String message) {
        JSONObject root = new JSONObject(message);
        String channel = root.optString("channel", null);
        if (!isAccountOrdersChannel(channel)) {
            return null;
        }

        Integer channelMarketIndex = parseMarketIndexFromChannel(channel);
        Map<Integer, List<LighterOrder>> ordersByMarket = parseOrdersByMarket(root.opt("orders"), channelMarketIndex);
        if (ordersByMarket.isEmpty()) {
            return null;
        }

        String messageType = parseString(root.opt("type"));
        Long accountIndex = parseLong(root.opt("account"));
        Long nonce = parseLong(root.opt("nonce"));
        return new LighterOrdersUpdate(channel, messageType, accountIndex, nonce, ordersByMarket);
    }

    protected boolean isAccountOrdersChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_ACCOUNT_ORDERS;
        return channel.startsWith(prefix + "/") || channel.startsWith(prefix + ":");
    }

    protected Integer parseMarketIndexFromChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_ACCOUNT_ORDERS;
        if (channel.startsWith(prefix + ":")) {
            return parseInteger(channel.substring((prefix + ":").length()));
        }
        if (channel.startsWith(prefix + "/")) {
            String remainder = channel.substring((prefix + "/").length());
            int separator = remainder.indexOf('/');
            String marketText = separator >= 0 ? remainder.substring(0, separator) : remainder;
            return parseInteger(marketText);
        }
        return null;
    }

    protected Map<Integer, List<LighterOrder>> parseOrdersByMarket(Object ordersValue, Integer fallbackMarketIndex) {
        Map<Integer, List<LighterOrder>> byMarket = new LinkedHashMap<>();
        if (ordersValue instanceof JSONObject ordersByMarketJson) {
            Iterator<String> keys = ordersByMarketJson.keys();
            while (keys.hasNext()) {
                String marketKey = keys.next();
                Integer marketIndex = parseInteger(marketKey);
                if (marketIndex == null) {
                    marketIndex = fallbackMarketIndex;
                }

                Object marketOrdersValue = ordersByMarketJson.opt(marketKey);
                if (!(marketOrdersValue instanceof JSONArray marketOrdersJson)) {
                    continue;
                }
                List<LighterOrder> parsedOrders = parseOrders(marketOrdersJson, marketIndex);
                if (parsedOrders.isEmpty()) {
                    continue;
                }

                Integer resolvedMarketIndex = marketIndex;
                if (resolvedMarketIndex == null) {
                    LighterOrder firstOrder = parsedOrders.get(0);
                    if (firstOrder != null) {
                        resolvedMarketIndex = firstOrder.getMarketIndex();
                    }
                }
                if (resolvedMarketIndex != null) {
                    byMarket.put(resolvedMarketIndex, parsedOrders);
                }
            }
            return byMarket;
        }

        if (ordersValue instanceof JSONArray ordersArray) {
            List<LighterOrder> parsedOrders = parseOrders(ordersArray, fallbackMarketIndex);
            if (!parsedOrders.isEmpty()) {
                Integer resolvedMarketIndex = fallbackMarketIndex;
                if (resolvedMarketIndex == null) {
                    LighterOrder firstOrder = parsedOrders.get(0);
                    if (firstOrder != null) {
                        resolvedMarketIndex = firstOrder.getMarketIndex();
                    }
                }
                if (resolvedMarketIndex != null) {
                    byMarket.put(resolvedMarketIndex, parsedOrders);
                }
            }
        }
        return byMarket;
    }

    protected List<LighterOrder> parseOrders(JSONArray ordersJson, Integer fallbackMarketIndex) {
        List<LighterOrder> orders = new ArrayList<>();
        if (ordersJson == null || ordersJson.isEmpty()) {
            return orders;
        }
        for (int i = 0; i < ordersJson.length(); i++) {
            Object value = ordersJson.opt(i);
            if (!(value instanceof JSONObject orderJson)) {
                continue;
            }
            LighterOrder order = parseOrder(orderJson, fallbackMarketIndex);
            if (order != null) {
                orders.add(order);
            }
        }
        return orders;
    }

    protected LighterOrder parseOrder(JSONObject orderJson, Integer fallbackMarketIndex) {
        if (orderJson == null) {
            return null;
        }
        LighterOrder order = new LighterOrder();
        order.setOrderIndex(parseLong(orderJson.opt("order_index")));
        order.setClientOrderIndex(parseLong(orderJson.opt("client_order_index")));
        order.setOrderId(parseString(orderJson.opt("order_id")));
        order.setClientOrderId(parseString(orderJson.opt("client_order_id")));

        Integer marketIndex = parseInteger(orderJson.opt("market_index"));
        if (marketIndex == null) {
            marketIndex = fallbackMarketIndex;
        }
        order.setMarketIndex(marketIndex);

        order.setOwnerAccountIndex(parseLong(orderJson.opt("owner_account_index")));
        order.setInitialBaseAmount(parseBigDecimal(orderJson.opt("initial_base_amount")));
        order.setPrice(parseBigDecimal(orderJson.opt("price")));
        order.setNonce(parseLong(orderJson.opt("nonce")));
        order.setRemainingBaseAmount(parseBigDecimal(orderJson.opt("remaining_base_amount")));
        order.setAsk(parseBoolean(orderJson.opt("is_ask")));
        order.setBaseSize(parseLong(orderJson.opt("base_size")));
        order.setBasePrice(parseLong(orderJson.opt("base_price")));
        order.setFilledBaseAmount(parseBigDecimal(orderJson.opt("filled_base_amount")));
        order.setFilledQuoteAmount(parseBigDecimal(orderJson.opt("filled_quote_amount")));
        order.setSide(parseString(orderJson.opt("side")));
        order.setType(parseString(orderJson.opt("type")));
        order.setTimeInForce(parseString(orderJson.opt("time_in_force")));
        order.setReduceOnly(parseBoolean(orderJson.opt("reduce_only")));
        order.setTriggerPrice(parseBigDecimal(orderJson.opt("trigger_price")));
        order.setOrderExpiry(parseLong(orderJson.opt("order_expiry")));
        order.setStatus(parseString(orderJson.opt("status")));
        order.setTriggerStatus(parseString(orderJson.opt("trigger_status")));
        order.setTriggerTime(parseLong(orderJson.opt("trigger_time")));
        order.setParentOrderIndex(parseLong(orderJson.opt("parent_order_index")));
        order.setParentOrderId(parseString(orderJson.opt("parent_order_id")));
        order.setToTriggerOrderId0(parseString(orderJson.opt("to_trigger_order_id_0")));
        order.setToTriggerOrderId1(parseString(orderJson.opt("to_trigger_order_id_1")));
        order.setToCancelOrderId0(parseString(orderJson.opt("to_cancel_order_id_0")));
        order.setBlockHeight(parseLong(orderJson.opt("block_height")));
        order.setTimestamp(parseLong(orderJson.opt("timestamp")));
        order.setCreatedAt(parseLong(orderJson.opt("created_at")));
        order.setUpdatedAt(parseLong(orderJson.opt("updated_at")));
        order.setTransactionTime(parseLong(orderJson.opt("transaction_time")));
        return order;
    }

    protected BigDecimal parseBigDecimal(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            logger.warn("Unable to parse decimal value '{}'", value);
            return null;
        }
    }

    protected Integer parseInteger(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (Exception e) {
            logger.warn("Unable to parse integer value '{}'", value);
            return null;
        }
    }

    protected Long parseLong(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (Exception e) {
            logger.warn("Unable to parse long value '{}'", value);
            return null;
        }
    }

    protected Boolean parseBoolean(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = value.toString();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    protected String parseString(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        String text = value.toString();
        if (text.isBlank()) {
            return null;
        }
        return text;
    }
}
