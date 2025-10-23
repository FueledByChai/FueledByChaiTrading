package com.fueledbychai.paradex.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

@ExtendWith(MockitoExtension.class)
class ResilientParadexInstrumentLookupTest {

    @Mock
    private IParadexRestApi mockApi;

    @Test
    void testSuccessfulLookupWithoutRetry() throws IOException {
        // Setup
        InstrumentDescriptor expectedDescriptor = mock(InstrumentDescriptor.class);
        when(mockApi.getInstrumentDescriptor("BTC-USD-PERP")).thenReturn(expectedDescriptor);

        ParadexInstrumentLookup lookup = new ParadexInstrumentLookup(mockApi);

        // Execute
        InstrumentDescriptor result = lookup.lookupByExchangeSymbol("BTC-USD-PERP");

        // Verify
        assertNotNull(result);
        assertEquals(expectedDescriptor, result);
        verify(mockApi, times(1)).getInstrumentDescriptor("BTC-USD-PERP");
    }

    @Test
    void testRetryOnTemporaryFailure() throws IOException {
        // Setup - fail twice with retryable exception, then succeed
        InstrumentDescriptor expectedDescriptor = mock(InstrumentDescriptor.class);
        when(mockApi.getInstrumentDescriptor("BTC-USD-PERP")).thenThrow(new IOException("HTTP 503 Service Unavailable"))
                .thenThrow(new IOException("Connection timeout")).thenReturn(expectedDescriptor);

        ParadexInstrumentLookup lookup = new ParadexInstrumentLookup(mockApi);

        // Execute
        InstrumentDescriptor result = lookup.lookupByExchangeSymbol("BTC-USD-PERP");

        // Verify
        assertNotNull(result);
        assertEquals(expectedDescriptor, result);
        // Should have been called 3 times (2 failures + 1 success)
        verify(mockApi, times(3)).getInstrumentDescriptor("BTC-USD-PERP");
    }

    @Test
    void testNoRetryOnPermanentFailure() throws IOException {
        // Setup - client error (404) should not be retried
        when(mockApi.getInstrumentDescriptor("INVALID-SYMBOL")).thenThrow(new IOException("HTTP 404 Not Found"));

        ParadexInstrumentLookup lookup = new ParadexInstrumentLookup(mockApi);

        // Execute & Verify
        assertThrows(FueledByChaiException.class, () -> {
            lookup.lookupByExchangeSymbol("INVALID-SYMBOL");
        });

        // Should have been called only once (no retries for 404)
        verify(mockApi, times(1)).getInstrumentDescriptor("INVALID-SYMBOL");
    }

    @Test
    void testRetryExhaustionThrowsException() throws IOException {
        // Setup - always throw retryable exception
        when(mockApi.getInstrumentDescriptor("BTC-USD-PERP"))
                .thenThrow(new IOException("HTTP 503 Service Unavailable"));

        ParadexInstrumentLookup lookup = new ParadexInstrumentLookup(mockApi);

        // Execute & Verify
        assertThrows(FueledByChaiException.class, () -> {
            lookup.lookupByExchangeSymbol("BTC-USD-PERP");
        });

        // Should have been called 4 times (1 original + 3 retries)
        verify(mockApi, times(4)).getInstrumentDescriptor("BTC-USD-PERP");
    }

    @Test
    void testGetAllInstrumentsWithRetry() throws IOException {
        // Setup
        InstrumentDescriptor[] expectedInstruments = new InstrumentDescriptor[1];
        expectedInstruments[0] = mock(InstrumentDescriptor.class);

        when(mockApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES))
                .thenThrow(new IOException("Connection timeout")).thenReturn(expectedInstruments);

        ParadexInstrumentLookup lookup = new ParadexInstrumentLookup(mockApi);

        // Execute
        InstrumentDescriptor[] result = lookup.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);

        // Verify
        assertNotNull(result);
        assertEquals(expectedInstruments, result);
        verify(mockApi, times(2)).getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
    }
}