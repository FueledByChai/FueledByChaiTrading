package com.fueledbychai.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.binance.model.BinanceInstrumentDescriptorResult;
import com.fueledbychai.data.InstrumentType;

public interface IBinanceRestApi {

    BinanceInstrumentDescriptorResult getAllInstrumentsForType(InstrumentType instrumentType);

    /**
     * Returns the best bid/offer (book ticker) for the given symbol.
     *
     * @param symbol the exchange symbol
     * @return the JSON response containing bidPrice, bidQty, askPrice, askQty
     */
    JsonNode getBookTicker(String symbol);

}