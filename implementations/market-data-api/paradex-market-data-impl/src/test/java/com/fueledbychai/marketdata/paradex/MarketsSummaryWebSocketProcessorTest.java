package com.fueledbychai.marketdata.paradex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class MarketsSummaryWebSocketProcessorTest {

    @Test
    void messageReceivedSpotSummaryWithEmptyFundingRateNotifiesListener() throws Exception {
        AtomicReference<String> fundingRate = new AtomicReference<>();
        AtomicReference<String> symbol = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        MarketsSummaryWebSocketProcessor processor = new MarketsSummaryWebSocketProcessor(() -> {
        });
        try {
            processor.addMarketsSummaryUpdateListener((createdAt, marketSymbol, bid, ask, last, markPrice,
                    openInterest, volume24h, underlyingPrice, fundingRateValue) -> {
                symbol.set(marketSymbol);
                fundingRate.set(fundingRateValue);
                latch.countDown();
            });

            String message = """
                    {
                      "jsonrpc": "2.0",
                      "method": "subscription",
                      "params": {
                        "channel": "markets_summary.ETH-USD",
                        "data": {
                          "symbol": "ETH-USD",
                          "mark_price": "1994.62",
                          "last_traded_price": "1991.76",
                          "bid": "1992.9",
                          "ask": "1994.45",
                          "volume_24h": "329825.6947310004",
                          "created_at": 1771375190278,
                          "underlying_price": "1994.62",
                          "open_interest": "0",
                          "funding_rate": ""
                        }
                      }
                    }
                    """;

            processor.messageReceived(message);
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Expected summary listener notification");
            assertEquals("ETH-USD", symbol.get());
            assertEquals("", fundingRate.get());
        } finally {
            processor.shutdownNow();
        }
    }

    @Test
    void messageReceivedSpotSummaryWithoutFundingRateUsesEmptyDefault() throws Exception {
        AtomicReference<String> fundingRate = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        MarketsSummaryWebSocketProcessor processor = new MarketsSummaryWebSocketProcessor(() -> {
        });
        try {
            processor.addMarketsSummaryUpdateListener((createdAt, marketSymbol, bid, ask, last, markPrice,
                    openInterest, volume24h, underlyingPrice, fundingRateValue) -> {
                fundingRate.set(fundingRateValue);
                latch.countDown();
            });

            String message = """
                    {
                      "jsonrpc": "2.0",
                      "method": "subscription",
                      "params": {
                        "channel": "markets_summary.ETH-USD",
                        "data": {
                          "symbol": "ETH-USD",
                          "mark_price": "1994.62",
                          "last_traded_price": "1991.76",
                          "bid": "1992.9",
                          "ask": "1994.45",
                          "volume_24h": "329825.6947310004",
                          "created_at": 1771375190278,
                          "underlying_price": "1994.62",
                          "open_interest": "0"
                        }
                      }
                    }
                    """;

            processor.messageReceived(message);
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Expected summary listener notification");
            assertEquals("", fundingRate.get());
        } finally {
            processor.shutdownNow();
        }
    }
}
