package com.fueledbychai.marketdata.binance;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.binance.BinanceConfiguration;
import com.fueledbychai.binance.BinanceTickerRegistry;
import com.fueledbychai.binance.ws.BinanceWebSocketClient;
import com.fueledbychai.binance.ws.BinanceWebSocketClientBuilder;
import com.fueledbychai.binance.ws.partialbook.OrderBookSnapshot;
import com.fueledbychai.binance.ws.partialbook.PartialOrderBookProcessor;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.Level1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;

public class BinanceQuoteEngine extends QuoteEngine {

    protected static Logger logger = LoggerFactory.getLogger(BinanceQuoteEngine.class);

    protected volatile boolean started = false;
    protected boolean threadCompleted = false;

    protected int fundingRateUpdateIntervalSeconds = 5;
    protected ArrayList<String> urlStrings = new ArrayList<>();

    protected String wsUrl;
    protected boolean includeFundingRate = true;

    protected ITickerRegistry tickerRegistry;

    public BinanceQuoteEngine() {
        wsUrl = BinanceConfiguration.getInstance().getWebSocketUrl();
        logger.info("Binance WebSocket URL: {}", wsUrl);
        tickerRegistry = BinanceTickerRegistry.getInstance();
    }

    @Override
    public String getDataProviderName() {
        return "Binance";
    }

    @Override
    public Date getServerTime() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isConnected() {
        return started;
    }

    @Override
    public void startEngine() {
        if (threadCompleted) {
            throw new IllegalStateException("Quote Engine was already stopped");
        }
        started = true;

    }

    @Override
    public void startEngine(Properties props) {
        startEngine();
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public void stopEngine() {
        started = false;
        // stopFundingRateUpdates();
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.error("useDelayedData() Not supported for binance market data");
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        if (!super.level1ListenerMap.containsKey(ticker)) {
            startPartialOrderBookClient(ticker);
            // startVolumeAndFundingWSClient(ticker);
        }
        super.subscribeLevel1(ticker, listener);
    }

    @Override
    public void unsubscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        super.unsubscribeLevel1(ticker, listener);
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        throw new UnsupportedOperationException("Market depth not supported for Binance");
    }

    @Override
    public void unsubscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        // TODO Auto-generated method stub
        super.unsubscribeMarketDepth(ticker, listener);
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        throw new UnsupportedOperationException("Order flow not supported for Binance");
        // if (!super.orderFlowListenerMap.containsKey(ticker)) {
        // // No order flow client yet for this ticker, so create one.
        // startTradesWSClient(ticker);
        // }
        // super.subscribeOrderFlow(ticker, listener);
    }

    @Override
    public void unsubscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        super.unsubscribeOrderFlow(ticker, listener);
    }

    public void onBBOUpdate(Ticker ticker, BigDecimal bestBid, BigDecimal bidSize, BigDecimal bestAsk,
            BigDecimal askSize, ZonedDateTime timeStamp) {
        Level1Quote quote = new Level1Quote(ticker, timeStamp);
        if (bestBid != null) {
            quote.addQuote(QuoteType.BID, bestBid);
            quote.addQuote(QuoteType.BID_SIZE, bidSize);
        }
        if (bestAsk != null) {
            quote.addQuote(QuoteType.ASK, bestAsk);
            quote.addQuote(QuoteType.ASK_SIZE, askSize);
        }
        super.fireLevel1Quote(quote);
    }

    protected void startPartialOrderBookClient(final Ticker ticker) {
        try {
            logger.info("Starting Partial Order Book WebSocket client");
            PartialOrderBookProcessor processor = new PartialOrderBookProcessor(() -> {
                logger.info("Partial Order Book WebSocket closed, trying to restart...");
                startPartialOrderBookClient(ticker);
            });
            processor.addEventListener((OrderBookSnapshot obs) -> {
                ZonedDateTime eventTime = ZonedDateTime.now(ZoneId.of("UTC"));
                onBBOUpdate(ticker, obs.getBestBid().getPriceAsDecimal(), obs.getBestBid().getQuantityAsDecimal(),
                        obs.getBestAsk().getPriceAsDecimal(), obs.getBestAsk().getQuantityAsDecimal(), eventTime);
            });

            BinanceWebSocketClient partialBookDepthClient = BinanceWebSocketClientBuilder.buildPartialBookDepth(wsUrl,
                    ticker, processor);
            partialBookDepthClient.connect();

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

}
