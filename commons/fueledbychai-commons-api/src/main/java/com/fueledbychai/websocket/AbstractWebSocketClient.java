package com.fueledbychai.websocket;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.fueledbychai.diagnostics.SecretRedactor;
import com.fueledbychai.diagnostics.WireTap;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWebSocketClient extends WebSocketClient {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractWebSocketClient.class);
    protected IWebSocketProcessor processor;
    protected List<String> messages = new ArrayList<>();
    protected String channel;
    private final String serverUriString;

    // --- WS disconnect diagnostics (debug the reconnect churn, e.g. HYPE-Hibachi
    // 2026-06-29). onClose logs WHAT (code/remote/idle age); close()/closeBlocking()
    // overrides log WHO initiated a client-side close, with the caller stack — the
    // piece the plain "onClose code=1000 remote=false" line was missing. ---
    private final long wsCreatedAtMs = System.currentTimeMillis();
    private volatile long wsLastRecvMs = 0L;
    private volatile long wsLastSendMs = 0L;

    public AbstractWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        super(new URI(serverUri));
        setProxy(ProxyConfig.getInstance().getProxy());
        this.processor = processor;
        this.channel = channel;
        this.serverUriString = serverUri;
    }

    @Override
    public void onMessage(String message) {
        wsLastRecvMs = System.currentTimeMillis();
        if (WireTap.isEnabled()) {
            WireTap.publishWs(new WireTap.WsEvent(
                    System.currentTimeMillis(),
                    WireTap.Direction.IN,
                    exchangeFromHost(),
                    channel,
                    serverUriString,
                    SecretRedactor.redactBody(message)));
        }
        processor.messageReceived(message);
    }

    @Override
    public void send(String text) {
        wsLastSendMs = System.currentTimeMillis();
        if (WireTap.isEnabled()) {
            WireTap.publishWs(new WireTap.WsEvent(
                    System.currentTimeMillis(),
                    WireTap.Direction.OUT,
                    exchangeFromHost(),
                    channel,
                    serverUriString,
                    SecretRedactor.redactBody(text)));
        }
        super.send(text);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        long now = System.currentTimeMillis();
        // remote=true => server/peer closed us; remote=false => closed locally (our
        // close() call or the WS library). connAge + idle gaps distinguish a fresh
        // connect-fail loop, an idle/timeout close, and a deliberate teardown.
        logger.info("WS DIAG onClose ex={} ch={} code={} remote={} reason='{}' connAgeMs={} msSinceRecv={} msSinceSend={}",
                exchangeFromHost(), channel, code, remote, reason,
                now - wsCreatedAtMs,
                wsLastRecvMs == 0L ? -1L : now - wsLastRecvMs,
                wsLastSendMs == 0L ? -1L : now - wsLastSendMs);
        processor.connectionClosed(code, reason, remote);
    }

    @Override
    public void close() {
        logger.info("WS DIAG close() ex={} ch={} connAgeMs={} — initiated by:\n{}",
                exchangeFromHost(), channel, System.currentTimeMillis() - wsCreatedAtMs, wsCallerStack());
        super.close();
    }

    @Override
    public void closeBlocking() throws InterruptedException {
        logger.info("WS DIAG closeBlocking() ex={} ch={} connAgeMs={} — initiated by:\n{}",
                exchangeFromHost(), channel, System.currentTimeMillis() - wsCreatedAtMs, wsCallerStack());
        super.closeBlocking();
    }

    /** Compact caller stack (skips the diagnostic frames) so we can see what triggered a client-side WS close. */
    private static String wsCallerStack() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 3; i < st.length && shown < 12; i++, shown++) {
            sb.append("    at ").append(st[i]).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void onError(Exception ex) {
        logger.error(ex.getMessage(), ex);
    }

    private String exchangeFromHost() {
        try {
            String host = getURI() != null && getURI().getHost() != null ? getURI().getHost().toLowerCase() : "";
            if (host.contains("hyperliquid")) return "HYPERLIQUID";
            if (host.contains("paradex")) return "PARADEX";
            if (host.contains("lighter")) return "LIGHTER";
            if (host.contains("hibachi")) return "HIBACHI";
            if (host.contains("aster")) return "ASTER";
            if (host.contains("bybit")) return "BYBIT";
            if (host.contains("okx") || host.contains("okex")) return "OKX";
            if (host.contains("drift")) return "DRIFT";
            if (host.contains("deribit")) return "DERIBIT";
            if (host.contains("binance")) return "BINANCE";
            if (host.contains("dydx")) return "DYDX";
            return host.isEmpty() ? "unknown" : host;
        } catch (Exception e) {
            return "unknown";
        }
    }

}
