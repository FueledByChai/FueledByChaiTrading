package com.fueledbychai.bybit.common.api;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.google.gson.JsonObject;

/**
 * Public REST contract for the Bybit exchange integration.
 *
 * Keep this interface small and stable. Strategy code and higher-level factory
 * consumers should depend on this interface, while the concrete implementation
 * handles transport details, authentication, and payload normalization.
 */
public interface IBybitRestApi {

    /**
     * Returns known instrument descriptors for the requested instrument type.
     *
     * @param instrumentType the instrument type to load
     * @return the resolved instrument descriptors
     */
    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    /**
     * Returns option instrument descriptors scoped to a specific base coin.
     *
     * Implementations may return an empty array when the base coin is unknown or
     * unsupported.
     *
     * @param baseCoin option base coin symbol (for example BTC or ETH)
     * @return option descriptors for the base coin
     */
    default InstrumentDescriptor[] getOptionInstrumentsForBaseCoin(String baseCoin) {
        return getAllInstrumentsForType(InstrumentType.OPTION);
    }

    /**
     * Resolves a single instrument descriptor by symbol.
     *
     * @param symbol the exchange symbol
     * @return the resolved descriptor, or {@code null} when unavailable
     */
    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    /**
     * Returns the public ticker snapshot for the given category and symbol.
     *
     * The response contains the full Bybit API payload including best bid/ask
     * prices and sizes from the {@code /v5/market/tickers} endpoint.
     *
     * @param category the Bybit product category (e.g. {@code linear}, {@code spot})
     * @param symbol   the Bybit symbol (e.g. {@code BTCUSDT})
     * @return the parsed JSON response object
     */
    JsonObject getTicker(String category, String symbol);

    /**
     * Indicates whether this API instance was created without private
     * credentials.
     *
     * @return {@code true} when the API instance is public-only
     */
    boolean isPublicApiOnly();
}
