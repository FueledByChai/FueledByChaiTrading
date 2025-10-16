package com.fueledbychai.marketdata.hyperliquid;

import java.math.BigDecimal;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.OrderBook;

public class HyperliquidOrderBook extends OrderBook {

    public HyperliquidOrderBook(Ticker ticker) {
        super(ticker);
    }

    public HyperliquidOrderBook(Ticker ticker, BigDecimal tickSize) {
        super(ticker, tickSize);
    }

}
