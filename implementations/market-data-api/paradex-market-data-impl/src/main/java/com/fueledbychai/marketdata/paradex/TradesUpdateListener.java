package com.fueledbychai.marketdata.paradex;

public interface TradesUpdateListener {

    void newTrade(long createdAtTimestamp, String market, String price, String side, String size);
}
