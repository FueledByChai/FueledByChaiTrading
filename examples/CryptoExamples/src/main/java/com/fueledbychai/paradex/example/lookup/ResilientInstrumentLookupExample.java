package com.fueledbychai.paradex.example.lookup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.paradex.common.api.ParadexInstrumentLookup;

/**
 * Example demonstrating the resilient Paradex instrument lookup functionality.
 * This example shows how the lookup will automatically retry on temporary
 * failures while failing fast on permanent errors.
 */
public class ResilientInstrumentLookupExample {

    private static final Logger logger = LoggerFactory.getLogger(ResilientInstrumentLookupExample.class);

    public void demonstrateResilientLookup() {
        // Create the resilient instrument lookup (uses default retry configuration)
        ParadexInstrumentLookup lookup = new ParadexInstrumentLookup();

        try {
            // This lookup will automatically retry if it encounters:
            // - Network timeouts
            // - Connection errors
            // - HTTP 5xx server errors
            // - Rate limiting (HTTP 429)
            //
            // But will NOT retry for:
            // - HTTP 4xx client errors (except 429)
            // - Invalid symbols (404)
            // - Authentication errors (401, 403)

            logger.info("Looking up BTC perpetual futures instrument...");
            InstrumentDescriptor btcInstrument = lookup.lookupByExchangeSymbol("BTC-USD-PERP");
            logger.info("Successfully retrieved BTC instrument: {}", btcInstrument);

            // Example using common symbol (this internally converts to exchange symbol)
            logger.info("Looking up ETH using common symbol...");
            InstrumentDescriptor ethInstrument = lookup.lookupByCommonSymbol("ETH");
            logger.info("Successfully retrieved ETH instrument: {}", ethInstrument);

            // Get all perpetual futures instruments
            logger.info("Fetching all perpetual futures instruments...");
            InstrumentDescriptor[] allInstruments = lookup.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
            logger.info("Successfully retrieved {} instruments", allInstruments.length);

        } catch (FueledByChaiException e) {
            // This will be thrown if:
            // 1. All retry attempts are exhausted for temporary errors
            // 2. A permanent error occurs (won't retry)
            logger.error("Failed to lookup instrument after retries: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during lookup: {}", e.getMessage(), e);
        }
    }

    public void demonstrateErrorHandling() {
        ParadexInstrumentLookup lookup = new ParadexInstrumentLookup();

        try {
            // This should fail quickly with a permanent error (no retries)
            logger.info("Attempting to lookup invalid symbol...");
            lookup.lookupByExchangeSymbol("INVALID-SYMBOL-123");

        } catch (FueledByChaiException e) {
            logger.info("Expected permanent error occurred: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        ResilientInstrumentLookupExample example = new ResilientInstrumentLookupExample();

        logger.info("=== Demonstrating Resilient Instrument Lookup ===");
        example.demonstrateResilientLookup();

        logger.info("\n=== Demonstrating Error Handling ===");
        example.demonstrateErrorHandling();

        logger.info("\nExample completed.");
    }
}