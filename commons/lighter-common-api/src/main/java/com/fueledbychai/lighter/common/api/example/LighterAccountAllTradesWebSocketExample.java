package com.fueledbychai.lighter.common.api.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.LighterConfiguration;
import com.fueledbychai.lighter.common.api.LighterSignerClient;
import com.fueledbychai.lighter.common.api.LighterWebSocketApi;
import com.fueledbychai.lighter.common.api.signer.LighterNativeTransactionSigner;
import com.fueledbychai.lighter.common.api.ws.LighterTrade;
import com.fueledbychai.lighter.common.api.ws.LighterTradesUpdate;

public class LighterAccountAllTradesWebSocketExample {

    private static final Logger logger = LoggerFactory.getLogger(LighterAccountAllTradesWebSocketExample.class);
    private static final long DEFAULT_AUTH_TTL_SECONDS = 600L;

    public static void main(String[] args) {
        LighterConfiguration.reset();
        LighterConfiguration configuration = LighterConfiguration.getInstance();
        long accountIndex = args.length > 0 ? Long.parseLong(args[0]) : configuration.getAccountIndex();
        String authToken = getArgOrSystemOrEnv(args, 1, "lighter.auth.token", "LIGHTER_AUTH_TOKEN");
        ILighterWebSocketApi wsApi = new LighterWebSocketApi(configuration.getWebSocketUrl());

        Runtime.getRuntime().addShutdownHook(new Thread(wsApi::disconnectAll));
        try {
            String resolvedAuthToken = resolveAuthToken(authToken, accountIndex, configuration, wsApi);
            wsApi.subscribeAccountAllTrades(accountIndex, resolvedAuthToken,
                    LighterAccountAllTradesWebSocketExample::handleUpdate);
            logger.info("Subscribed to account_all_trades for account_index={} env={} wsUrl={}", accountIndex,
                    configuration.getEnvironment(), configuration.getWebSocketUrl());
            Thread.sleep(600_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            wsApi.disconnectAll();
        }
    }

    private static void handleUpdate(LighterTradesUpdate update) {
        LighterTrade first = update.getFirstTrade();
        logger.info(
                "channel={} type={} trades={} firstTradeId={} market={} side={} price={} size={} timestamp={}",
                update.getChannel(), update.getMessageType(), update.getTrades().size(),
                first == null ? null : first.getId(), first == null ? null : first.getMarketId(),
                first == null ? null : first.getType(), first == null ? null : first.getPrice(),
                first == null ? null : first.getSize(), first == null ? null : first.getTimestamp());
    }

    private static String resolveAuthToken(String authToken, long accountIndex, LighterConfiguration configuration,
            ILighterWebSocketApi wsApi) {
        if (authToken != null && !authToken.isBlank()) {
            return authToken;
        }
        if (!configuration.hasPrivateKeyConfiguration()) {
            throw new IllegalArgumentException(
                    "authToken is required when private key configuration is unavailable. Set arg[1] or LIGHTER_AUTH_TOKEN.");
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        long expiry = timestamp + DEFAULT_AUTH_TTL_SECONDS;
        LighterSignerClient signerClient = new LighterSignerClient(wsApi, new LighterNativeTransactionSigner(configuration));
        return signerClient.createAuthTokenWithExpiry(timestamp, expiry, configuration.getApiKeyIndex(), accountIndex);
    }

    private static String getArgOrSystemOrEnv(String[] args, int index, String systemPropertyKey, String envKey) {
        if (args != null && args.length > index && args[index] != null && !args[index].isBlank()) {
            return args[index];
        }
        String systemProperty = System.getProperty(systemPropertyKey);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }
        String envValue = System.getenv(envKey);
        return (envValue == null || envValue.isBlank()) ? null : envValue;
    }
}
