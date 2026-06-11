package com.fueledbychai.binancefutures.common.api;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

/**
 * Public REST contract for the BinanceFutures exchange integration.
 *
 * Keep this interface small and stable. Strategy code and higher-level factory
 * consumers should depend on this interface, while the concrete implementation
 * handles transport details, authentication, and payload normalization.
 */
public interface IBinanceFuturesRestApi {

    /**
     * Returns known instrument descriptors for the requested instrument type.
     *
     * @param instrumentType the instrument type to load
     * @return the resolved instrument descriptors
     */
    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    /**
     * Resolves a single instrument descriptor by symbol.
     *
     * @param symbol the exchange symbol
     * @return the resolved descriptor, or {@code null} when unavailable
     */
    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    /**
     * Returns the current Binance Futures server time.
     *
     * @return the exchange server time
     */
    Date getServerTime();

    /**
     * Returns the best bid/offer (book ticker) for the given symbol.
     *
     * @param symbol the exchange symbol
     * @return the JSON response containing bidPrice, bidQty, askPrice, askQty
     */
    JsonNode getBookTicker(String symbol);

    /**
     * Returns a full order-book depth snapshot ({@code /fapi/v1/depth}) used to seed/anchor an
     * absolute book before applying the {@code @depth} diff stream. The response carries
     * {@code lastUpdateId} plus {@code bids}/{@code asks} arrays.
     *
     * @param symbol the exchange symbol
     * @param limit  number of levels (Binance valid values: 5, 10, 20, 50, 100, 500, 1000)
     * @return the JSON depth snapshot
     */
    JsonNode getDepthSnapshot(String symbol, int limit);

    /**
     * Returns the premium index ({@code /fapi/v1/premiumIndex}) for the given symbol, which
     * carries {@code markPrice}, {@code indexPrice}, {@code lastFundingRate}, {@code nextFundingTime}
     * and {@code time}. Default throws {@link UnsupportedOperationException}; the concrete REST
     * implementation overrides it.
     *
     * @param symbol the exchange symbol
     * @return the JSON premium-index response
     */
    default JsonNode getPremiumIndex(String symbol) {
        throw new UnsupportedOperationException("getPremiumIndex not supported by this implementation");
    }

    /**
     * Indicates whether this API instance was created without private
     * credentials.
     *
     * @return {@code true} when the API instance is public-only
     */
    boolean isPublicApiOnly();
}
