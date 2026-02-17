package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.account.LighterPosition;
import com.fueledbychai.util.ITickerRegistry;
import com.google.gson.JsonObject;

class LighterRestApiPositionsTest {

    @Test
    void parsePositionsResponseMapsFields() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        String response = "{"
                + "\"code\":200,"
                + "\"accounts\":[{"
                + "\"account_index\":255,"
                + "\"positions\":["
                + "{"
                + "\"market_id\":1,"
                + "\"symbol\":\"BTC\","
                + "\"sign\":1,"
                + "\"position\":\"0.015\","
                + "\"avg_entry_price\":\"68900.5\","
                + "\"liquidation_price\":\"54000\""
                + "},"
                + "{"
                + "\"market_id\":2,"
                + "\"symbol\":\"SOL\","
                + "\"sign\":-1,"
                + "\"position\":\"2.500\","
                + "\"avg_entry_price\":\"150.10\","
                + "\"liquidation_price\":\"220.00\""
                + "}"
                + "]"
                + "}]"
                + "}";

        List<LighterPosition> positions = api.parsePositions(response, 255L);

        assertEquals(2, positions.size());

        LighterPosition btc = positions.get(0);
        assertEquals("BTC", btc.getTicker().getSymbol());
        assertEquals("1", btc.getTicker().getId());
        assertBigDecimalEquals("0.015", btc.getSize());
        assertBigDecimalEquals("68900.5", btc.getAverageEntryPrice());
        assertBigDecimalEquals("54000", btc.getLiquidationPrice());
        assertEquals(Side.LONG, btc.getSide());
        assertEquals(LighterPosition.Status.OPEN, btc.getStatus());

        LighterPosition sol = positions.get(1);
        assertEquals("SOL", sol.getTicker().getSymbol());
        assertEquals("2", sol.getTicker().getId());
        assertBigDecimalEquals("2.500", sol.getSize());
        assertEquals(Side.SHORT, sol.getSide());
        assertEquals(LighterPosition.Status.OPEN, sol.getStatus());
    }

    @Test
    void parsePositionsResponseUsesAccountIndexFilter() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        String response = "{"
                + "\"code\":200,"
                + "\"accounts\":["
                + "{"
                + "\"account_index\":1,"
                + "\"positions\":[{\"market_id\":1,\"symbol\":\"BTC\",\"sign\":1,\"position\":\"1\"}]"
                + "},"
                + "{"
                + "\"account_index\":255,"
                + "\"positions\":[{\"market_id\":2,\"symbol\":\"ETH\",\"sign\":1,\"position\":\"3\"}]"
                + "}"
                + "]"
                + "}";

        List<LighterPosition> positions = api.parsePositions(response, 255L);

        assertEquals(1, positions.size());
        assertEquals("ETH", positions.get(0).getTicker().getSymbol());
        assertEquals("2", positions.get(0).getTicker().getId());
    }

    @Test
    void parsePositionsResponseSupportsMissingSignAndClosedPositions() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        String response = "{"
                + "\"code\":200,"
                + "\"accounts\":[{"
                + "\"account_index\":7,"
                + "\"positions\":["
                + "{"
                + "\"market_id\":3,"
                + "\"symbol\":\"ETH\","
                + "\"position\":\"-0.2500\","
                + "\"avg_entry_price\":\"2500\""
                + "},"
                + "{"
                + "\"market_id\":4,"
                + "\"symbol\":\"XRP\","
                + "\"position\":\"0\""
                + "}"
                + "]"
                + "}]"
                + "}";

        List<LighterPosition> positions = api.parsePositions(response, 7L);

        assertEquals(2, positions.size());
        assertBigDecimalEquals("0.2500", positions.get(0).getSize());
        assertEquals(Side.SHORT, positions.get(0).getSide());
        assertEquals(LighterPosition.Status.OPEN, positions.get(0).getStatus());

        assertBigDecimalEquals("0", positions.get(1).getSize());
        assertEquals(Side.LONG, positions.get(1).getSide());
        assertEquals(LighterPosition.Status.CLOSED, positions.get(1).getStatus());
    }

    @Test
    void getPositionsRejectsNegativeAccountIndex() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        assertThrows(IllegalArgumentException.class, () -> api.getPositions(-1L));
    }

    private static void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0,
                "Expected " + expected + " but got " + actual);
    }

    private static class TestableLighterRestApi extends LighterRestApi {

        TestableLighterRestApi() {
            super("https://example.com", true);
        }

        List<LighterPosition> parsePositions(String responseBody, long accountIndex) {
            return parsePositionsResponse(responseBody, accountIndex);
        }

        @Override
        protected ITickerRegistry getTickerRegistry() {
            return null;
        }

        @Override
        protected Ticker resolvePositionTicker(JsonObject positionObject, ITickerRegistry tickerRegistry) {
            String symbol = getString(positionObject, "symbol");
            String instrumentId = getStringFromFields(positionObject, "market_id", "marketId", "instrument_id",
                    "instrumentId", "id");
            Ticker ticker = new Ticker(symbol);
            ticker.setSymbol(symbol);
            ticker.setId(instrumentId);
            ticker.setExchange(Exchange.LIGHTER);
            ticker.setPrimaryExchange(Exchange.LIGHTER);
            ticker.setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
            ticker.setCurrency("USDC");
            return ticker;
        }
    }
}
