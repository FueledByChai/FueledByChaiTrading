package com.fueledbychai.paradex.example.market.data;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;
import com.fueledbychai.websocket.ProxyConfig;

public class BinanceOptionChainLookupExample {

    private static final Logger logger = LoggerFactory.getLogger(BinanceOptionChainLookupExample.class);
    private static final String DEFAULT_UNDERLYING = "BTC";
    private static final LocalDate DEFAULT_EXPIRY = LocalDate.of(2026, 3, 4);
    private static final ITickerRegistry.OptionRightFilter DEFAULT_RIGHT_FILTER = ITickerRegistry.OptionRightFilter.ALL;

    // private Exchange exchange = Exchange.BINANCE_FUTURES;
    private Exchange exchange = Exchange.DERIBIT;

    public void start(String underlyingSymbol, LocalDate expiry, ITickerRegistry.OptionRightFilter rightFilter) {
        ProxyConfig.getInstance().setRunningLocally(true);

        ITickerRegistry tickerRegistry = TickerRegistryFactory.getInstance(exchange);

        Ticker[] optionChain = tickerRegistry.getOptionChain(underlyingSymbol, expiry.getYear(), expiry.getMonthValue(),
                expiry.getDayOfMonth(), rightFilter);

                optionChain = tickerRegistry.getAllTickersForType(InstrumentType.OPTION);

        logger.info("Retrieved {} Binance option contracts for {} expiring {} ({})", optionChain.length,
                underlyingSymbol.toUpperCase(), expiry, rightFilter);

        if (optionChain.length == 0) {
            logger.warn("No Binance options were returned for {} expiring {}. Try another date or right filter.",
                    underlyingSymbol.toUpperCase(), expiry);
            return;
        }

        for (Ticker ticker : optionChain) {
            logger.info("{} strike={} right={} expiry={}", ticker.getSymbol(), ticker.getStrike(), ticker.getRight(),
                    formatExpiry(ticker));
        }
    }

    protected String formatExpiry(Ticker ticker) {
        if (ticker == null || ticker.getExpiryYear() <= 0 || ticker.getExpiryMonth() <= 0
                || ticker.getExpiryDay() <= 0) {
            return "UNKNOWN";
        }
        return LocalDate.of(ticker.getExpiryYear(), ticker.getExpiryMonth(), ticker.getExpiryDay()).toString();
    }

    public static void main(String[] args) {
        String underlyingSymbol = args.length > 0 ? args[0] : DEFAULT_UNDERLYING;
        LocalDate expiry = args.length > 1 ? LocalDate.parse(args[1]) : DEFAULT_EXPIRY;
        ITickerRegistry.OptionRightFilter rightFilter = args.length > 2
                ? ITickerRegistry.OptionRightFilter.valueOf(args[2].trim().toUpperCase())
                : DEFAULT_RIGHT_FILTER;

        System.setProperty("socksProxyHost", "127.0.0.1");
        System.setProperty("socksProxyPort", "1080");
        System.setProperty("binance.run.proxy", "true");

        new BinanceOptionChainLookupExample().start(underlyingSymbol, expiry, rightFilter);
    }
}
