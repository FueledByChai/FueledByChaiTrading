package com.fueledbychai.drift.common.api.model;

import java.util.Collections;
import java.util.List;

public class DriftGatewayCancelRequest {

    private final Integer marketIndex;
    private final DriftMarketType marketType;
    private final List<Long> orderIds;
    private final List<Integer> userOrderIds;

    public DriftGatewayCancelRequest(Integer marketIndex, DriftMarketType marketType, List<Long> orderIds,
            List<Integer> userOrderIds) {
        this.marketIndex = marketIndex;
        this.marketType = marketType;
        this.orderIds = orderIds == null ? Collections.emptyList() : Collections.unmodifiableList(orderIds);
        this.userOrderIds = userOrderIds == null ? Collections.emptyList() : Collections.unmodifiableList(userOrderIds);
    }

    public Integer getMarketIndex() {
        return marketIndex;
    }

    public DriftMarketType getMarketType() {
        return marketType;
    }

    public List<Long> getOrderIds() {
        return orderIds;
    }

    public List<Integer> getUserOrderIds() {
        return userOrderIds;
    }
}
