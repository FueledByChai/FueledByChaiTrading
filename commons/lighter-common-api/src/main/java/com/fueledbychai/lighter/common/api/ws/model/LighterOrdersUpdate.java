package com.fueledbychai.lighter.common.api.ws.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LighterOrdersUpdate {

    private final String channel;
    private final String messageType;
    private final Long accountIndex;
    private final Long nonce;
    private final Map<Integer, List<LighterOrder>> ordersByMarket;
    private final List<LighterOrder> allOrders;

    public LighterOrdersUpdate(String channel, String messageType, Long accountIndex, Long nonce,
            Map<Integer, List<LighterOrder>> ordersByMarket) {
        this.channel = channel;
        this.messageType = messageType;
        this.accountIndex = accountIndex;
        this.nonce = nonce;
        this.ordersByMarket = toUnmodifiableMap(ordersByMarket);
        this.allOrders = flattenOrders(this.ordersByMarket);
    }

    public String getChannel() {
        return channel;
    }

    public String getMessageType() {
        return messageType;
    }

    public Long getAccountIndex() {
        return accountIndex;
    }

    public Long getNonce() {
        return nonce;
    }

    public Map<Integer, List<LighterOrder>> getOrdersByMarket() {
        return ordersByMarket;
    }

    public List<LighterOrder> getOrders() {
        return allOrders;
    }

    public List<LighterOrder> getOrdersForMarket(int marketIndex) {
        List<LighterOrder> values = ordersByMarket.get(Integer.valueOf(marketIndex));
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values;
    }

    public LighterOrder getFirstOrder() {
        if (allOrders.isEmpty()) {
            return null;
        }
        return allOrders.get(0);
    }

    public int getTotalOrderCount() {
        return allOrders.size();
    }

    private Map<Integer, List<LighterOrder>> toUnmodifiableMap(Map<Integer, List<LighterOrder>> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<LighterOrder>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<LighterOrder>> entry : values.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            List<LighterOrder> orders = entry.getValue();
            if (orders == null || orders.isEmpty()) {
                continue;
            }
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(orders)));
        }
        if (copy.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(copy);
    }

    private List<LighterOrder> flattenOrders(Map<Integer, List<LighterOrder>> byMarket) {
        if (byMarket == null || byMarket.isEmpty()) {
            return Collections.emptyList();
        }
        List<LighterOrder> values = new ArrayList<>();
        for (List<LighterOrder> orders : byMarket.values()) {
            if (orders != null && !orders.isEmpty()) {
                values.addAll(orders);
            }
        }
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(values);
    }
}
