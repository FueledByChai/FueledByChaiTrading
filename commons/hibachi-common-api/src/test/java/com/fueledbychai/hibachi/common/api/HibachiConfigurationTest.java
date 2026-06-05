package com.fueledbychai.hibachi.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fueledbychai.hibachi.common.api.ws.HibachiTopicRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HibachiConfigurationTest {

    @AfterEach
    void cleanup() {
        // Singleton + system property — restore a clean slate for other tests.
        System.clearProperty(HibachiConfiguration.HIBACHI_MARKET_DATA_LIVE_BOOK);
        HibachiConfiguration.reset();
    }

    @Test
    void marketDataLiveBook_defaultsFalse() {
        System.clearProperty(HibachiConfiguration.HIBACHI_MARKET_DATA_LIVE_BOOK);
        HibachiConfiguration.reset();
        assertFalse(HibachiConfiguration.getInstance().isMarketDataLiveBook(),
                "live_book must be opt-in (default off)");
    }

    @Test
    void marketDataLiveBook_trueWhenPropertySet() {
        System.setProperty(HibachiConfiguration.HIBACHI_MARKET_DATA_LIVE_BOOK, "true");
        HibachiConfiguration.reset();
        assertTrue(HibachiConfiguration.getInstance().isMarketDataLiveBook());
    }

    @Test
    void liveBookTopicHasExpectedWireValue() {
        // Guard the exact channel string the MM-partner feed expects.
        assertEquals("live_book", HibachiTopicRouter.TOPIC_LIVE_BOOK);
    }
}
