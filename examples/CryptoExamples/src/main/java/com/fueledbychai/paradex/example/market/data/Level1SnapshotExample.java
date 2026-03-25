package com.fueledbychai.paradex.example.market.data;

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

    public static void main(String[] args) {
        Exchange exchange = args.length > 0 ? Exchange.getExchangeFromString(args[0].trim().toUpperCase()) : Exchange.ASTER;
        String symbol = args.length > 1 ? args[1] : "BTCUSDT";
        InstrumentType instrumentType = args.length > 2
                ? InstrumentType.valueOf(args[2].trim().toUpperCase())
                : InstrumentType.PERPETUAL_FUTURES;

        logger.info("Requesting L1 snapshot from {} for {} ({})", exchange, symbol, instrumentType);

        Ticker ticker = resolveTicker(exchange, symbol, instrumentType);
        QuoteEngine quoteEngine = QuoteEngine.getInstance(exchange);

        ILevel1Quote quote = quoteEngine.requestLevel1Snapshot(ticker);

        logger.info("=== L1 Snapshot for {} on {} ===", ticker.getSymbol(), exchange);
        logField(quote, "Bid", QuoteType.BID);
        logField(quote, "Bid Size", QuoteType.BID_SIZE);
        logField(quote, "Ask", QuoteType.ASK);
        logField(quote, "Ask Size", QuoteType.ASK_SIZE);
        logField(quote, "Mark Price", QuoteType.MARK_PRICE);
        logField(quote, "Underlying", QuoteType.UNDERLYING_PRICE);
        logger.info("Timestamp: {}", quote.getTimeStamp());
    }

    private static Ticker resolveTicker(Exchange exchange, String symbol, InstrumentType instrumentType) {
        ITickerRegistry registry = TickerRegistryFactory.getInstance(exchange);
        Ticker ticker = registry.lookupByBrokerSymbol(instrumentType, symbol);
        if (ticker == null) {
            ticker = registry.lookupByCommonSymbol(instrumentType, symbol);
        }
        if (ticker == null) {
            ticker = new Ticker(symbol)
                    .setExchange(exchange)
                    .setInstrumentType(instrumentType);
        }
        return ticker;
    }

    private static void logField(ILevel1Quote quote, String label, QuoteType type) {
        if (quote.containsType(type)) {
            logger.info("  {}: {}", label, quote.getValue(type));
        }
    }
}
