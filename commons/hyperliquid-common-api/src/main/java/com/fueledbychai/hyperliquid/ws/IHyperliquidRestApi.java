package com.fueledbychai.hyperliquid.ws;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.hyperliquid.ws.json.SignableExchangeOrderRequest;

public interface IHyperliquidRestApi {

    boolean isPublicApiOnly();

    String placeOrder(SignableExchangeOrderRequest order);

    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

}