package com.fueledbychai.paradex.common.api;

import java.util.List;
import java.util.Map;

import com.fueledbychai.broker.Position;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.google.gson.JsonObject;
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

    /**
     * Cancels multiple orders in a single batch request.
     *
     * <p>Paradex's {@code /orders/batch} endpoint accepts only exchange order IDs
     * (rejects client order IDs with {@code INVALID_REQUEST_PARAMETER}). For
     * orders that have only a client ID, fall back to
     * {@link #cancelOrderByClientOrderId(String, String)} per-order.
     *
     * @param jwtToken Authentication token.
     * @param orderIds Paradex order IDs to cancel; must be non-empty.
     * @return The raw REST response. The response body contains a
     *         {@code results} array with per-ID outcomes (status values such
     *         as {@code QUEUED_FOR_CANCELLATION}, {@code ALREADY_CLOSED},
     *         {@code NOT_FOUND}).
     */
    RestResponse cancelOrderBatch(String jwtToken, List<String> orderIds);

    ParadexOrder getOrderByClientOrderId(String jwtToken, String clientOrderId);

    String placeOrder(String jwtToken, ParadexOrder tradeOrder);

    String modifyOrder(String jwtToken, ParadexOrder tradeOrder);

    String getJwtToken();

    String getJwtToken(Map<String, String> headers);

    // String getOrderMessageSignature(String orderMessage);

    boolean isPublicApiOnly();

    /**
     * Returns the best bid/offer (BBO) data for the given market.
     *
     * @param market the Paradex market symbol (e.g. {@code BTC-USD-PERP})
     * @return the parsed JSON response object containing bid/ask prices and sizes
     */
    JsonObject getBBO(String market);

    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    InstrumentDescriptor[] getAllInstrumentsForTypes(InstrumentType[] instrumentTypes);

    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    boolean onboardAccount(String ethereumAddress, String starketAddress, boolean isTestnet) throws Exception;

    /**
     * Returns the current status of the Paradex system.
     *
     * This is the canonical method for retrieving system status information.
     *
     * @return the current {@link SystemStatus} of the system
     */
    SystemStatus getSystemStatus();

    /**
     * Alias for {@link #getSystemStatus()} retained for backward compatibility.
     * <p>
     * New code should call {@link #getSystemStatus()} instead.
     *
     * @return the current {@link SystemStatus} of the system
     */
    @Deprecated
    SystemStatus getSystemState();

}
