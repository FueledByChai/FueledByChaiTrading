package com.fueledbychai.lighter.common.api;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

public interface ILighterRestApi {

    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    public InstrumentDescriptor getInstrumentDescriptor(String symbol);

    
}
