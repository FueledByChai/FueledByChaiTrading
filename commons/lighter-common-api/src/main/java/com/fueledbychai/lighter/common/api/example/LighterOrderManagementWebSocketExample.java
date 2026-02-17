package com.fueledbychai.lighter.common.api.example;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.LighterTickerRegistry;
import com.fueledbychai.lighter.common.api.ILighterRestApi;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.LighterConfiguration;
import com.fueledbychai.lighter.common.api.LighterRestApi;
import com.fueledbychai.lighter.common.api.LighterSignerClient;
import com.fueledbychai.lighter.common.api.LighterWebSocketApi;
import com.fueledbychai.lighter.common.api.auth.LighterAccountTier;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierRequest;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierResponse;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterOrderType;
import com.fueledbychai.lighter.common.api.order.LighterTimeInForce;
import com.fueledbychai.lighter.common.api.signer.LighterNativeTransactionSigner;
import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;
import com.fueledbychai.lighter.common.api.ws.model.LighterSendTxResponse;
import com.fueledbychai.lighter.common.api.ws.model.LighterTradesUpdate;

/**
 * Example for signing + submitting Lighter order management transactions over
 * websocket (create/modify/cancel).
 *
 * Numeric fields are low-level integer precision values expected by the Lighter
 * signer.
 */
public class LighterOrderManagementWebSocketExample {

    private static final Logger logger = LoggerFactory.getLogger(LighterOrderManagementWebSocketExample.class);
    private static final String LATENCY_LOG_PREFIX = "[LATENCY]";

    // Trading intent (hard-coded for client-style example usage).
    private static final String SYMBOL = "BTC";
    private static final boolean IS_ASK = false;
    private static final boolean REDUCE_ONLY = false;
    private static final long CREATE_BASE_AMOUNT = 1_000L;
    private static final int CREATE_LIMIT_PRICE = 710_000;
    private static final long MODIFY_BASE_AMOUNT = 900L;
    private static final int MODIFY_LIMIT_PRICE = 99_500;
    private static final LighterAccountTier TARGET_ACCOUNT_TIER = LighterAccountTier.PREMIUM;
    private static final long ACCOUNT_TRADES_AUTH_TTL_SECONDS = 600L;
    private static final long WAIT_FOR_ORDER_EXECUTION_MILLIS = 10_000L;
    private static final long STALE_TRADE_TOLERANCE_MILLIS = 2_000L;

    // Set this to an existing open order index to run modify/cancel in this
    // example.
    private static final long EXISTING_ORDER_INDEX_FOR_MODIFY_CANCEL = -1L;
    private static final AtomicReference<PendingOrderExecution> PENDING_ORDER_EXECUTION = new AtomicReference<>();

    public static void main(String[] args) {
        LighterConfiguration.reset();
        LighterConfiguration configuration = LighterConfiguration.getInstance();
        validateSigningConfiguration(configuration);

        ILighterRestApi restApi = new LighterRestApi(configuration.getRestUrl(), configuration.getAccountAddress(),
                configuration.getPrivateKey(), !configuration.isProductionEnvironment());
        ensureAccountTier(restApi, configuration, TARGET_ACCOUNT_TIER);
        int marketIndex = resolveMarketIndex(restApi, SYMBOL);
        long clientOrderIndex = System.currentTimeMillis();

        ILighterWebSocketApi wsApi = new LighterWebSocketApi(configuration.getWebSocketUrl());
        LighterSignerClient signerClient = new LighterSignerClient(wsApi,
                new LighterNativeTransactionSigner(configuration));

        Runtime.getRuntime().addShutdownHook(new Thread(wsApi::disconnectAll));

        logger.info("Lighter order example env={} restUrl={} wsUrl={} symbol={} marketIndex={} isAsk={} reduceOnly={}",
                configuration.getEnvironment(), configuration.getRestUrl(), configuration.getWebSocketUrl(), SYMBOL,
                marketIndex, IS_ASK, REDUCE_ONLY);

        try {
            subscribeTradeFeeds(wsApi, signerClient, configuration, marketIndex);

            // placeOrder(signerClient, marketIndex, clientOrderIndex, CREATE_BASE_AMOUNT,
            // CREATE_LIMIT_PRICE, IS_ASK,
            // REDUCE_ONLY);
            PendingOrderExecution pendingOrderExecution = placeMarketOrder(signerClient, marketIndex, clientOrderIndex + 1,
                    CREATE_BASE_AMOUNT, CREATE_LIMIT_PRICE, IS_ASK, REDUCE_ONLY);

            // if (EXISTING_ORDER_INDEX_FOR_MODIFY_CANCEL < 0) {
            // logger.info("Skipping modify/cancel because
            // EXISTING_ORDER_INDEX_FOR_MODIFY_CANCEL is unset. "
            // + "Set it to an open order index to run those calls.");
            // return;
            // }

            // modifyOrder(signerClient, marketIndex,
            // EXISTING_ORDER_INDEX_FOR_MODIFY_CANCEL, MODIFY_BASE_AMOUNT,
            // MODIFY_LIMIT_PRICE, IS_ASK, REDUCE_ONLY);

            // cancelOrder(signerClient, marketIndex,
            // EXISTING_ORDER_INDEX_FOR_MODIFY_CANCEL);
            waitForOrderExecution(pendingOrderExecution);
        } finally {
            wsApi.disconnectAll();
        }
    }

    private static void subscribeTradeFeeds(ILighterWebSocketApi wsApi, LighterSignerClient signerClient,
            LighterConfiguration configuration, int marketIndex) {
        wsApi.subscribeTrades(marketIndex, LighterOrderManagementWebSocketExample::handleMarketTradesUpdate);

        long timestamp = System.currentTimeMillis() / 1000L;
        long expiry = timestamp + ACCOUNT_TRADES_AUTH_TTL_SECONDS;
        String accountTradesAuthToken = signerClient.createAuthTokenWithExpiry(timestamp, expiry,
                configuration.getApiKeyIndex(), configuration.getAccountIndex());
        wsApi.subscribeAccountAllTrades(configuration.getAccountIndex(), accountTradesAuthToken,
                LighterOrderManagementWebSocketExample::handleAccountAllTradesUpdate);

        logger.info("Subscribed trade feeds for marketIndex={} accountIndex={}", marketIndex,
                configuration.getAccountIndex());
    }

    private static void handleMarketTradesUpdate(LighterTradesUpdate update) {
        logTradeUpdate("market trades", update);
    }

    private static void handleAccountAllTradesUpdate(LighterTradesUpdate update) {
        checkForOrderExecution(update);
        logTradeUpdate("account_all_trades", update);
    }

    private static void logTradeUpdate(String feedType, LighterTradesUpdate update) {
        LighterTrade first = update == null ? null : update.getFirstTrade();
        logger.info("{} update: channel={} type={} trades={} firstTradeId={} market={} side={} price={} size={} isMakerAsk={} timestamp={}",
                feedType, update == null ? null : update.getChannel(), update == null ? null : update.getMessageType(),
                update == null || update.getTrades() == null ? 0 : update.getTrades().size(),
                first == null ? null : first.getId(), first == null ? null : first.getMarketId(),
                first == null ? null : first.getType(), first == null ? null : first.getPrice(),
                first == null ? null : first.getSize(), first == null ? null : first.getMakerAsk(),
                first == null ? null : first.getTimestamp());
    }

    private static void waitForOrderExecution(PendingOrderExecution pendingOrderExecution) {
        if (pendingOrderExecution == null || !pendingOrderExecution.isSubmissionAccepted()) {
            return;
        }
        logLatencyInfo("Waiting up to {} ms for order execution...", WAIT_FOR_ORDER_EXECUTION_MILLIS);
        try {
            boolean executed = pendingOrderExecution.awaitExecution(WAIT_FOR_ORDER_EXECUTION_MILLIS);
            if (!executed) {
                logLatencyWarn("No matching execution trade observed within {} ms after order submission",
                        WAIT_FOR_ORDER_EXECUTION_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            PENDING_ORDER_EXECUTION.compareAndSet(pendingOrderExecution, null);
        }
    }

    private static PendingOrderExecution placeMarketOrder(LighterSignerClient signerClient, int marketIndex,
            long clientOrderIndex,
            long baseAmount, int price, boolean isAsk, boolean reduceOnly) {
        LighterCreateOrderRequest req = LighterCreateOrderRequest.marketOrder(marketIndex, clientOrderIndex, baseAmount,
                price, isAsk);
        req.setReduceOnly(reduceOnly);
        req.setTimeInForce(LighterTimeInForce.IOC);

        PendingOrderExecution pendingOrderExecution = new PendingOrderExecution(marketIndex, clientOrderIndex,
                System.currentTimeMillis(), System.nanoTime());
        PENDING_ORDER_EXECUTION.set(pendingOrderExecution);

        logger.info("Signing and sending order");
        LighterSendTxResponse response = signerClient.createOrder(req);
        pendingOrderExecution.recordSubmissionResponse(response, System.nanoTime());
        logLatencyInfo("Create market order submit ack latencyMs={}", pendingOrderExecution.getSubmitAckLatencyMillis());
        logSendTxResponse("create market order", response);
        if (!pendingOrderExecution.isSubmissionAccepted()) {
            logLatencyWarn("Order submission was not accepted; cannot measure submit-to-execution latency");
            PENDING_ORDER_EXECUTION.compareAndSet(pendingOrderExecution, null);
        }

        return pendingOrderExecution;
    }

    private static void placeOrder(LighterSignerClient signerClient, int marketIndex, long clientOrderIndex,
            long baseAmount, int price, boolean isAsk, boolean reduceOnly) {
        LighterCreateOrderRequest request = new LighterCreateOrderRequest();
        request.setMarketIndex(marketIndex);
        request.setClientOrderIndex(clientOrderIndex);
        request.setBaseAmount(baseAmount);
        request.setPrice(price);
        request.setAsk(isAsk);
        request.setReduceOnly(reduceOnly);
        request.setOrderType(LighterOrderType.LIMIT);
        request.setTimeInForce(LighterTimeInForce.GTT);

        LighterSendTxResponse response = signerClient.createOrder(request);
        logSendTxResponse("create", response);
    }

    private static void modifyOrder(LighterSignerClient signerClient, int marketIndex, long orderIndex, long baseAmount,
            int price, boolean isAsk, boolean reduceOnly) {
        LighterModifyOrderRequest request = new LighterModifyOrderRequest();
        request.setMarketIndex(marketIndex);
        request.setOrderIndex(orderIndex);
        request.setBaseAmount(baseAmount);
        request.setPrice(price);
        request.setAsk(isAsk);
        request.setReduceOnly(reduceOnly);
        request.setOrderType(LighterOrderType.LIMIT);
        request.setTimeInForce(LighterTimeInForce.GTT);

        LighterSendTxResponse response = signerClient.modifyOrder(request);
        logSendTxResponse("modify", response);
    }

    private static void cancelOrder(LighterSignerClient signerClient, int marketIndex, long orderIndex) {
        LighterCancelOrderRequest request = new LighterCancelOrderRequest();
        request.setMarketIndex(marketIndex);
        request.setOrderIndex(orderIndex);

        LighterSendTxResponse response = signerClient.cancelOrder(request);
        logSendTxResponse("cancel", response);
    }

    private static void logSendTxResponse(String action, LighterSendTxResponse response) {
        logger.info("{} response: success={} code={} message={} raw={}", action,
                response != null && response.isSuccess(), response == null ? null : response.getCode(),
                response == null ? null : response.getMessage(), response == null ? null : response.getRawMessage());
    }

    private static void checkForOrderExecution(LighterTradesUpdate update) {
        PendingOrderExecution pendingOrderExecution = PENDING_ORDER_EXECUTION.get();
        if (pendingOrderExecution == null || !pendingOrderExecution.isSubmissionAccepted()) {
            return;
        }
        if (update == null || update.getTrades() == null || update.getTrades().isEmpty()) {
            return;
        }

        for (LighterTrade trade : update.getTrades()) {
            if (!pendingOrderExecution.matches(trade)) {
                continue;
            }
            if (!pendingOrderExecution.recordExecution(trade, System.nanoTime())) {
                continue;
            }

            logLatencyInfo(
                    "Order execution latency measured: submitToExecutionLocalMs={} submitToExecutionExchangeMs={} tradeId={} market={} side={} price={} size={} isMakerAsk={} tradeTimestamp={}",
                    pendingOrderExecution.getSubmitToExecutionLocalLatencyMillis(),
                    pendingOrderExecution.getSubmitToExecutionExchangeLatencyMillis(), trade.getId(), trade.getMarketId(),
                    trade.getType(), trade.getPrice(), trade.getSize(), trade.getMakerAsk(), trade.getTimestamp());
            return;
        }
    }

    private static void logLatencyInfo(String message, Object... args) {
        logger.info(LATENCY_LOG_PREFIX + " " + message, args);
    }

    private static void logLatencyWarn(String message, Object... args) {
        logger.warn(LATENCY_LOG_PREFIX + " " + message, args);
    }

    private static void validateSigningConfiguration(LighterConfiguration configuration) {
        if (!configuration.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException(
                    "Missing signing credentials. Configure " + LighterConfiguration.LIGHTER_ACCOUNT_ADDRESS + " and "
                            + LighterConfiguration.LIGHTER_PRIVATE_KEY + ".");
        }
    }

    private static void ensureAccountTier(ILighterRestApi restApi, LighterConfiguration configuration,
            LighterAccountTier targetTier) {
        boolean isProduction = configuration.isProductionEnvironment();
        try {
            LighterChangeAccountTierRequest request = new LighterChangeAccountTierRequest();
            request.setAccountIndex(configuration.getAccountIndex());
            request.setNewTier(targetTier);

            LighterChangeAccountTierResponse response = restApi.changeAccountTier(request);
            int code = response == null ? 0 : response.getCode();
            String message = response == null ? "null response" : response.getMessage();
            if (code != 200) {
                String error = "Unable to change account tier to " + targetTier + ". code=" + code + " message="
                        + message;
                if (isProduction) {
                    throw new IllegalStateException(error);
                }
                logger.warn("{} (continuing because environment={})", error, configuration.getEnvironment());
                return;
            }
            logger.info("Ensured account tier={} for accountIndex={} message={}", targetTier,
                    configuration.getAccountIndex(), message);
        } catch (RuntimeException ex) {
            if (isProduction) {
                throw ex;
            }
            logger.warn("Failed to change account tier to {} in environment={} (continuing): {}", targetTier,
                    configuration.getEnvironment(), ex.getMessage());
        }
    }

    private static int resolveMarketIndex(ILighterRestApi restApi, String symbol) {
        Ticker ticker = LighterTickerRegistry.getInstance(restApi)
                .lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        logger.info("Found ticker: " + ticker);
        return ticker.getIdAsInt();

        // InstrumentDescriptor descriptor = restApi.getInstrumentDescriptor(symbol);

    }

    private static class PendingOrderExecution {
        private final int marketIndex;
        private final long clientOrderIndex;
        private final long submitStartedMillis;
        private final long submitStartedNanos;
        private final CountDownLatch executionLatch = new CountDownLatch(1);
        private final AtomicBoolean executionRecorded = new AtomicBoolean(false);
        private volatile boolean submissionAccepted;
        private volatile long submitAckNanos;
        private volatile LighterTrade executionTrade;
        private volatile long executionObservedNanos;

        private PendingOrderExecution(int marketIndex, long clientOrderIndex, long submitStartedMillis,
                long submitStartedNanos) {
            this.marketIndex = marketIndex;
            this.clientOrderIndex = clientOrderIndex;
            this.submitStartedMillis = submitStartedMillis;
            this.submitStartedNanos = submitStartedNanos;
        }

        void recordSubmissionResponse(LighterSendTxResponse response, long ackNanos) {
            this.submitAckNanos = ackNanos;
            this.submissionAccepted = response != null && response.isSuccess();
            if (!this.submissionAccepted) {
                executionLatch.countDown();
            }
        }

        boolean isSubmissionAccepted() {
            return submissionAccepted;
        }

        boolean matches(LighterTrade trade) {
            if (trade == null) {
                return false;
            }
            Integer tradeMarketId = trade.getMarketId();
            if (tradeMarketId != null && tradeMarketId.intValue() != marketIndex) {
                return false;
            }
            Long tradeTimestamp = trade.getTimestamp();
            if (tradeTimestamp != null && tradeTimestamp.longValue() + STALE_TRADE_TOLERANCE_MILLIS < submitStartedMillis) {
                return false;
            }
            return true;
        }

        boolean recordExecution(LighterTrade trade, long observedNanos) {
            if (!executionRecorded.compareAndSet(false, true)) {
                return false;
            }
            this.executionTrade = trade;
            this.executionObservedNanos = observedNanos;
            executionLatch.countDown();
            return true;
        }

        long getSubmitAckLatencyMillis() {
            if (submitAckNanos <= 0L) {
                return -1L;
            }
            return TimeUnit.NANOSECONDS.toMillis(submitAckNanos - submitStartedNanos);
        }

        long getSubmitToExecutionLocalLatencyMillis() {
            if (executionObservedNanos <= 0L) {
                return -1L;
            }
            return TimeUnit.NANOSECONDS.toMillis(executionObservedNanos - submitStartedNanos);
        }

        Long getSubmitToExecutionExchangeLatencyMillis() {
            if (executionTrade == null || executionTrade.getTimestamp() == null) {
                return null;
            }
            return Long.valueOf(executionTrade.getTimestamp().longValue() - submitStartedMillis);
        }

        boolean awaitExecution(long timeoutMillis) throws InterruptedException {
            boolean signaled = executionLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            return signaled && executionRecorded.get();
        }

        @SuppressWarnings("unused")
        long getClientOrderIndex() {
            return clientOrderIndex;
        }
    }
}
