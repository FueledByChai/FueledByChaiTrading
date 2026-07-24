package com.fueledbychai.grvt.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.grvt.common.api.model.GrvtInstrument;

class GrvtRestApiInstrumentTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void usesVenueFundingIntervalInInstrumentDescriptor() throws Exception {
        TestableGrvtRestApi api = new TestableGrvtRestApi();
        GrvtInstrument instrument = api.parse("""
                {"instrument":"STRK_USDT_Perp","instrument_hash":"0x034901",
                 "base":"STRK","quote":"USDT","kind":"PERPETUAL",
                 "base_decimals":6,"quote_decimals":6,"tick_size":"0.00001",
                 "min_size":"0.1","funding_interval_hours":4}
                """);

        InstrumentDescriptor descriptor = api.descriptor(instrument);

        assertEquals(4, instrument.getFundingIntervalHours());
        assertEquals(4, descriptor.getFundingPeriodHours());
    }

    private static final class TestableGrvtRestApi extends GrvtRestApi {
        private TestableGrvtRestApi() {
            super(GrvtEnvironment.forName("prod"));
        }

        private GrvtInstrument parse(String json) throws Exception {
            return parseInstrument(OBJECT_MAPPER.readTree(json));
        }

        private InstrumentDescriptor descriptor(GrvtInstrument instrument) {
            return toDescriptor(instrument);
        }
    }
}
