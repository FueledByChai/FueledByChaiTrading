package com.fueledbychai.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fueledbychai.binance.model.BinanceInstrumentDescriptorResult;
import com.fueledbychai.binance.model.BinanceSymbol;
import com.fueledbychai.binance.model.BinanceSymbol.LotSizeFilterInfo;
import com.fueledbychai.binance.model.BinanceSymbol.PriceFilterInfo;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.ExchangeRestApiFactory;

class BinanceInstrumentLookupTest {

    @Test
    void constructorRejectsNullApi() {
        assertThrows(IllegalArgumentException.class, () -> new BinanceInstrumentLookup(null));
    }

    @Test
    void getAllInstrumentsForTypeRejectsUnsupportedType() {
        BinanceInstrumentLookup lookup = new BinanceInstrumentLookup(mock(IBinanceRestApi.class));

        assertThrows(IllegalArgumentException.class,
                () -> lookup.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES));
    }

    @Test
    void getAllInstrumentsForTypeConvertsTradingSymbols() {
        IBinanceRestApi api = mock(IBinanceRestApi.class);
        BinanceInstrumentDescriptorResult result = mock(BinanceInstrumentDescriptorResult.class);
        BinanceSymbol symbol = mock(BinanceSymbol.class);
        PriceFilterInfo priceFilter = mock(PriceFilterInfo.class);
        LotSizeFilterInfo lotSizeFilter = mock(LotSizeFilterInfo.class);

        when(api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT)).thenReturn(result);
        when(result.getTradingSymbols()).thenReturn(List.of(symbol));

        when(symbol.getSymbol()).thenReturn("BTCUSDT");
        when(symbol.getBaseAsset()).thenReturn("BTC");
        when(symbol.getQuoteAsset()).thenReturn("USDT");
        when(symbol.getPriceFilter()).thenReturn(priceFilter);
        when(symbol.getLotSizeFilter()).thenReturn(lotSizeFilter);

        when(priceFilter.getTickSize()).thenReturn("0.01");
        when(lotSizeFilter.getStepSize()).thenReturn("0.001");
        when(lotSizeFilter.getMinQty()).thenReturn("0.01");

        BinanceInstrumentLookup lookup = new BinanceInstrumentLookup(api);
        InstrumentDescriptor[] descriptors = lookup.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);

        assertEquals(1, descriptors.length);
        InstrumentDescriptor descriptor = descriptors[0];
        assertEquals(Exchange.BINANCE_SPOT, descriptor.getExchange());
        assertEquals("BTCUSDT", descriptor.getExchangeSymbol());
        assertEquals("BTC", descriptor.getBaseCurrency());
        assertEquals("USDT", descriptor.getQuoteCurrency());
        assertEquals(new BigDecimal("0.001"), descriptor.getOrderSizeIncrement());
        assertEquals(new BigDecimal("0.01"), descriptor.getPriceTickSize());
        assertEquals(new BigDecimal("0.01"), descriptor.getMinOrderSize());
    }

    @Test
    void defaultConstructorUsesExchangeRestApiFactory() {
        IBinanceRestApi api = mock(IBinanceRestApi.class);
        BinanceInstrumentDescriptorResult result = mock(BinanceInstrumentDescriptorResult.class);

        try (MockedStatic<ExchangeRestApiFactory> mockedFactory = Mockito.mockStatic(ExchangeRestApiFactory.class)) {
            mockedFactory.when(() -> ExchangeRestApiFactory.getApi(Exchange.BINANCE_SPOT, IBinanceRestApi.class))
                    .thenReturn(api);
            when(api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT)).thenReturn(result);
            when(result.getTradingSymbols()).thenReturn(List.of());

            BinanceInstrumentLookup lookup = new BinanceInstrumentLookup();
            InstrumentDescriptor[] descriptors = lookup.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);

            assertEquals(0, descriptors.length);
            mockedFactory.verify(() -> ExchangeRestApiFactory.getApi(Exchange.BINANCE_SPOT, IBinanceRestApi.class));
            verify(api).getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);
        }
    }
}
