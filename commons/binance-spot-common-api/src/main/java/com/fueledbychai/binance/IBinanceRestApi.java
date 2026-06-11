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

    /**
     * Returns a full order-book depth snapshot ({@code /depth}) used to seed/anchor an absolute
     * book before applying the {@code @depth} diff stream. The response carries {@code lastUpdateId}
     * plus {@code bids}/{@code asks} arrays.
     *
     * @param symbol the exchange symbol
     * @param limit  number of levels (Binance valid values: 5, 10, 20, 50, 100, 500, 1000, 5000)
     * @return the JSON depth snapshot
     */
    JsonNode getDepthSnapshot(String symbol, int limit);

}