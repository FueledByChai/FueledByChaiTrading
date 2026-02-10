package com.fueledbychai.lighter.common.api.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.ws.LighterMarketStats;
import com.fueledbychai.lighter.common.api.ws.LighterMarketStatsUpdate;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

public class LighterMarketStatsWebSocketExample {

    private static final Logger logger = LoggerFactory.getLogger(LighterMarketStatsWebSocketExample.class);

    public static void main(String[] args) {
        int marketId = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        ILighterWebSocketApi wsApi = ExchangeWebSocketApiFactory.getApi(Exchange.LIGHTER, ILighterWebSocketApi.class);
        wsApi.subscribeMarketStats(marketId, LighterMarketStatsWebSocketExample::handleUpdate);

        Runtime.getRuntime().addShutdownHook(new Thread(wsApi::disconnectAll));

        logger.info("Subscribed to Lighter market stats for market_id={}", marketId);
        try {
            Thread.sleep(60_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            wsApi.disconnectAll();
        }
    }

    private static void handleUpdate(LighterMarketStatsUpdate update) {
        for (String marketId : update.getMarketStatsByMarketId().keySet()) {
            LighterMarketStats stats = update.getMarketStatsByMarketId().get(marketId);
            logger.info("market={} markPrice={} lastPrice={} fundingRate={} dailyQuoteVolume={}", marketId,
                    stats.getMarkPrice(), stats.getLastPrice(), stats.getFundingRate(), stats.getDailyQuoteVolume());
        }
    }
}
