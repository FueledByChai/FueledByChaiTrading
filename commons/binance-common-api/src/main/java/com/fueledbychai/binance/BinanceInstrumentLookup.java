package com.fueledbychai.binance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fueledbychai.binance.model.BinanceInstrumentDescriptorResult;
import com.fueledbychai.binance.model.BinanceSymbol;
import com.fueledbychai.binance.model.BinanceSymbol.LotSizeFilterInfo;
import com.fueledbychai.binance.model.BinanceSymbol.PriceFilterInfo;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.IInstrumentLookup;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ExchangeRestApiFactory;

public class BinanceInstrumentLookup implements IInstrumentLookup {

    protected final IBinanceRestApi api;

    public BinanceInstrumentLookup() {
        this(ExchangeRestApiFactory.getApi(Exchange.BINANCE_SPOT, IBinanceRestApi.class));
    }

    public BinanceInstrumentLookup(IBinanceRestApi api) {
        if (api == null) {
            throw new IllegalArgumentException("api is required");
        }
        this.api = api;
    }

    @Override
    public InstrumentDescriptor lookupByCommonSymbol(String commonSymbol) {
        return lookupByExchangeSymbol(commonSymbol);
    }

    @Override
    public InstrumentDescriptor lookupByExchangeSymbol(String exchangeSymbol) {
        // return api.getInstrumentDescriptor(exchangeSymbol);
        return null;
    }

    @Override
    public InstrumentDescriptor lookupByTicker(Ticker ticker) {
        return lookupByExchangeSymbol(ticker.getSymbol());
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.CRYPTO_SPOT) {
            throw new IllegalArgumentException("Only crypto spot is supported at this time.");
        }

        BinanceInstrumentDescriptorResult result = api.getAllInstrumentsForType(instrumentType);
        return convertBinanceResultsToInstrumentDescriptors(result);
    }

    protected InstrumentDescriptor[] convertBinanceResultsToInstrumentDescriptors(
            BinanceInstrumentDescriptorResult result) {
        List<BinanceSymbol> tradingSymbols = result.getTradingSymbols();
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        for (BinanceSymbol symbol : tradingSymbols) {

            InstrumentDescriptor descriptor = convertBinanceSymbolToInstrumentDescriptor(symbol);
            descriptors.add(descriptor);
        }
        return descriptors.toArray(new InstrumentDescriptor[0]);
    }

    protected InstrumentDescriptor convertBinanceSymbolToInstrumentDescriptor(BinanceSymbol symbol) {
        PriceFilterInfo priceFilter = symbol.getPriceFilter();
        LotSizeFilterInfo lotSizeFilter = symbol.getLotSizeFilter();
        // get tick size and strip trailing zeros
        BigDecimal priceTickSize = new BigDecimal(priceFilter.getTickSize()).stripTrailingZeros();
        BigDecimal orderSizeIncrement = new BigDecimal(lotSizeFilter.getStepSize()).stripTrailingZeros();
        BigDecimal minOrderSize = new BigDecimal(lotSizeFilter.getMinQty()).stripTrailingZeros();

        InstrumentDescriptor descriptor = new InstrumentDescriptor(InstrumentType.CRYPTO_SPOT, Exchange.BINANCE_SPOT,
                symbol.getSymbol(), symbol.getSymbol(), symbol.getBaseAsset(), symbol.getQuoteAsset(),
                orderSizeIncrement, priceTickSize, 0, minOrderSize, 0, BigDecimal.ZERO, 0, "");

        return descriptor;
    }
}
