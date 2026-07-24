package com.fueledbychai.websocket;

/**
 * Common lifecycle contract for cached exchange WebSocket APIs.
 *
 * <p>Implementations returned by {@code ExchangeWebSocketApiFactory} are
 * process-wide cached instances. Consequently, disconnecting a session must
 * not permanently poison the object: a later connect or subscribe call must
 * be able to create a fresh session, including fresh reconnect workers and
 * subscription state.</p>
 */
public interface ExchangeWebSocketLifecycle {

    /**
     * Closes every active connection and cancels reconnect work for the current
     * session.
     *
     * <p>This operation is idempotent and restartable. It must release callbacks
     * and desired subscriptions owned by the ending session, but it must not make
     * the cached API instance unusable. A subsequent connect or subscribe call
     * starts a new session.</p>
     */
    void disconnectAll();
}
