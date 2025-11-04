package com.fueledbychai.marketdata.hyperliquid;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.binance.BinanceConfiguration;
import com.fueledbychai.binance.BinanceTickerRegistry;
import com.fueledbychai.binance.ws.partialbook.OrderBookSnapshot;
import com.fueledbychai.binance.ws.partialbook.OrderBookSnapshot.DepthUpdateData;
import com.fueledbychai.binance.ws.partialbook.PartialOrderBookProcessor;
import com.fueledbychai.binance.ws.partialbook.PriceLevel;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.Level1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.Util;

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

    protected void startPartialOrderBookClient(Ticker ticker) {
        try {
            logger.info("Starting Partial Order Book WebSocket client");
            PartialOrderBookProcessor processor = new PartialOrderBookProcessor(() -> {
                logger.info("Partial Order Book WebSocket closed, trying to restart...");
                startPartialOrderBookClient(ticker);
            });
            processor.addEventListener((OrderBookSnapshot obs) -> {
                DepthUpdateData data = obs.getData();
                ZonedDateTime eventTime = Util.convertEpochToZonedDateTime(data.getEventTime());
                String symbol = data.getSymbol();
                Ticker reqTicker = tickerRegistry.lookupByBrokerSymbol(symbol);
                PriceLevel bid = data.getBids().get(0);
                PriceLevel ask = data.getAsks().get(0);
                onBBOUpdate(reqTicker, new BigDecimal(bid.getPrice()), new BigDecimal(bid.getQuantity()),
                        new BigDecimal(ask.getPrice()), new BigDecimal(ask.getQuantity()), eventTime);
            });

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

}
