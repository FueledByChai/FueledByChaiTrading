package com.fueledbychai.lighter.common.api.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.LighterConfiguration;
import com.fueledbychai.lighter.common.api.ws.LighterOrderBookLevel;
import com.fueledbychai.lighter.common.api.ws.LighterOrderBookUpdate;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

public class LighterOrderBookWebSocketExample {

    private static final Logger logger = LoggerFactory.getLogger(LighterOrderBookWebSocketExample.class);

    public static void main(String[] args) {
        int marketId = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        boolean runProxy = parseBoolean(getArgOrSystemOrEnv(args, 1, LighterConfiguration.LIGHTER_RUN_PROXY,
                "LIGHTER_RUN_PROXY"), false);
        String proxyHost = getArgOrSystemOrEnv(args, 2, LighterConfiguration.LIGHTER_PROXY_HOST, "LIGHTER_PROXY_HOST");
        String proxyPort = getArgOrSystemOrEnv(args, 3, LighterConfiguration.LIGHTER_PROXY_PORT, "LIGHTER_PROXY_PORT");
        String websocketReadonly = getArgOrSystemOrEnv(args, 4, LighterConfiguration.LIGHTER_WEBSOCKET_READONLY,
                "LIGHTER_WEBSOCKET_READONLY");

        configureRuntimeOverrides(runProxy, proxyHost, proxyPort, websocketReadonly);
        LighterConfiguration.reset();
        LighterConfiguration configuration = LighterConfiguration.getInstance();

        ILighterWebSocketApi wsApi = ExchangeWebSocketApiFactory.getApi(Exchange.LIGHTER, ILighterWebSocketApi.class);
        wsApi.subscribeOrderBook(marketId, LighterOrderBookWebSocketExample::handleUpdate);

        Runtime.getRuntime().addShutdownHook(new Thread(wsApi::disconnectAll));

        logger.info("Subscribed to Lighter order book for market_id={} proxyEnabled={} proxy={}:{} readonly={}",
                marketId, configuration.isProxyEnabled(), configuration.getProxyHost(), configuration.getProxyPort(),
                configuration.isWebSocketReadonlyEnabled());
        try {
            Thread.sleep(60_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            wsApi.disconnectAll();
        }
    }

    private static void handleUpdate(LighterOrderBookUpdate update) {
        LighterOrderBookLevel bestBid = update.getBestBid();
        LighterOrderBookLevel bestAsk = update.getBestAsk();

        logger.info(
                "market={} bestBidPrice={} bestBidSize={} bestAskPrice={} bestAskSize={} nonce={} beginNonce={} timestamp={} levels(bid={},ask={})",
                update.getMarketId(), bestBid == null ? null : bestBid.getPrice(), bestBid == null ? null : bestBid.getSize(),
                bestAsk == null ? null : bestAsk.getPrice(), bestAsk == null ? null : bestAsk.getSize(),
                update.getNonce(), update.getBeginNonce(), update.getTimestamp(), update.getBids().size(),
                update.getAsks().size());
    }

    private static void configureRuntimeOverrides(boolean runProxy, String proxyHost, String proxyPort,
            String websocketReadonly) {
        System.setProperty(LighterConfiguration.LIGHTER_RUN_PROXY, String.valueOf(runProxy));
        if (proxyHost != null && !proxyHost.isBlank()) {
            System.setProperty(LighterConfiguration.LIGHTER_PROXY_HOST, proxyHost);
        }
        if (proxyPort != null && !proxyPort.isBlank()) {
            System.setProperty(LighterConfiguration.LIGHTER_PROXY_PORT, proxyPort);
        }

        if (websocketReadonly != null && !websocketReadonly.isBlank()) {
            System.setProperty(LighterConfiguration.LIGHTER_WEBSOCKET_READONLY, websocketReadonly);
            return;
        }

        if (runProxy) {
            System.setProperty(LighterConfiguration.LIGHTER_WEBSOCKET_READONLY, "false");
        }
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

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
