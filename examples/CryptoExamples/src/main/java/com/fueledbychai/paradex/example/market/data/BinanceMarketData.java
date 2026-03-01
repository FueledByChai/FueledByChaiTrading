package com.fueledbychai.paradex.example.market.data;

import com.fueledbychai.binance.BinanceTickerRegistry;
import com.fueledbychai.binancefutures.common.BinanceFuturesTickerRegistry;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.websocket.ProxyConfig;

public class BinanceMarketData {

    // InstrumentType instrumentType = InstrumentType.CRYPTO_SPOT;
    // Exchange exchange = Exchange.BINANCE_SPOT;

    InstrumentType instrumentType = InstrumentType.PERPETUAL_FUTURES;
    Exchange exchange = Exchange.BINANCE_FUTURES;

    public void start() throws Exception {
        ProxyConfig.getInstance().setRunningLocally(true);
        // ITickerRegistry tickerRegistry = BinanceTickerRegistry.getInstance();
        ITickerRegistry tickerRegistry = BinanceFuturesTickerRegistry.getInstance();
        Ticker ticker = tickerRegistry.lookupByCommonSymbol(instrumentType, "BTC/USDC");

        QuoteEngine quoteEngine = QuoteEngine.getInstance(exchange);
        quoteEngine.startEngine();

        quoteEngine.subscribeLevel1(ticker, (quote) -> {
            System.out.println("Level 1 Quote Update: " + quote);
        });

        Thread.sleep(50000);

        // Your implementation to start market data retrieval from Binance
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("socksProxyHost", "127.0.0.1");
        System.setProperty("socksProxyPort", "1080");
        System.setProperty("binance.run.proxy", "true");
        BinanceMarketData example = new BinanceMarketData();
        example.start();
    }
}
