/**
 * MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy of this software 
and associated documentation files (the "Software"), to deal in the Software without restriction, 
including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, 
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING 
BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, 
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE 
OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.fueledbychai.paradex.example.market.data;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class DeribitOptionMarketDataExample {

    protected static final Logger logger = LoggerFactory.getLogger(DeribitOptionMarketDataExample.class);
    protected static final String DEFAULT_OPTION_SYMBOL = "BTC-1MAR26-68000-C";
    protected static final long RUN_MILLIS = 60_000L;

    public void start(String optionSymbol) throws InterruptedException {
        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.DERIBIT);
        Ticker optionTicker = registry.lookupByBrokerSymbol(InstrumentType.OPTION, optionSymbol);
        if (optionTicker == null) {
            throw new IllegalArgumentException("Unknown Deribit option '" + optionSymbol
                    + "'. Use an active Deribit option instrument name such as BTC-01MAR26-68000-C.");
        }

        QuoteEngine quoteEngine = QuoteEngine.getInstance(Exchange.DERIBIT);
        quoteEngine.startEngine();

        logger.info("Subscribing to Deribit option {}", optionTicker.getSymbol());

        quoteEngine.subscribeLevel1(optionTicker, this::logLevel1);
        quoteEngine.subscribeMarketDepth(optionTicker, this::logLevel2);

        try {
            Thread.sleep(RUN_MILLIS);
        } finally {
            quoteEngine.stopEngine();
        }
    }

    protected void logLevel1(ILevel1Quote quote) {
        logger.info("L1 {} bid={} ask={} mark={} markIv={} delta={}", quote.getTicker().getSymbol(),
                valueOrNull(quote, QuoteType.BID), valueOrNull(quote, QuoteType.ASK),
                valueOrNull(quote, QuoteType.MARK_PRICE), valueOrNull(quote, QuoteType.MARK_IV),
                valueOrNull(quote, QuoteType.DELTA));
    }

    protected void logLevel2(ILevel2Quote quote) {
        logger.info("L2 {} bestBid={}x{} bestAsk={}x{}", quote.getTicker().getSymbol(),
                quote.getOrderBook().getBestBid().getPrice(), quote.getOrderBook().getBestBid().getSize(),
                quote.getOrderBook().getBestAsk().getPrice(), quote.getOrderBook().getBestAsk().getSize());
    }

    protected BigDecimal valueOrNull(ILevel1Quote quote, QuoteType quoteType) {
        if (quote == null || quoteType == null || !quote.containsType(quoteType)) {
            return null;
        }
        return quote.getValue(quoteType);
    }

    public static void main(String[] args) throws Exception {
        String optionSymbol = args.length > 0 ? args[0] : DEFAULT_OPTION_SYMBOL;
        new DeribitOptionMarketDataExample().start(optionSymbol);
    }
}
