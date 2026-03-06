package com.fueledbychai.okx.common.api;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

/**
 * Public REST contract for the Okx exchange integration.
 *
 * Keep this interface small and stable. Strategy code and higher-level factory
 * consumers should depend on this interface, while the concrete implementation
 * handles transport details, authentication, and payload normalization.
 */
public interface IOkxRestApi {

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
     * Indicates whether this API instance was created without private
     * credentials.
     *
     * @return {@code true} when the API instance is public-only
     */
    boolean isPublicApiOnly();
}
