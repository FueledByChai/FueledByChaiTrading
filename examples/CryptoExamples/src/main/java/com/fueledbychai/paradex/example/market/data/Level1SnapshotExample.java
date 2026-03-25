package com.fueledbychai.paradex.example.market.data;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

/**
 * Demonstrates the requestLevel1Snapshot() feature which makes a one-shot REST
 * call to get the current BBO without subscribing to streaming data.
 *
 * Usage: Level1SnapshotExample [exchange] [symbol] [instrumentType]
 *
 * Examples:
 *   Level1SnapshotExample ASTER BTCUSDT PERPETUAL_FUTURES
 *   Level1SnapshotExample PARADEX ETH-USD-PERP PERPETUAL_FUTURES
 *   Level1SnapshotExample LIGHTER BTC/USDC PERPETUAL_FUTURES
 *   Level1SnapshotExample BINANCE_FUTURES ETHUSDT PERPETUAL_FUTURES
 *   Level1SnapshotExample OKX BTC-USDT-SWAP PERPETUAL_FUTURES
 *   Level1SnapshotExample DERIBIT BTC-PERPETUAL PERPETUAL_FUTURES
 *   Level1SnapshotExample BYBIT BTCUSDT PERPETUAL_FUTURES
 *   Level1SnapshotExample HYPERLIQUID BTC PERPETUAL_FUTURES
 *   Level1SnapshotExample BINANCE_SPOT BTCUSDT CRYPTO_SPOT
 */
public class Level1SnapshotExample {

    private static final Logger logger = LoggerFactory.getLogger(Level1SnapshotExample.class);

    private static final List<Exchange> EXCHANGES = List.of(
            Exchange.PARADEX, Exchange.HYPERLIQUID, Exchange.LIGHTER, Exchange.DRIFT, Exchange.ASTER,
            Exchange.BINANCE_FUTURES, Exchange.BINANCE_SPOT, Exchange.OKX, Exchange.BYBIT, Exchange.DERIBIT);

    public static void main(String[] args) {
        System.setProperty("fueledbychai.run.proxy", "true");
        String symbol = args.length > 0 ? args[0] : "BTC";

        record Result(Exchange exchange, String ticker, ILevel1Quote quote, String error) {}
        List<Result> results = new java.util.ArrayList<>();

        for (Exchange exchange : EXCHANGES) {
            InstrumentType instrumentType = exchange == Exchange.BINANCE_SPOT
                    ? InstrumentType.CRYPTO_SPOT : InstrumentType.PERPETUAL_FUTURES;

            logger.info("Requesting L1 snapshot from {} for {} ({})", exchange, symbol, instrumentType);
            try {
                Ticker ticker = resolveTicker(exchange, symbol, instrumentType);
                QuoteEngine quoteEngine = QuoteEngine.getInstance(exchange);

                ILevel1Quote quote = quoteEngine.requestLevel1Snapshot(ticker);
                results.add(new Result(exchange, ticker.getSymbol(), quote, null));

                logger.info("=== L1 Snapshot for {} on {} ===", ticker.getSymbol(), exchange);
                logField(quote, "Bid", QuoteType.BID);
                logField(quote, "Bid Size", QuoteType.BID_SIZE);
                logField(quote, "Ask", QuoteType.ASK);
                logField(quote, "Ask Size", QuoteType.ASK_SIZE);
                logField(quote, "Mark Price", QuoteType.MARK_PRICE);
                logField(quote, "Underlying", QuoteType.UNDERLYING_PRICE);
                logger.info("Timestamp: {}", quote.getTimeStamp());
            } catch (Exception e) {
                results.add(new Result(exchange, null, null, e.getMessage()));
                logger.error("Failed to get L1 snapshot from {} for {}: {}", exchange, symbol, e.getMessage(), e);
            }
        }

        System.out.println("\n========== L1 SNAPSHOT SUMMARY ==========");
        System.out.printf("%-20s %-15s %12s %12s %12s %12s%n",
                "EXCHANGE", "TICKER", "BID", "ASK", "MARK", "TIMESTAMP");
        System.out.println("-".repeat(90));
        for (Result r : results) {
            if (r.error() != null) {
                System.out.printf("%-20s %-15s  ERROR: %s%n", r.exchange().getExchangeName(), "", r.error());
            } else {
                ILevel1Quote q = r.quote();
                System.out.printf("%-20s %-15s %12s %12s %12s %12s%n",
                        r.exchange().getExchangeName(),
                        r.ticker(),
                        q.containsType(QuoteType.BID) ? q.getValue(QuoteType.BID) : "-",
                        q.containsType(QuoteType.ASK) ? q.getValue(QuoteType.ASK) : "-",
                        q.containsType(QuoteType.MARK_PRICE) ? q.getValue(QuoteType.MARK_PRICE) : "-",
                        q.getTimeStamp() != null ? q.getTimeStamp() : "-");
            }
        }
        System.out.println("=========================================");
    }

    private static Ticker resolveTicker(Exchange exchange, String symbol, InstrumentType instrumentType) {
        ITickerRegistry registry = TickerRegistryFactory.getInstance(exchange);
        Ticker ticker = registry.lookupByBrokerSymbol(instrumentType, symbol);
        if (ticker == null) {
            ticker = registry.lookupByCommonSymbol(instrumentType, symbol);
        }

        if( ticker == null) {
            throw new IllegalArgumentException("Ticker not found for exchange=" + exchange + ", symbol=" + symbol);
        }

        return ticker;
    }

    private static void logField(ILevel1Quote quote, String label, QuoteType type) {
        if (quote.containsType(type)) {
            logger.info("  {}: {}", label, quote.getValue(type));
        }
    }
}
