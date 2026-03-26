package com.fueledbychai.lighter.common.api.ws.processor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.websocket.IWebSocketClosedListener;
import com.fueledbychai.websocket.IWebSocketProcessor;

/**
 * A multiplexing processor that broadcasts incoming WebSocket messages to
 * multiple child processors. Each child processor parses only messages that
 * match its channel type (returning null for non-matching messages), so
 * broadcasting is safe and efficient.
 *
 * Used in shared-socket mode where a single WebSocket connection carries
 * subscriptions for multiple channels.
 */
public class LighterMultiplexingProcessor implements IWebSocketProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LighterMultiplexingProcessor.class);

    private final List<IWebSocketProcessor> processors = new CopyOnWriteArrayList<>();
    private final IWebSocketClosedListener closedListener;

    public LighterMultiplexingProcessor(IWebSocketClosedListener closedListener) {
        this.closedListener = closedListener;
    }

    public void addProcessor(IWebSocketProcessor processor) {
        if (processor != null && !processors.contains(processor)) {
            processors.add(processor);
        }
    }

    public void removeProcessor(IWebSocketProcessor processor) {
        processors.remove(processor);
    }

    public int getProcessorCount() {
        return processors.size();
    }

    @Override
    public void messageReceived(String message) {
        for (IWebSocketProcessor processor : processors) {
            try {
                processor.messageReceived(message);
            } catch (Exception e) {
                logger.error("Error dispatching message to child processor", e);
            }
        }
    }

    @Override
    public void connectionClosed(int code, String reason, boolean remote) {
        logger.info("Shared WebSocket connection closed: {}", reason);
        if (closedListener != null) {
            closedListener.connectionClosed();
        }
    }

    @Override
    public void connectionError(Exception error) {
        logger.error("Shared WebSocket connection error", error);
        if (closedListener != null) {
            closedListener.connectionClosed();
        }
    }

    @Override
    public void connectionEstablished() {
        // no-op
    }

    @Override
    public void connectionOpened() {
        // no-op
    }
}
