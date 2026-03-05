package com.fueledbychai.paradex.example.market.data;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.binancefutures.common.BinanceFuturesTickerRegistry;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesRestApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.websocket.ProxyConfig;

public class BinanceOptionMarketDataExample {

    private static final Logger logger = LoggerFactory.getLogger(BinanceOptionMarketDataExample.class);
    private static final String REQUESTED_OPTION_SYMBOL = "BTC-260304-69000-C";

    public void start() throws Exception {
        ProxyConfig.getInstance().setRunningLocally(true);

        IBinanceFuturesRestApi restApi = ExchangeRestApiFactory.getPublicApi(Exchange.BINANCE_FUTURES,
                IBinanceFuturesRestApi.class);
        ITickerRegistry tickerRegistry = BinanceFuturesTickerRegistry.getInstance(restApi);

        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.OPTION, REQUESTED_OPTION_SYMBOL);

        logger.info("Subscribing to Binance option {}", ticker.getSymbol());

        QuoteEngine quoteEngine = QuoteEngine.getInstance(Exchange.BINANCE_FUTURES);
        quoteEngine.startEngine();
        AtomicBoolean receivedUpdate = new AtomicBoolean(false);

        quoteEngine.subscribeLevel1(ticker, quote -> {
            receivedUpdate.set(true);
            logger.info("Level 1 Quote Update: {}", quote);
        });
        quoteEngine.subscribeMarketDepth(ticker, quote -> {
            receivedUpdate.set(true);
            logger.info("Level 2 Quote Update: {}", quote);
        });

        Thread.sleep(100000L);
        if (!receivedUpdate.get()) {
            logger.warn("No quote updates received for {} in the first 10 seconds. The selected contract may be quiet.",
                    ticker.getSymbol());
        }

        Thread.sleep(50000L);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("socksProxyHost", "127.0.0.1");
        System.setProperty("socksProxyPort", "1080");
        System.setProperty("binance.run.proxy", "true");
        new BinanceOptionMarketDataExample().start();
    }
}
