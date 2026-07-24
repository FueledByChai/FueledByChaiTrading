package com.fueledbychai.extended.common;

import com.fueledbychai.data.ITickerTranslator;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.data.TickerTranslator;

/**
 * Translates Extended {@link InstrumentDescriptor}s into {@link Ticker}s.
 * Extended currently exposes only perpetual futures, so this simply delegates to
 * the default {@link TickerTranslator}.
 */
public class ExtendedTickerTranslator implements ITickerTranslator {

    protected final TickerTranslator delegate = new TickerTranslator();

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        return delegate.translateTicker(descriptor);
    }
}
