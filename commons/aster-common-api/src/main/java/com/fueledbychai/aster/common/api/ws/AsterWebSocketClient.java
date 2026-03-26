package com.fueledbychai.aster.common.api.ws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import org.java_websocket.handshake.ServerHandshake;

import com.fueledbychai.websocket.AbstractWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class AsterWebSocketClient extends AbstractWebSocketClient {

    private static int idCounter = 1;

    public AsterWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        super(serverUri, channel, processor);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        processor.connectionOpened();
        if (channel != null && !channel.isBlank()) {
            sendCommand("SUBSCRIBE", channel);
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        if (processor == null) {
            return;
        }
        try {
            String message = decodeBinaryMessage(bytes);
            if (message != null && !message.isBlank()) {
                processor.messageReceived(message);
            }
        } catch (Exception e) {
            logger.warn("Failed to decode Aster binary websocket payload", e);
        }
    }

    @Override
    public void onError(Exception ex) {
        super.onError(ex);
        if (processor != null) {
            processor.connectionError(ex);
        }
    }

    public void sendCommand(String method, String param) {
        JsonObject commandJson = new JsonObject();
        commandJson.addProperty("method", method);
        JsonArray paramsArray = new JsonArray();
        paramsArray.add(param);
        commandJson.add("params", paramsArray);
        commandJson.addProperty("id", idCounter++);
        send(commandJson.toString());
    }

    public void sendCommand(String method, java.util.Collection<String> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        JsonObject commandJson = new JsonObject();
        commandJson.addProperty("method", method);
        JsonArray paramsArray = new JsonArray();
        for (String param : params) {
            paramsArray.add(param);
        }
        commandJson.add("params", paramsArray);
        commandJson.addProperty("id", idCounter++);
        send(commandJson.toString());
    }

    String decodeBinaryMessage(ByteBuffer bytes) throws Exception {
        if (bytes == null) {
            return null;
        }
        ByteBuffer copy = bytes.asReadOnlyBuffer();
        byte[] payload = new byte[copy.remaining()];
        copy.get(payload);
        if (payload.length == 0) {
            return null;
        }
        if (isGzip(payload)) {
            return gunzip(payload);
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    boolean isGzip(byte[] payload) {
        return payload != null && payload.length >= 2 && (payload[0] & 0xFF) == 0x1F && (payload[1] & 0xFF) == 0x8B;
    }

    String gunzip(byte[] payload) throws Exception {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(payload));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = gzipInputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
