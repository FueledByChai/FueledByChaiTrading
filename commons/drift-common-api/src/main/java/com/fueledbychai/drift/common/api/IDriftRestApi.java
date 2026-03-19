package com.fueledbychai.drift.common.api;

import java.math.BigDecimal;
import java.util.List;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.drift.common.api.model.DriftGatewayCancelRequest;
import com.fueledbychai.drift.common.api.model.DriftGatewayMarket;
import com.fueledbychai.drift.common.api.model.DriftGatewayOrder;
import com.fueledbychai.drift.common.api.model.DriftGatewayOrderRequest;
import com.fueledbychai.drift.common.api.model.DriftGatewayPosition;
import com.fueledbychai.drift.common.api.model.DriftMarket;
import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.drift.common.api.model.DriftOrderBookSnapshot;

public interface IDriftRestApi {

    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    List<DriftMarket> getMarkets();

    DriftMarket getMarket(String symbol);

    DriftOrderBookSnapshot getOrderBook(String marketName, DriftMarketType marketType);

    List<DriftGatewayMarket> getGatewayMarkets();

    List<DriftGatewayOrder> getOpenOrders();

    List<DriftGatewayPosition> getPositions();

    DriftGatewayPosition getPerpPositionInfo(int marketIndex);

    String placeOrder(DriftGatewayOrderRequest orderRequest);

    String modifyOrder(DriftGatewayOrderRequest orderRequest);

    String cancelOrder(DriftGatewayCancelRequest cancelRequest);

    String cancelAllOrders(Integer marketIndex, DriftMarketType marketType);

    BigDecimal getTotalCollateral();

    BigDecimal getFreeCollateral();

    BigDecimal getInitialMarginRequirement();

    BigDecimal getMaintenanceMarginRequirement();

    boolean isPublicApiOnly();
}
