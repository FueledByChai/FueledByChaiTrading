/**
 * MIT License
 *
 * Copyright (c) 2015  FueledByChai Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.fueledbychai.util;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;

public class DefaultExchangeCapabilities implements ExchangeCapabilities {

    private final Exchange exchange;
    private final boolean supportsStreaming;
    private final boolean supportsBrokerage;
    private final boolean supportsHistoricalData;
    private final Set<InstrumentType> instrumentTypes;

    private DefaultExchangeCapabilities(Builder builder) {
        this.exchange = builder.exchange;
        this.supportsStreaming = builder.supportsStreaming;
        this.supportsBrokerage = builder.supportsBrokerage;
        this.supportsHistoricalData = builder.supportsHistoricalData;
        this.instrumentTypes = builder.instrumentTypes == null || builder.instrumentTypes.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.instrumentTypes));
    }

    @Override
    public Exchange getExchange() {
        return exchange;
    }

    @Override
    public boolean supportsStreaming() {
        return supportsStreaming;
    }

    @Override
    public boolean supportsBrokerage() {
        return supportsBrokerage;
    }

    @Override
    public boolean supportsHistoricalData() {
        return supportsHistoricalData;
    }

    @Override
    public Set<InstrumentType> getInstrumentTypes() {
        return instrumentTypes;
    }

    public static Builder builder(Exchange exchange) {
        return new Builder(exchange);
    }

    public static class Builder {
        private final Exchange exchange;
        private boolean supportsStreaming;
        private boolean supportsBrokerage;
        private boolean supportsHistoricalData;
        private Set<InstrumentType> instrumentTypes;

        public Builder(Exchange exchange) {
            if (exchange == null) {
                throw new IllegalArgumentException("Exchange is required");
            }
            this.exchange = exchange;
            this.instrumentTypes = EnumSet.noneOf(InstrumentType.class);
        }

        public Builder supportsStreaming(boolean supportsStreaming) {
            this.supportsStreaming = supportsStreaming;
            return this;
        }

        public Builder supportsBrokerage(boolean supportsBrokerage) {
            this.supportsBrokerage = supportsBrokerage;
            return this;
        }

        public Builder supportsHistoricalData(boolean supportsHistoricalData) {
            this.supportsHistoricalData = supportsHistoricalData;
            return this;
        }

        public Builder addInstrumentType(InstrumentType instrumentType) {
            if (instrumentType != null) {
                this.instrumentTypes.add(instrumentType);
            }
            return this;
        }

        public Builder addInstrumentTypes(Set<InstrumentType> instrumentTypes) {
            if (instrumentTypes != null) {
                this.instrumentTypes.addAll(instrumentTypes);
            }
            return this;
        }

        public DefaultExchangeCapabilities build() {
            return new DefaultExchangeCapabilities(this);
        }
    }
}
