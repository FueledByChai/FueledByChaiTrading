package com.fueledbychai.marketdata;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.fueledbychai.data.Ticker;

public interface OrderBookUpdateListener {

    void bestBidUpdated(Ticker ticker, BigDecimal bestBid, Double bidSize, ZonedDateTime timeStamp);

    void bestAskUpdated(Ticker ticker, BigDecimal bestAsk, Double askSize, ZonedDateTime timeStamp);

    void orderBookImbalanceUpdated(Ticker ticker, BigDecimal imbalance, ZonedDateTime timeStamp);

    void orderBookUpdated(Ticker ticker, IOrderBook book, ZonedDateTime timeStamp);
}
