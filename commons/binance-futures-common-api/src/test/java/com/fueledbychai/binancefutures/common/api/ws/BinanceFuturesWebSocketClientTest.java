package com.fueledbychai.binancefutures.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import com.fueledbychai.websocket.IWebSocketProcessor;

class BinanceFuturesWebSocketClientTest {

    @Test
    void decodesPlainBinaryPayloadAsUtf8() throws Exception {
        BinanceFuturesWebSocketClient client = new BinanceFuturesWebSocketClient(
                "wss://fstream.binance.com/public/ws", "btc-260303-69000-c@bookTicker", noOpProcessor());

        String message = client.decodeBinaryMessage(ByteBuffer.wrap("{\"e\":\"ticker\"}".getBytes()));

        assertEquals("{\"e\":\"ticker\"}", message);
    }

    @Test
    void decodesGzipBinaryPayload() throws Exception {
        BinanceFuturesWebSocketClient client = new BinanceFuturesWebSocketClient(
                "wss://fstream.binance.com/public/ws", "btc-260303-69000-c@bookTicker", noOpProcessor());
        byte[] compressed = gzip("{\"e\":\"depth\"}");

        String message = client.decodeBinaryMessage(ByteBuffer.wrap(compressed));

        assertEquals("{\"e\":\"depth\"}", message);
    }

    private static byte[] gzip(String value) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(value.getBytes());
            gzipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private static IWebSocketProcessor noOpProcessor() {
        return new IWebSocketProcessor() {
            @Override
            public void messageReceived(String message) {
            }

            @Override
            public void connectionClosed(int code, String reason, boolean remote) {
            }

            @Override
            public void connectionOpened() {
            }

            @Override
            public void connectionError(Exception error) {
            }

            @Override
            public void connectionEstablished() {
            }
        };
    }
}
