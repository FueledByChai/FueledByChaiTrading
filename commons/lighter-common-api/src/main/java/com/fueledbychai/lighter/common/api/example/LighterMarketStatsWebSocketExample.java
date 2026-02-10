package com.fueledbychai.lighter.common.api.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.LighterConfiguration;
import com.fueledbychai.lighter.common.api.ws.LighterMarketStats;
import com.fueledbychai.lighter.common.api.ws.LighterMarketStatsUpdate;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

public class LighterMarketStatsWebSocketExample {

    private static final Logger logger = LoggerFactory.getLogger(LighterMarketStatsWebSocketExample.class);

    public static void main(String[] args) {
        int marketId = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        System.setProperty(LighterConfiguration.LIGHTER_RUN_PROXY, String.valueOf("true"));

        LighterConfiguration.reset();
        LighterConfiguration configuration = LighterConfiguration.getInstance();

        ILighterWebSocketApi wsApi = ExchangeWebSocketApiFactory.getApi(Exchange.LIGHTER, ILighterWebSocketApi.class);
        wsApi.subscribeMarketStats(marketId, LighterMarketStatsWebSocketExample::handleUpdate);

        Runtime.getRuntime().addShutdownHook(new Thread(wsApi::disconnectAll));

        logger.info("Subscribed to Lighter market stats for market_id={} proxyEnabled={} proxy={}:{} readonly={}",
                marketId, configuration.isProxyEnabled(), configuration.getProxyHost(), configuration.getProxyPort(),
                configuration.isWebSocketReadonlyEnabled());
        try {
            Thread.sleep(180_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            wsApi.disconnectAll();
        }
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
            // Typical usage through local SOCKS tunnel should not require readonly mode.
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

    private static void handleUpdate(LighterMarketStatsUpdate update) {
        for (String marketId : update.getMarketStatsByMarketId().keySet()) {
            LighterMarketStats stats = update.getMarketStatsByMarketId().get(marketId);
            logger.info(
                    "market={} markPrice={} lastPrice={} fundingCurrent={} fundingLast={} fundingTs={} dailyBaseUnits={} dailyQuoteNotional={}",
                    marketId, stats.getMarkPrice(), stats.getLastPrice(), stats.getCurrentFundingRate(),
                    stats.getLastFundingRate(), stats.getFundingTimestamp(), stats.getDailyBaseVolumeUnits(),
                    stats.getDailyQuoteVolumeNotional());
        }
    }
}
