package com.fueledbychai.binance.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketProcessor;

@ExtendWith(MockitoExtension.class)
public class BinanceWebSocketClientBuilderTest {

    @Mock
    private IWebSocketProcessor processor;

    @Test
    public void testBuildPartialBookDepth() throws Exception {
        Ticker ticker = new Ticker("ZECUSDT");
        BinanceWebSocketClient client = BinanceWebSocketClientBuilder
                .buildPartialBookDepth("wss://example.test/stream", ticker, processor);

        assertNotNull(client);
        assertEquals("wss://example.test/stream", client.getURI().toString());
        assertEquals("zecusdt@depth5@100ms", getFieldValue(client, "channel", String.class));
        assertSame(processor, getFieldValue(client, "processor", IWebSocketProcessor.class));
    }

    @Test
    public void testBuildTradesClient() throws Exception {
        Ticker ticker = new Ticker("BTCUSDT");
        BinanceWebSocketClient client = BinanceWebSocketClientBuilder
                .buildTradesClient("wss://example.test/stream", ticker, processor);

        assertNotNull(client);
        assertEquals("wss://example.test/stream", client.getURI().toString());
        assertEquals("btcusdt@aggTrade", getFieldValue(client, "channel", String.class));
        assertSame(processor, getFieldValue(client, "processor", IWebSocketProcessor.class));
    }

    private static <T> T getFieldValue(Object target, String fieldName, Class<T> type) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return type.cast(field.get(target));
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Unable to access field: " + fieldName, ex);
            }
        }
        throw new IllegalStateException("Field not found: " + fieldName);
    }
}
