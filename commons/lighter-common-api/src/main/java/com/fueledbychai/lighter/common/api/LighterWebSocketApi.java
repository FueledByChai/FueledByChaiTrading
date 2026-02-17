package com.fueledbychai.lighter.common.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.lighter.common.api.ws.listener.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterOrderBookListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterAccountAllTradesListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterAccountOrdersListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterAccountStatsListener;
import com.fueledbychai.lighter.common.api.ws.model.LighterSendTxResponse;
import com.fueledbychai.lighter.common.api.ws.processor.LighterSendTxWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterTradeListener;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.signer.ILighterTransactionSigner;
import com.fueledbychai.lighter.common.api.signer.LighterNativeTransactionSigner;
import com.fueledbychai.lighter.common.api.signer.LighterSignedTransaction;
import com.fueledbychai.lighter.common.api.ws.processor.LighterAccountAllTradesWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.processor.LighterAccountOrdersWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.processor.LighterAccountStatsWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.processor.LighterMarketStatsWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.processor.LighterOrderBookWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.processor.LighterTradesWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.client.LighterWSClientBuilder;
import com.fueledbychai.lighter.common.api.ws.client.LighterWebSocketClient;
import com.fueledbychai.websocket.IWebSocketEventListener;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWebSocketApi
        implements ILighterWebSocketApi, IWebSocketEventListener<LighterSendTxResponse> {

    private static final Logger logger = LoggerFactory.getLogger(LighterWebSocketApi.class);
    private static final long DEFAULT_RECONNECT_DELAY_MILLIS = 2_000L;
    private static final long DEFAULT_SEND_TX_TIMEOUT_MILLIS = 5_000L;
    private static final long DEFAULT_TX_CONNECT_TIMEOUT_MILLIS = 5_000L;
    private static final long DEFAULT_WS_AUTH_TOKEN_TTL_SECONDS = 600L;
    private static final int DEFAULT_MAX_CONNECT_ATTEMPTS_PER_MINUTE = 50;
    private static final long DEFAULT_CONNECT_ATTEMPT_WINDOW_MILLIS = 60_000L;

    protected enum ChannelType {
        MARKET_STATS, ORDER_BOOK, TRADE, ACCOUNT_ALL_TRADES, ACCOUNT_ORDERS, ACCOUNT_STATS
    }

    private final String webSocketUrl;
    private final Map<String, LighterWebSocketClient> channelClients = new ConcurrentHashMap<>();
    private final Map<String, LighterMarketStatsWebSocketProcessor> marketStatsProcessors = new ConcurrentHashMap<>();
    private final Map<String, LighterOrderBookWebSocketProcessor> orderBookProcessors = new ConcurrentHashMap<>();
    private final Map<String, LighterTradesWebSocketProcessor> tradeProcessors = new ConcurrentHashMap<>();
    private final Map<String, LighterAccountAllTradesWebSocketProcessor> accountAllTradesProcessors = new ConcurrentHashMap<>();
    private final Map<String, LighterAccountOrdersWebSocketProcessor> accountOrdersProcessors = new ConcurrentHashMap<>();
    private final Map<String, LighterAccountStatsWebSocketProcessor> accountStatsProcessors = new ConcurrentHashMap<>();
    private final Map<String, String> channelSubscribeAuth = new ConcurrentHashMap<>();
    private final Map<String, Long> accountAllTradesChannelAccountIndex = new ConcurrentHashMap<>();
    private final Map<String, Long> accountOrdersChannelAccountIndex = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> reconnectTasks = new ConcurrentHashMap<>();
    private final Set<String> manualCloseChannels = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedDeque<Long> recentConnectAttemptTimestamps = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, CompletableFuture<LighterSendTxResponse>> pendingTxResponses = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> pendingTxRequestOrder = new ConcurrentLinkedQueue<>();
    private final AtomicInteger txRequestIdCounter = new AtomicInteger(1);
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "lighter-ws-reconnect");
        thread.setDaemon(true);
        return thread;
    });
    private volatile ILighterTransactionSigner orderSigner;
    private volatile LighterNativeTransactionSigner wsAuthSigner;
    private volatile LighterWebSocketClient txClient;
    private volatile LighterSendTxWebSocketProcessor txProcessor;

    public LighterWebSocketApi() {
        this(LighterConfiguration.getInstance().getWebSocketUrl(), null);
    }

    public LighterWebSocketApi(String webSocketUrl) {
        this(webSocketUrl, null);
    }

    public LighterWebSocketApi(String webSocketUrl, ILighterTransactionSigner orderSigner) {
        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            throw new IllegalArgumentException("webSocketUrl is required");
        }
        this.webSocketUrl = webSocketUrl;
        this.orderSigner = orderSigner;
    }

    @Override
    public LighterWebSocketClient subscribeMarketStats(int marketId, ILighterMarketStatsListener listener) {
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }
        String channel = LighterWSClientBuilder.getMarketStatsChannel(marketId);
        return subscribeMarketStats(channel, listener);
    }

    @Override
    public LighterWebSocketClient subscribeAllMarketStats(ILighterMarketStatsListener listener) {
        return subscribeMarketStats(LighterWSClientBuilder.WS_TYPE_MARKET_STATS_ALL, listener);
    }

    @Override
    public LighterWebSocketClient subscribeOrderBook(int marketId, ILighterOrderBookListener listener) {
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }
        String channel = LighterWSClientBuilder.getOrderBookChannel(marketId);
        return subscribeOrderBook(channel, listener);
    }

    @Override
    public LighterWebSocketClient subscribeTrades(int marketId, ILighterTradeListener listener) {
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }
        String channel = LighterWSClientBuilder.getTradeChannel(marketId);
        return subscribeTrades(channel, listener);
    }

    @Override
    public LighterWebSocketClient subscribeAccountAllTrades(long accountIndex, String authToken,
            ILighterAccountAllTradesListener listener) {
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("authToken is required");
        }
        String channel = LighterWSClientBuilder.getAccountAllTradesChannel(accountIndex);
        return subscribeAccountAllTrades(channel, accountIndex, authToken, listener);
    }

    @Override
    public LighterWebSocketClient subscribeAccountOrders(int marketIndex, long accountIndex, String authToken,
            ILighterAccountOrdersListener listener) {
        if (marketIndex < 0) {
            throw new IllegalArgumentException("marketIndex must be >= 0");
        }
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("authToken is required");
        }
        String channel = LighterWSClientBuilder.getAccountOrdersChannel(marketIndex, accountIndex);
        return subscribeAccountOrders(channel, accountIndex, authToken, listener);
    }

    @Override
    public LighterWebSocketClient subscribeAccountStats(long accountIndex, ILighterAccountStatsListener listener) {
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        String channel = LighterWSClientBuilder.getAccountStatsChannel(accountIndex);
        return subscribeAccountStats(channel, accountIndex, listener);
    }

    @Override
    public LighterSignedTransaction signOrder(LighterCreateOrderRequest orderRequest) {
        if (orderRequest == null) {
            throw new IllegalArgumentException("orderRequest is required");
        }
        return getRequiredOrderSigner().signCreateOrder(orderRequest);
    }

    @Override
    public LighterSendTxResponse submitOrder(LighterCreateOrderRequest orderRequest) {
        LighterSignedTransaction signedTransaction = signOrder(orderRequest);
        return sendSignedTransaction(signedTransaction.getTxType(), signedTransaction.getTxInfo());
    }

    @Override
    public LighterSignedTransaction signCancelOrder(LighterCancelOrderRequest cancelRequest) {
        if (cancelRequest == null) {
            throw new IllegalArgumentException("cancelRequest is required");
        }
        return getRequiredOrderSigner().signCancelOrder(cancelRequest);
    }

    @Override
    public LighterSendTxResponse cancelOrder(LighterCancelOrderRequest cancelRequest) {
        LighterSignedTransaction signedTransaction = signCancelOrder(cancelRequest);
        return sendSignedTransaction(signedTransaction.getTxType(), signedTransaction.getTxInfo());
    }

    @Override
    public LighterSignedTransaction signModifyOrder(LighterModifyOrderRequest modifyRequest) {
        if (modifyRequest == null) {
            throw new IllegalArgumentException("modifyRequest is required");
        }
        return getRequiredOrderSigner().signModifyOrder(modifyRequest);
    }

    @Override
    public LighterSendTxResponse modifyOrder(LighterModifyOrderRequest modifyRequest) {
        LighterSignedTransaction signedTransaction = signModifyOrder(modifyRequest);
        return sendSignedTransaction(signedTransaction.getTxType(), signedTransaction.getTxInfo());
    }

    @Override
    public LighterSendTxResponse sendSignedTransaction(int txType, JSONObject txInfo) {
        if (txType <= 0) {
            throw new IllegalArgumentException("txType must be > 0");
        }
        if (txInfo == null) {
            throw new IllegalArgumentException("txInfo is required");
        }

        String requestId = String.valueOf(txRequestIdCounter.getAndIncrement());
        CompletableFuture<LighterSendTxResponse> future = new CompletableFuture<>();
        pendingTxResponses.put(requestId, future);
        pendingTxRequestOrder.offer(requestId);

        try {
            LighterWebSocketClient client = getOrCreateTxClient();
            client.postMessage(buildSendTxMessage(requestId, txType, txInfo));
            return future.get(getSendTxTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timeout waiting for Lighter sendtx response for id " + requestId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Lighter sendtx response", e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to submit Lighter signed transaction", e);
        } finally {
            pendingTxResponses.remove(requestId);
            pendingTxRequestOrder.remove(requestId);
        }
    }

    protected synchronized LighterWebSocketClient subscribeMarketStats(String channel,
            ILighterMarketStatsListener listener) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        manualCloseChannels.remove(channel);
        channelSubscribeAuth.remove(channel);
        cancelReconnectTask(channel);

        LighterMarketStatsWebSocketProcessor processor = marketStatsProcessors.get(channel);
        if (processor == null) {
            processor = createMarketStatsProcessor(channel);
            LighterWebSocketClient client = createClient(channel, processor);
            marketStatsProcessors.put(channel, processor);
            channelClients.put(channel, client);
            logger.info("Connecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            acquireConnectAttemptSlot("market stats/" + channel);
            client.connect();
        }

        processor.addEventListener(listener);
        return channelClients.get(channel);
    }

    protected synchronized LighterWebSocketClient subscribeOrderBook(String channel, ILighterOrderBookListener listener) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        manualCloseChannels.remove(channel);
        channelSubscribeAuth.remove(channel);
        cancelReconnectTask(channel);

        LighterOrderBookWebSocketProcessor processor = orderBookProcessors.get(channel);
        if (processor == null) {
            processor = createOrderBookProcessor(channel);
            LighterWebSocketClient client = createClient(channel, processor);
            orderBookProcessors.put(channel, processor);
            channelClients.put(channel, client);
            logger.info("Connecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            acquireConnectAttemptSlot("order book/" + channel);
            client.connect();
        }

        processor.addEventListener(listener);
        return channelClients.get(channel);
    }

    protected synchronized LighterWebSocketClient subscribeTrades(String channel, ILighterTradeListener listener) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        manualCloseChannels.remove(channel);
        channelSubscribeAuth.remove(channel);
        cancelReconnectTask(channel);

        LighterTradesWebSocketProcessor processor = tradeProcessors.get(channel);
        if (processor == null) {
            processor = createTradesProcessor(channel);
            LighterWebSocketClient client = createClient(channel, processor);
            tradeProcessors.put(channel, processor);
            channelClients.put(channel, client);
            logger.info("Connecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            acquireConnectAttemptSlot("trade/" + channel);
            client.connect();
        }

        processor.addEventListener(listener);
        return channelClients.get(channel);
    }

    protected synchronized LighterWebSocketClient subscribeAccountAllTrades(String channel, long accountIndex,
            String authToken,
            ILighterAccountAllTradesListener listener) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("authToken is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        manualCloseChannels.remove(channel);
        accountAllTradesChannelAccountIndex.put(channel, accountIndex);
        String subscribeAuth = resolveSubscribeAuth(channel, authToken);
        channelSubscribeAuth.put(channel, subscribeAuth);
        cancelReconnectTask(channel);

        LighterAccountAllTradesWebSocketProcessor processor = accountAllTradesProcessors.get(channel);
        if (processor == null) {
            processor = createAccountAllTradesProcessor(channel);
            LighterWebSocketClient client = createClient(channel, processor, subscribeAuth);
            accountAllTradesProcessors.put(channel, processor);
            channelClients.put(channel, client);
            logger.info("Connecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            acquireConnectAttemptSlot("account all trades/" + channel);
            client.connect();
        }

        processor.addEventListener(listener);
        return channelClients.get(channel);
    }

    protected synchronized LighterWebSocketClient subscribeAccountOrders(String channel, long accountIndex,
            String authToken,
            ILighterAccountOrdersListener listener) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("authToken is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        manualCloseChannels.remove(channel);
        accountOrdersChannelAccountIndex.put(channel, accountIndex);
        String subscribeAuth = resolveSubscribeAuth(channel, authToken);
        channelSubscribeAuth.put(channel, subscribeAuth);
        cancelReconnectTask(channel);

        LighterAccountOrdersWebSocketProcessor processor = accountOrdersProcessors.get(channel);
        if (processor == null) {
            processor = createAccountOrdersProcessor(channel);
            LighterWebSocketClient client = createClient(channel, processor, subscribeAuth);
            accountOrdersProcessors.put(channel, processor);
            channelClients.put(channel, client);
            logger.info("Connecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            acquireConnectAttemptSlot("account orders/" + channel);
            client.connect();
        }

        processor.addEventListener(listener);
        return channelClients.get(channel);
    }

    protected synchronized LighterWebSocketClient subscribeAccountStats(String channel, long accountIndex,
            ILighterAccountStatsListener listener) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        manualCloseChannels.remove(channel);
        channelSubscribeAuth.remove(channel);
        cancelReconnectTask(channel);

        LighterAccountStatsWebSocketProcessor processor = accountStatsProcessors.get(channel);
        if (processor == null) {
            processor = createAccountStatsProcessor(channel);
            LighterWebSocketClient client = createClient(channel, processor);
            accountStatsProcessors.put(channel, processor);
            channelClients.put(channel, client);
            logger.info("Connecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            acquireConnectAttemptSlot("account stats/" + channel);
            client.connect();
        }

        processor.addEventListener(listener);
        return channelClients.get(channel);
    }

    @Override
    public synchronized void disconnectAll() {
        manualCloseChannels.addAll(channelClients.keySet());
        cancelAllReconnectTasks();
        failPendingTxResponses(new IllegalStateException("Lighter websocket disconnected"));

        if (txClient != null) {
            try {
                txClient.close();
            } catch (Exception e) {
                logger.warn("Error closing Lighter tx websocket client", e);
            } finally {
                txClient = null;
            }
        }
        if (txProcessor != null) {
            try {
                txProcessor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down Lighter tx websocket processor", e);
            } finally {
                txProcessor = null;
            }
        }

        for (LighterWebSocketClient client : channelClients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("Error closing websocket client", e);
            }
        }
        for (LighterMarketStatsWebSocketProcessor processor : marketStatsProcessors.values()) {
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down websocket processor", e);
            }
        }
        for (LighterOrderBookWebSocketProcessor processor : orderBookProcessors.values()) {
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down websocket processor", e);
            }
        }
        for (LighterTradesWebSocketProcessor processor : tradeProcessors.values()) {
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down websocket processor", e);
            }
        }
        for (LighterAccountAllTradesWebSocketProcessor processor : accountAllTradesProcessors.values()) {
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down websocket processor", e);
            }
        }
        for (LighterAccountOrdersWebSocketProcessor processor : accountOrdersProcessors.values()) {
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down websocket processor", e);
            }
        }
        for (LighterAccountStatsWebSocketProcessor processor : accountStatsProcessors.values()) {
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down websocket processor", e);
            }
        }
        channelClients.clear();
        marketStatsProcessors.clear();
        orderBookProcessors.clear();
        tradeProcessors.clear();
        accountAllTradesProcessors.clear();
        accountOrdersProcessors.clear();
        accountStatsProcessors.clear();
        channelSubscribeAuth.clear();
        accountAllTradesChannelAccountIndex.clear();
        accountOrdersChannelAccountIndex.clear();
        wsAuthSigner = null;
        manualCloseChannels.clear();
        recentConnectAttemptTimestamps.clear();
    }

    protected LighterMarketStatsWebSocketProcessor createMarketStatsProcessor(String channel) {
        return new LighterMarketStatsWebSocketProcessor(() -> {
            handleChannelClosed(channel, ChannelType.MARKET_STATS);
        });
    }

    protected LighterOrderBookWebSocketProcessor createOrderBookProcessor(String channel) {
        return new LighterOrderBookWebSocketProcessor(() -> {
            handleChannelClosed(channel, ChannelType.ORDER_BOOK);
        });
    }

    protected LighterTradesWebSocketProcessor createTradesProcessor(String channel) {
        return new LighterTradesWebSocketProcessor(() -> {
            handleChannelClosed(channel, ChannelType.TRADE);
        });
    }

    protected LighterAccountAllTradesWebSocketProcessor createAccountAllTradesProcessor(String channel) {
        return new LighterAccountAllTradesWebSocketProcessor(() -> {
            handleChannelClosed(channel, ChannelType.ACCOUNT_ALL_TRADES);
        });
    }

    protected LighterAccountOrdersWebSocketProcessor createAccountOrdersProcessor(String channel) {
        return new LighterAccountOrdersWebSocketProcessor(() -> {
            handleChannelClosed(channel, ChannelType.ACCOUNT_ORDERS);
        });
    }

    protected LighterAccountStatsWebSocketProcessor createAccountStatsProcessor(String channel) {
        return new LighterAccountStatsWebSocketProcessor(() -> {
            handleChannelClosed(channel, ChannelType.ACCOUNT_STATS);
        });
    }

    protected LighterWebSocketClient createClient(String channel, IWebSocketProcessor processor) {
        try {
            return LighterWSClientBuilder.buildClient(webSocketUrl, channel, processor);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create Lighter websocket client for channel " + channel, e);
        }
    }

    protected LighterWebSocketClient createClient(String channel, IWebSocketProcessor processor, String subscribeAuth) {
        if (subscribeAuth == null || subscribeAuth.isBlank()) {
            return createClient(channel, processor);
        }
        try {
            return LighterWSClientBuilder.buildClient(webSocketUrl, channel, subscribeAuth, processor);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create Lighter websocket client for channel " + channel, e);
        }
    }

    protected long getReconnectDelayMillis() {
        return DEFAULT_RECONNECT_DELAY_MILLIS;
    }

    protected int getMaxConnectAttemptsPerWindow() {
        return DEFAULT_MAX_CONNECT_ATTEMPTS_PER_MINUTE;
    }

    protected long getConnectAttemptWindowMillis() {
        return DEFAULT_CONNECT_ATTEMPT_WINDOW_MILLIS;
    }

    protected void acquireConnectAttemptSlot(String connectionType) {
        while (true) {
            long waitMillis;
            synchronized (recentConnectAttemptTimestamps) {
                long now = System.currentTimeMillis();
                evictExpiredConnectAttempts(now);
                int maxAttempts = getMaxConnectAttemptsPerWindow();
                if (maxAttempts <= 0 || recentConnectAttemptTimestamps.size() < maxAttempts) {
                    recentConnectAttemptTimestamps.addLast(now);
                    return;
                }

                Long oldest = recentConnectAttemptTimestamps.peekFirst();
                if (oldest == null) {
                    continue;
                }
                long elapsed = now - oldest;
                waitMillis = Math.max(1L, getConnectAttemptWindowMillis() - elapsed);
            }

            logger.warn(
                    "Throttling websocket connect for {} by {} ms to stay under Lighter connection rate limits",
                    connectionType, waitMillis);
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for websocket reconnect rate limit slot", e);
            }
        }
    }

    protected void evictExpiredConnectAttempts(long now) {
        long windowMillis = getConnectAttemptWindowMillis();
        Long oldest = recentConnectAttemptTimestamps.peekFirst();
        while (oldest != null && now - oldest >= windowMillis) {
            recentConnectAttemptTimestamps.pollFirst();
            oldest = recentConnectAttemptTimestamps.peekFirst();
        }
    }

    protected String resolveSubscribeAuth(String channel, String fallbackAuthToken) {
        if (isAccountAllTradesChannel(channel)) {
            Long accountIndex = accountAllTradesChannelAccountIndex.get(channel);
            if (accountIndex == null) {
                return fallbackAuthToken;
            }
            String refreshedAuth = createFreshAccountAllTradesAuthToken(accountIndex.longValue());
            if (refreshedAuth != null && !refreshedAuth.isBlank()) {
                return refreshedAuth;
            }
            return fallbackAuthToken;
        }

        if (isAccountOrdersChannel(channel)) {
            Long accountIndex = accountOrdersChannelAccountIndex.get(channel);
            if (accountIndex == null) {
                return fallbackAuthToken;
            }
            String refreshedAuth = createFreshAccountOrdersAuthToken(accountIndex.longValue());
            if (refreshedAuth != null && !refreshedAuth.isBlank()) {
                return refreshedAuth;
            }
        }

        return fallbackAuthToken;
    }

    protected boolean isAccountAllTradesChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_ACCOUNT_ALL_TRADES;
        return channel.startsWith(prefix + "/") || channel.startsWith(prefix + ":");
    }

    protected boolean isAccountOrdersChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_ACCOUNT_ORDERS;
        return channel.startsWith(prefix + "/") || channel.startsWith(prefix + ":");
    }

    protected String createFreshAccountAllTradesAuthToken(long accountIndex) {
        LighterNativeTransactionSigner signer = getOrCreateWsAuthSigner();
        if (signer == null) {
            return null;
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        long expiry = timestamp + getWsAuthTokenTtlSeconds();
        try {
            return signer.createAuthTokenWithExpiry(timestamp, expiry, getWsAuthApiKeyIndex(), accountIndex);
        } catch (Exception e) {
            logger.warn("Unable to create fresh account_all_trades auth token for account {}", accountIndex, e);
            return null;
        }
    }

    protected String createFreshAccountOrdersAuthToken(long accountIndex) {
        LighterNativeTransactionSigner signer = getOrCreateWsAuthSigner();
        if (signer == null) {
            return null;
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        long expiry = timestamp + getWsAuthTokenTtlSeconds();
        try {
            return signer.createAuthTokenWithExpiry(timestamp, expiry, getWsAuthApiKeyIndex(), accountIndex);
        } catch (Exception e) {
            logger.warn("Unable to create fresh account_orders auth token for account {}", accountIndex, e);
            return null;
        }
    }

    protected long getWsAuthTokenTtlSeconds() {
        return DEFAULT_WS_AUTH_TOKEN_TTL_SECONDS;
    }

    protected int getWsAuthApiKeyIndex() {
        return LighterConfiguration.getInstance().getApiKeyIndex();
    }

    protected synchronized LighterNativeTransactionSigner getOrCreateWsAuthSigner() {
        LighterConfiguration configuration = LighterConfiguration.getInstance();
        if (!configuration.hasPrivateKeyConfiguration()) {
            return null;
        }

        if (wsAuthSigner == null) {
            wsAuthSigner = createWsAuthSigner(configuration);
        }
        return wsAuthSigner;
    }

    protected LighterNativeTransactionSigner createWsAuthSigner(LighterConfiguration configuration) {
        return new LighterNativeTransactionSigner(configuration);
    }

    protected void handleChannelClosed(String channel, ChannelType type) {
        logger.info("Lighter websocket closed for channel: {}", channel);
        channelClients.remove(channel);
        if (manualCloseChannels.remove(channel)) {
            return;
        }
        scheduleReconnect(channel, type);
    }

    protected void scheduleReconnect(String channel, ChannelType type) {
        if (!hasProcessor(channel, type)) {
            return;
        }
        ScheduledFuture<?> existingTask = reconnectTasks.get(channel);
        if (existingTask != null && !existingTask.isDone()) {
            return;
        }

        long delay = getReconnectDelayMillis();
        ScheduledFuture<?> task = reconnectScheduler.schedule(() -> reconnectChannel(channel, type), delay,
                TimeUnit.MILLISECONDS);
        reconnectTasks.put(channel, task);
        logger.info("Scheduled reconnect for channel {} in {} ms", channel, delay);
    }

    protected synchronized void reconnectChannel(String channel, ChannelType type) {
        reconnectTasks.remove(channel);
        if (manualCloseChannels.contains(channel)) {
            return;
        }
        if (channelClients.containsKey(channel)) {
            return;
        }

        IWebSocketProcessor processor = getProcessor(channel, type);
        if (processor == null) {
            return;
        }

        try {
            String subscribeAuth = resolveSubscribeAuth(channel, channelSubscribeAuth.get(channel));
            if (subscribeAuth != null && !subscribeAuth.isBlank()) {
                channelSubscribeAuth.put(channel, subscribeAuth);
            }
            LighterWebSocketClient client = createClient(channel, processor, subscribeAuth);
            channelClients.put(channel, client);
            logger.info("Reconnecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            acquireConnectAttemptSlot("reconnect/" + channel);
            client.connect();
        } catch (Exception e) {
            logger.warn("Reconnect attempt failed for channel {}", channel, e);
            scheduleReconnect(channel, type);
        }
    }

    protected IWebSocketProcessor getProcessor(String channel, ChannelType type) {
        return switch (type) {
            case MARKET_STATS -> marketStatsProcessors.get(channel);
            case ORDER_BOOK -> orderBookProcessors.get(channel);
            case TRADE -> tradeProcessors.get(channel);
            case ACCOUNT_ALL_TRADES -> accountAllTradesProcessors.get(channel);
            case ACCOUNT_ORDERS -> accountOrdersProcessors.get(channel);
            case ACCOUNT_STATS -> accountStatsProcessors.get(channel);
        };
    }

    protected boolean hasProcessor(String channel, ChannelType type) {
        return getProcessor(channel, type) != null;
    }

    protected void cancelReconnectTask(String channel) {
        ScheduledFuture<?> task = reconnectTasks.remove(channel);
        if (task != null) {
            task.cancel(false);
        }
    }

    protected void cancelAllReconnectTasks() {
        for (ScheduledFuture<?> task : reconnectTasks.values()) {
            if (task != null) {
                task.cancel(false);
            }
        }
        reconnectTasks.clear();
    }

    @Override
    public void onWebSocketEvent(LighterSendTxResponse event) {
        if (event == null) {
            return;
        }
        CompletableFuture<LighterSendTxResponse> future = null;
        String requestId = event.getRequestId();

        if (requestId != null) {
            future = pendingTxResponses.remove(requestId);
            pendingTxRequestOrder.remove(requestId);
        } else {
            String pendingId = pendingTxRequestOrder.poll();
            while (pendingId != null && future == null) {
                future = pendingTxResponses.remove(pendingId);
                if (future == null) {
                    pendingId = pendingTxRequestOrder.poll();
                }
            }
        }
        if (future != null) {
            future.complete(event);
        }
    }

    protected synchronized LighterWebSocketClient getOrCreateTxClient() {
        if (txProcessor == null) {
            txProcessor = createSendTxProcessor();
            txProcessor.addEventListener(this);
        }

        if (txClient != null && txClient.isOpen()) {
            return txClient;
        }

        txClient = createSendTxClient(txProcessor);
        logger.info("Connecting to Lighter tx websocket endpoint: {}", getSendTxWebSocketUrl());
        acquireConnectAttemptSlot("tx");
        txClient.connect();
        waitForTxClientConnection(txClient, getTxConnectTimeoutMillis());
        return txClient;
    }

    protected LighterSendTxWebSocketProcessor createSendTxProcessor() {
        return new LighterSendTxWebSocketProcessor(this::handleTxClientClosed);
    }

    protected LighterWebSocketClient createSendTxClient(IWebSocketProcessor processor) {
        try {
            return LighterWSClientBuilder.buildClient(getSendTxWebSocketUrl(), null, processor);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create Lighter websocket tx client", e);
        }
    }

    protected void handleTxClientClosed() {
        txClient = null;
        failPendingTxResponses(new IllegalStateException("Lighter tx websocket connection closed"));
    }

    protected void failPendingTxResponses(Exception cause) {
        for (CompletableFuture<LighterSendTxResponse> future : pendingTxResponses.values()) {
            if (future != null && !future.isDone()) {
                future.completeExceptionally(cause);
            }
        }
        pendingTxResponses.clear();
        pendingTxRequestOrder.clear();
    }

    protected long getSendTxTimeoutMillis() {
        return DEFAULT_SEND_TX_TIMEOUT_MILLIS;
    }

    protected long getTxConnectTimeoutMillis() {
        return DEFAULT_TX_CONNECT_TIMEOUT_MILLIS;
    }

    protected String buildSendTxMessage(String requestId, int txType, JSONObject txInfo) {
        JSONObject data = new JSONObject();
        data.put("tx_type", txType);
        data.put("tx_info", txInfo);

        JSONObject request = new JSONObject();
        request.put("type", "jsonapi/sendtx");
        request.put("data", data);
        request.put("id", requestId);
        return request.toString();
    }

    protected void waitForTxClientConnection(LighterWebSocketClient client, long timeoutMillis) {
        if (client == null) {
            throw new IllegalStateException("tx websocket client is not initialized");
        }
        long deadlineMillis = System.currentTimeMillis() + timeoutMillis;
        while (!client.isOpen() && System.currentTimeMillis() < deadlineMillis) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for tx websocket connection", e);
            }
        }
        if (!client.isOpen()) {
            throw new IllegalStateException("Timed out waiting for tx websocket connection to open");
        }
    }

    protected String getSendTxWebSocketUrl() {
        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            throw new IllegalStateException("No websocket URL configured");
        }
        if (!webSocketUrl.contains("?")) {
            return webSocketUrl;
        }

        int queryStart = webSocketUrl.indexOf('?');
        String baseUrl = webSocketUrl.substring(0, queryStart);
        String query = webSocketUrl.substring(queryStart + 1);

        String filteredQuery = Arrays.stream(query.split("&"))
                .filter(part -> part != null && !part.isBlank())
                .filter(part -> !part.toLowerCase(Locale.ROOT).startsWith("readonly="))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");

        if (filteredQuery.isBlank()) {
            return baseUrl;
        }
        return baseUrl + "?" + filteredQuery;
    }

    protected ILighterTransactionSigner getRequiredOrderSigner() {
        ILighterTransactionSigner signer = getOrCreateOrderSigner();
        if (signer == null) {
            throw new IllegalStateException("No Lighter signer configured. Set "
                    + LighterConfiguration.LIGHTER_PRIVATE_KEY + ", " + LighterConfiguration.LIGHTER_ACCOUNT_INDEX
                    + ", and " + LighterConfiguration.LIGHTER_API_KEY_INDEX + ".");
        }
        return signer;
    }

    protected synchronized ILighterTransactionSigner getOrCreateOrderSigner() {
        if (orderSigner != null) {
            return orderSigner;
        }
        LighterConfiguration configuration = LighterConfiguration.getInstance();
        if (!configuration.hasPrivateKeyConfiguration()) {
            return null;
        }
        orderSigner = createOrderSigner(configuration);
        return orderSigner;
    }

    protected ILighterTransactionSigner createOrderSigner(LighterConfiguration configuration) {
        return new LighterNativeTransactionSigner(configuration);
    }
}
