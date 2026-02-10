package com.fueledbychai.marketdata.test;

import java.util.Date;
import java.util.Properties;

import com.fueledbychai.marketdata.QuoteEngine;

public class TestQuoteEngine extends QuoteEngine {

    private boolean started;

    @Override
    public String getDataProviderName() {
        return "TestQuoteEngine";
    }

    @Override
    public void startEngine() {
        started = true;
    }

    @Override
    public void startEngine(Properties props) {
        started = true;
    }

    @Override
    public void stopEngine() {
        started = false;
    }

    @Override
    public Date getServerTime() {
        return new Date(0);
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public boolean isConnected() {
        return started;
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        // no-op
    }
}
