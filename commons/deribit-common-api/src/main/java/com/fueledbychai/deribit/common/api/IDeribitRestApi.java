package com.fueledbychai.deribit.common.api;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.google.gson.JsonObject;

/**
 * Public REST contract for the Deribit exchange integration.
 *
 * Keep this interface small and stable. Strategy code and higher-level factory
 * consumers should depend on this interface, while the concrete implementation
 * handles transport details, authentication, and payload normalization.
 */
public interface IDeribitRestApi {

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
     * Returns the public ticker snapshot for the given instrument name.
     *
     * The response contains best bid/ask prices and amounts as well as other
     * market statistics provided by the Deribit {@code public/ticker} endpoint.
     *
     * @param instrumentName the Deribit instrument name (e.g. {@code BTC-PERPETUAL})
     * @return the parsed JSON response object
     */
    JsonObject getTicker(String instrumentName);

    /**
     * Indicates whether this API instance was created without private
     * credentials.
     *
     * @return {@code true} when the API instance is public-only
     */
    boolean isPublicApiOnly();
}
