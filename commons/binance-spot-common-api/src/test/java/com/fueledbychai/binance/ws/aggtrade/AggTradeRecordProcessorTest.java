package com.fueledbychai.binance.ws.aggtrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.websocket.IWebSocketClosedListener;

@ExtendWith(MockitoExtension.class)
public class AggTradeRecordProcessorTest {

    @Mock
    private IWebSocketClosedListener closedListener;

    @Test
    public void testParseMessageWithEnvelope() {
        String json = """
            {"stream":"zecusdt@aggTrade","data":{"e":"aggTrade","E":1768313856798,"a":329301872,"s":"ZECUSDT","p":"409.69","q":"0.141","nq":"0.141","f":793703628,"l":793703630,"T":1768313856768,"m":false}}
            """;

        AggTradeRecordProcessor processor = new AggTradeRecordProcessor(closedListener);
        TradeRecord record = processor.parseMessage(json);

        assertNotNull(record);
        assertEquals(1768313856798L, record.getEventTime());
        assertEquals("ZECUSDT", record.getSymbol());
        assertEquals("409.69", record.getPrice());
        assertEquals("0.141", record.getQuantity());
        assertEquals(1768313856768L, record.getTradeTime());
        assertFalse(record.isBuyerMarketMaker());
    }

    @Test
    public void testParseMessageWithoutEnvelope() {
        String json = """
            {"e":"aggTrade","E":12345,"a":329301872,"s":"ZECUSDT","p":"1.23","q":"0.5","T":67890,"m":true}
            """;

        AggTradeRecordProcessor processor = new AggTradeRecordProcessor(closedListener);
        TradeRecord record = processor.parseMessage(json);

        assertNotNull(record);
        assertEquals(12345L, record.getEventTime());
        assertEquals("ZECUSDT", record.getSymbol());
        assertEquals("1.23", record.getPrice());
        assertEquals("0.5", record.getQuantity());
        assertEquals(67890L, record.getTradeTime());
        assertTrue(record.isBuyerMarketMaker());
    }

    @Test
    public void testParseMessageInvalidJsonReturnsNull() {
        AggTradeRecordProcessor processor = new AggTradeRecordProcessor(closedListener);
        TradeRecord record = processor.parseMessage("{invalid json");
        assertNull(record);
    }
}
