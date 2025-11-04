package com.fueledbychai.binance;

import com.fueledbychai.binance.model.BinanceInstrumentDescriptorResult;
import com.fueledbychai.data.InstrumentType;

public class BinanceClientTest {

    public static void main(String[] args) throws Exception {
        BinanceRestApi api = BinanceRestApi.getPublicOnlyApi("https://api.binance.com/api/v3");
        BinanceInstrumentDescriptorResult result = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
        System.out.println("Retrieved " + result.getSymbols().size() + " instruments.");

    }
}