package com.fueledbychai.binance;

import com.fueledbychai.binance.model.BinanceInstrumentDescriptorResult;
import com.fueledbychai.data.InstrumentType;

public interface IBinanceRestApi {

    BinanceInstrumentDescriptorResult getAllInstrumentsForType(InstrumentType instrumentType);

}