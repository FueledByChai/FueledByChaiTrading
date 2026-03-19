package com.fueledbychai.binance.ws.bookticker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.websocket.IWebSocketClosedListener;

@ExtendWith(MockitoExtension.class)
public class BookTickerRecordProcessorTest {

    @Mock
    private IWebSocketClosedListener closedListener;

    @Test
    public void testParseMessageWithEnvelope() {
        String json = """
            {"stream":"btcusdt@bookTicker","data":{"u":400900217,"s":"BTCUSDT","b":"41000.10","B":"0.452","a":"41000.20","A":"0.638","E":1768313856798}}
            """;

        BookTickerRecordProcessor processor = new BookTickerRecordProcessor(closedListener);
        BookTickerRecord record = processor.parseMessage(json);

        assertNotNull(record);
        assertEquals(1768313856798L, record.getEventTime());
        assertEquals(400900217L, record.getUpdateId());
        assertEquals("BTCUSDT", record.getSymbol());
        assertEquals("41000.10", record.getBestBidPrice());
        assertEquals("0.452", record.getBestBidQty());
        assertEquals("41000.20", record.getBestAskPrice());
        assertEquals("0.638", record.getBestAskQty());
    }

    @Test
    public void testParseMessageWithoutEnvelope() {
        String json = """
            {"u":123456,"s":"ETHUSDT","b":"2000.1","B":"1.2","a":"2000.2","A":"1.0","E":987654321}
            """;

        BookTickerRecordProcessor processor = new BookTickerRecordProcessor(closedListener);
        BookTickerRecord record = processor.parseMessage(json);

        assertNotNull(record);
        assertEquals(987654321L, record.getEventTime());
        assertEquals("ETHUSDT", record.getSymbol());
    }

    @Test
    public void testParseMessageInvalidJsonReturnsNull() {
        BookTickerRecordProcessor processor = new BookTickerRecordProcessor(closedListener);
        BookTickerRecord record = processor.parseMessage("{invalid json");
        assertNull(record);
    }
}
