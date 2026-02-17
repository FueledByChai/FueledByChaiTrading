package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

class LighterRestApiInstrumentIdTest {

    @Test
    void parseInstrumentDescriptorReadsMarketIdIntoInstrumentId() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        String response = "{"
                + "\"results\":[{"
                + "\"asset_kind\":\"PERP\","
                + "\"symbol\":\"BTC\","
                + "\"base_currency\":\"BTC\","
                + "\"quote_currency\":\"USDC\","
                + "\"price_tick_size\":\"0.1\","
                + "\"order_size_increment\":\"0.001\","
                + "\"min_notional\":10,"
                + "\"funding_period_hours\":8,"
                + "\"market_id\":53"
                + "}]"
                + "}";

        InstrumentDescriptor descriptor = api.parseSingleDescriptor(response);

        assertEquals("53", descriptor.getInstrumentId());
    }

    @Test
    void parseOrderBookDetailsReadsMarketIdIntoInstrumentId() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        String response = "{"
                + "\"order_book_details\":[{"
                + "\"status\":\"active\","
                + "\"market_type\":\"perp\","
                + "\"symbol\":\"BTC\","
                + "\"size_decimals\":3,"
                + "\"price_decimals\":1,"
                + "\"min_base_amount\":\"0.001\","
                + "\"min_quote_amount\":\"10\","
                + "\"default_initial_margin_fraction\":500,"
                + "\"quote_multiplier\":\"1\","
                + "\"market_id\":1073"
                + "}]"
                + "}";

        InstrumentDescriptor[] descriptors = api.parseDescriptors(InstrumentType.PERPETUAL_FUTURES, response);

        assertEquals(1, descriptors.length);
        assertEquals("1073", descriptors[0].getInstrumentId());
    }

    private static class TestableLighterRestApi extends LighterRestApi {

        TestableLighterRestApi() {
            super("https://example.com", true);
        }

        InstrumentDescriptor parseSingleDescriptor(String responseBody) {
            return parseInstrumentDescriptor(responseBody);
        }

        InstrumentDescriptor[] parseDescriptors(InstrumentType instrumentType, String responseBody) {
            return parseInstrumentDescriptors(instrumentType, responseBody);
        }
    }
}
