package com.fueledbychai.paradex.example.market.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class BybitTickerLookupExample {

    protected static final Logger logger = LoggerFactory.getLogger(BybitTickerLookupExample.class);

    public void start() {
        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.BYBIT);

        Ticker[] spot = registry.getAllTickersForType(InstrumentType.CRYPTO_SPOT);
        Ticker[] perp = registry.getAllTickersForType(InstrumentType.PERPETUAL_FUTURES);
        Ticker[] futures = registry.getAllTickersForType(InstrumentType.FUTURES);
        Ticker[] options = registry.getAllTickersForType(InstrumentType.OPTION);

        logger.info("Bybit spot ticker count: {}", spot.length);
        logger.info("Bybit perpetual ticker count: {}", perp.length);
        logger.info("Bybit futures ticker count: {}", futures.length);
        logger.info("Bybit options ticker count: {}", options.length);

        Ticker btcSpot = registry.lookupByBrokerSymbol(InstrumentType.CRYPTO_SPOT, "BTCUSDT");
        Ticker btcPerp = registry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC/USDT-PERP");

        logger.info("Lookup BTCUSDT spot: {}", btcSpot);
        logger.info("Lookup BTC/USDT-PERP: {}", btcPerp);
    }

    public static void main(String[] args) {
        System.setProperty("fueledbychai.run.proxy", "true");
        System.setProperty("fueledbychai.proxy.host", "127.0.0.1");
        System.setProperty("fueledbychai.proxy.port", "1080");
        new BybitTickerLookupExample().start();
    }
}
