package com.fueledbychai.paradex.common.api;

import java.util.List;
import java.util.Map;

import com.fueledbychai.broker.Position;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.paradex.common.api.historical.OHLCBar;
import com.fueledbychai.paradex.common.api.order.ParadexOrder;
import com.fueledbychai.paradex.common.api.ws.SystemStatus;

public interface IParadexRestApi {

    List<Position> getPositionInfo(String jwtToken);

    /**
     * Supported resolutions
     * 
     * resolution in minutes: 1, 3, 5, 15, 30, 60
     * 
     * 
     * @param symbol
     * @param resolutionInMinutes
     * @param lookbackInMinutes
     * @param priceKind
     * @return
     */
    List<OHLCBar> getOHLCBars(String symbol, int resolutionInMinutes, int lookbackInMinutes,
            HistoricalPriceKind priceKind);

    List<ParadexOrder> getOpenOrders(String jwtToken);

    List<ParadexOrder> getOpenOrders(String jwtToken, String market);

    RestResponse cancelOrder(String jwtToken, String orderId);

    RestResponse cancelOrderByClientOrderId(String jwtToken, String clientOrderId);

    ParadexOrder getOrderByClientOrderId(String jwtToken, String clientOrderId);

    String placeOrder(String jwtToken, ParadexOrder tradeOrder);

    String modifyOrder(String jwtToken, ParadexOrder tradeOrder);

    String getJwtToken();

    String getJwtToken(Map<String, String> headers);

    // String getOrderMessageSignature(String orderMessage);

    boolean isPublicApiOnly();

    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    boolean onboardAccount(String ethereumAddress, String starketAddress, boolean isTestnet) throws Exception;

    SystemStatus getSystemStatus();

}