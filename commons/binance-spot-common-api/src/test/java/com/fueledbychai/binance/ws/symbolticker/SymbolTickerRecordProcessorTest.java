package com.fueledbychai.binance.ws.symbolticker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.websocket.IWebSocketClosedListener;

@ExtendWith(MockitoExtension.class)
public class SymbolTickerRecordProcessorTest {

    @Mock
    private IWebSocketClosedListener closedListener;

    @Test
    public void testParseMessageWithEnvelope() {
        String json = """
            {"stream":"btcusdt@ticker","data":{"E":1768313856798,"s":"BTCUSDT","c":"41000.10","Q":"0.452","v":"145.25","q":"5962287.12"}}
            """;

        SymbolTickerRecordProcessor processor = new SymbolTickerRecordProcessor(closedListener);
        SymbolTickerRecord record = processor.parseMessage(json);

        assertNotNull(record);
        assertEquals(1768313856798L, record.getEventTime());
        assertEquals("BTCUSDT", record.getSymbol());
        assertEquals("41000.10", record.getLastPrice());
        assertEquals("0.452", record.getLastQuantity());
        assertEquals("145.25", record.getVolume());
        assertEquals("5962287.12", record.getVolumeNotional());
    }

    @Test
    public void testParseMessageWithoutEnvelope() {
        String json = """
            {"E":987654321,"s":"ETHUSDT","c":"2000.1","Q":"0.25","v":"500.0","q":"1000050.0"}
            """;

        SymbolTickerRecordProcessor processor = new SymbolTickerRecordProcessor(closedListener);
        SymbolTickerRecord record = processor.parseMessage(json);

        assertNotNull(record);
        assertEquals(987654321L, record.getEventTime());
        assertEquals("ETHUSDT", record.getSymbol());
        assertEquals("2000.1", record.getLastPrice());
        assertEquals("0.25", record.getLastQuantity());
        assertEquals("500.0", record.getVolume());
        assertEquals("1000050.0", record.getVolumeNotional());
    }

    @Test
    public void testParseMessageInvalidJsonReturnsNull() {
        SymbolTickerRecordProcessor processor = new SymbolTickerRecordProcessor(closedListener);
        SymbolTickerRecord record = processor.parseMessage("{invalid json");
        assertNull(record);
    }
}
