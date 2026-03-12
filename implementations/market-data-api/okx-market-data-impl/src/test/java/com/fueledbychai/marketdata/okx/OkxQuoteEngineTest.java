package com.fueledbychai.marketdata.okx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.okx.common.api.IOkxRestApi;
import com.fueledbychai.okx.common.api.IOkxWebSocketApi;
import com.fueledbychai.okx.common.api.ws.model.OkxFundingRateUpdate;
import com.fueledbychai.util.ITickerRegistry;

@ExtendWith(MockitoExtension.class)
class OkxQuoteEngineTest {

    @Mock
    private IOkxRestApi restApi;

    @Mock
    private IOkxWebSocketApi webSocketApi;

    @Mock
    private ITickerRegistry tickerRegistry;

    @Mock
    private Level1QuoteListener level1Listener;

    @Test
    void subscribeLevel1ForPerpetualsStartsTickerAndFundingRateStreamsOnce() {
        CapturingOkxQuoteEngine engine = new CapturingOkxQuoteEngine(restApi, webSocketApi, tickerRegistry);
        Ticker inputTicker = new Ticker("BTC-USDT-SWAP").setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        Ticker canonicalTicker = ticker("BTC-USDT-SWAP", InstrumentType.PERPETUAL_FUTURES)
                .setFundingRateInterval(8);

        when(tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC-USDT-SWAP"))
                .thenReturn(canonicalTicker);

        engine.subscribeLevel1(inputTicker, level1Listener);
        engine.subscribeLevel1(inputTicker, quote -> {
        });

        verify(webSocketApi, times(1)).subscribeTicker(eq("BTC-USDT-SWAP"), any());
        verify(webSocketApi, times(1)).subscribeFundingRate(eq("BTC-USDT-SWAP"), any());
        verify(restApi, times(1)).getFundingRate("BTC-USDT-SWAP");
        assertEquals(8, inputTicker.getFundingRateInterval());
        assertEquals(new BigDecimal("0.1"), inputTicker.getMinimumTickSize());
    }

    @Test
    void fundingRateUpdatesProduceLevel1FundingQuotes() {
        CapturingOkxQuoteEngine engine = new CapturingOkxQuoteEngine(restApi, webSocketApi, tickerRegistry);
        Ticker inputTicker = new Ticker("BTC-USDT-SWAP").setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        Ticker canonicalTicker = ticker("BTC-USDT-SWAP", InstrumentType.PERPETUAL_FUTURES)
                .setFundingRateInterval(8);

        when(tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC-USDT-SWAP"))
                .thenReturn(canonicalTicker);

        engine.subscribeLevel1(inputTicker, level1Listener);
        engine.handleFundingRateUpdate(new OkxFundingRateUpdate("BTC-USDT-SWAP", "SWAP", 1710000000000L,
                new BigDecimal("0.0008"), new BigDecimal("0.0010"), 1710000000000L, 1710028800000L));

        ILevel1Quote quote = engine.lastLevel1Quote;
        assertNotNull(quote);
        assertEquals(inputTicker, quote.getTicker());
        assertEquals(1.0d, quote.getValue(QuoteType.FUNDING_RATE_HOURLY_BPS).doubleValue(), 1.0e-9);
        assertEquals(87.6d, quote.getValue(QuoteType.FUNDING_RATE_APR).doubleValue(), 1.0e-9);
    }

    @Test
    void subscribeLevel1SeedsFundingRateFromRestSnapshot() {
        CapturingOkxQuoteEngine engine = new CapturingOkxQuoteEngine(restApi, webSocketApi, tickerRegistry);
        Ticker inputTicker = new Ticker("BTC-USDT-SWAP").setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        Ticker canonicalTicker = ticker("BTC-USDT-SWAP", InstrumentType.PERPETUAL_FUTURES)
                .setFundingRateInterval(8);

        when(tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC-USDT-SWAP"))
                .thenReturn(canonicalTicker);
        when(restApi.getFundingRate("BTC-USDT-SWAP")).thenReturn(new OkxFundingRateUpdate("BTC-USDT-SWAP", "SWAP",
                1710000000000L, new BigDecimal("0.0008"), new BigDecimal("0.0010"), 1710000000000L,
                1710028800000L));

        engine.subscribeLevel1(inputTicker, level1Listener);

        ILevel1Quote quote = engine.lastLevel1Quote;
        assertNotNull(quote);
        assertEquals(inputTicker, quote.getTicker());
        assertEquals(1.0d, quote.getValue(QuoteType.FUNDING_RATE_HOURLY_BPS).doubleValue(), 1.0e-9);
        assertEquals(87.6d, quote.getValue(QuoteType.FUNDING_RATE_APR).doubleValue(), 1.0e-9);
    }

    private static Ticker ticker(String symbol, InstrumentType instrumentType) {
        return new Ticker(symbol)
                .setExchange(Exchange.OKX)
                .setPrimaryExchange(Exchange.OKX)
                .setInstrumentType(instrumentType)
                .setMinimumTickSize(new BigDecimal("0.1"))
                .setOrderSizeIncrement(new BigDecimal("0.001"));
    }

    private static final class CapturingOkxQuoteEngine extends OkxQuoteEngine {
        private ILevel1Quote lastLevel1Quote;

        private CapturingOkxQuoteEngine(IOkxRestApi restApi, IOkxWebSocketApi webSocketApi,
                ITickerRegistry tickerRegistry) {
            super(restApi, webSocketApi, tickerRegistry);
        }

        @Override
        public void fireLevel1Quote(ILevel1Quote quote) {
            this.lastLevel1Quote = quote;
        }
    }
}
