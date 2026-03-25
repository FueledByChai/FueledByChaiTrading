package com.fueledbychai.hyperliquid.ws;

import com.google.gson.JsonObject;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.hyperliquid.ws.json.SignableExchangeOrderRequest;

public interface IHyperliquidRestApi {

    boolean isPublicApiOnly();

    String placeOrder(SignableExchangeOrderRequest order);

    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    /**
     * Returns the L2 order book for the given coin.
     *
     * @param coin the coin symbol, for example {@code BTC}
     * @return the JSON response containing levels array
     */
    JsonObject getL2Book(String coin);

}