package com.fueledbychai.extended.common.api.ws;

import com.fueledbychai.websocket.BaseCryptoWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

/**
 * WebSocket client for the Extended (extended.exchange) streaming API.
 *
 * <p>
 * Extended streams use <em>URL-path-based subscription</em>: the channel is
 * encoded in the connection URL (e.g. {@code .../orderbooks/BTC-USD}), so there
 * is no subscribe or auth message to send after connecting. The private
 * {@code /account} stream is authorized with an {@code X-Api-Key} header on the
 * handshake.
 * </p>
 */
public class ExtendedWebSocketClient extends BaseCryptoWebSocketClient {

    /**
     * @param serverUri full stream URL including the channel path (and query)
     * @param channel   logical channel label (for diagnostics/logging)
     * @param processor message processor
     * @param apiKey    API key for the {@code X-Api-Key} header, or {@code null}
     *                  for public streams
     */
    public ExtendedWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor, String apiKey)
            throws Exception {
        super(serverUri, channel, processor);
        if (apiKey != null && !apiKey.isBlank()) {
            addHeader("X-Api-Key", apiKey);
        }
    }

    public ExtendedWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        this(serverUri, channel, processor, null);
    }

    @Override
    protected String getProviderName() {
        return "Extended";
    }

    // No auth/subscribe messages: Extended subscribes via the URL path.
}
