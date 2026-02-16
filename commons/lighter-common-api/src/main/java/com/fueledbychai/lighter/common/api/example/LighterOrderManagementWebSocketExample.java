package com.fueledbychai.lighter.common.api.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.LighterTickerRegistry;
import com.fueledbychai.lighter.common.api.ILighterRestApi;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.LighterConfiguration;
import com.fueledbychai.lighter.common.api.LighterRestApi;
import com.fueledbychai.lighter.common.api.LighterSignerClient;
import com.fueledbychai.lighter.common.api.LighterWebSocketApi;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterOrderType;
import com.fueledbychai.lighter.common.api.order.LighterTimeInForce;
import com.fueledbychai.lighter.common.api.signer.LighterNativeTransactionSigner;
import com.fueledbychai.lighter.common.api.ws.LighterSendTxResponse;

/**
 * Example for signing + submitting Lighter order management transactions over
 * websocket (create/modify/cancel).
 *
 * Numeric fields are low-level integer precision values expected by the Lighter
 * signer.
 */
public class LighterOrderManagementWebSocketExample {

    private static final Logger logger = LoggerFactory.getLogger(LighterOrderManagementWebSocketExample.class);

    // Trading intent (hard-coded for client-style example usage).
    private static final String SYMBOL = "BTC";
    private static final boolean IS_ASK = false;
    private static final boolean REDUCE_ONLY = false;
    private static final long CREATE_BASE_AMOUNT = 1_000L;
    private static final int CREATE_LIMIT_PRICE = 710_000;
    private static final long MODIFY_BASE_AMOUNT = 900L;
    private static final int MODIFY_LIMIT_PRICE = 99_500;

    // Set this to an existing open order index to run modify/cancel in this
    // example.
    private static final long EXISTING_ORDER_INDEX_FOR_MODIFY_CANCEL = -1L;

    public static void main(String[] args) {
        LighterConfiguration.reset();
        LighterConfiguration configuration = LighterConfiguration.getInstance();
        validateSigningConfiguration(configuration);

        ILighterRestApi restApi = new LighterRestApi(configuration.getRestUrl(),
                !configuration.isProductionEnvironment());
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
            // placeOrder(signerClient, marketIndex, clientOrderIndex, CREATE_BASE_AMOUNT,
            // CREATE_LIMIT_PRICE, IS_ASK,
            // REDUCE_ONLY);
            placeMarketOrder(signerClient, marketIndex, clientOrderIndex + 1, CREATE_BASE_AMOUNT, CREATE_LIMIT_PRICE,
                    IS_ASK, REDUCE_ONLY);

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
        } finally {
            wsApi.disconnectAll();
        }
    }

    private static void placeMarketOrder(LighterSignerClient signerClient, int marketIndex, long clientOrderIndex,
            long baseAmount, int price, boolean isAsk, boolean reduceOnly) {
        LighterCreateOrderRequest req = LighterCreateOrderRequest.marketOrder(marketIndex, clientOrderIndex, baseAmount,
                price, isAsk);
        req.setReduceOnly(reduceOnly);
        req.setTimeInForce(LighterTimeInForce.IOC);

        logger.info("Signing and sending order");
        LighterSendTxResponse response = signerClient.createOrder(req);
        logSendTxResponse("create market order", response);

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

    private static void validateSigningConfiguration(LighterConfiguration configuration) {
        if (!configuration.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException(
                    "Missing signing credentials. Configure " + LighterConfiguration.LIGHTER_ACCOUNT_ADDRESS + " and "
                            + LighterConfiguration.LIGHTER_PRIVATE_KEY + ".");
        }
    }

    private static int resolveMarketIndex(ILighterRestApi restApi, String symbol) {
        Ticker ticker = LighterTickerRegistry.getInstance(restApi)
                .lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        logger.info("Found ticker: " + ticker);
        return ticker.getIdAsInt();

        // InstrumentDescriptor descriptor = restApi.getInstrumentDescriptor(symbol);

    }
}
