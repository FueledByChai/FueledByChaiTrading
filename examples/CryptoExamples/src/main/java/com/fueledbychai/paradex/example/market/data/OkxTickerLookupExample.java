package com.fueledbychai.paradex.example.market.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class OkxTickerLookupExample {

    protected static final Logger logger = LoggerFactory.getLogger(OkxTickerLookupExample.class);

    public void start() {
        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.OKX);

        Ticker[] spot = registry.getAllTickersForType(InstrumentType.CRYPTO_SPOT);
        Ticker[] perp = registry.getAllTickersForType(InstrumentType.PERPETUAL_FUTURES);
        Ticker[] futures = registry.getAllTickersForType(InstrumentType.FUTURES);
        Ticker[] options = registry.getAllTickersForType(InstrumentType.OPTION);

        logger.info("OKX spot ticker count: {}", spot.length);
        logger.info("OKX perpetual ticker count: {}", perp.length);
        logger.info("OKX futures ticker count: {}", futures.length);
        logger.info("OKX options ticker count: {}", options.length);

        Ticker btcSpot = registry.lookupByBrokerSymbol(InstrumentType.CRYPTO_SPOT, "BTC-USDT");
        Ticker btcPerp = registry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC/USDT-PERP");

        logger.info("Lookup BTC-USDT spot: {}", btcSpot);
        logger.info("Lookup BTC/USDT-PERP: {}", btcPerp);
    }

    public static void main(String[] args) {
        new OkxTickerLookupExample().start();
    }
}
