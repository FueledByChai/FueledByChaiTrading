package com.fueledbychai.datacollector;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;

/**
 * One venue/instrument the collector should record: which {@link Exchange}, the common
 * symbol to resolve on that venue's ticker registry, and the {@link InstrumentType}
 * (e.g. {@code PERPETUAL_FUTURES} for perps, {@code CRYPTO_SPOT} for Binance spot).
 */
public record InstrumentSpec(Exchange exchange, String commonSymbol, InstrumentType instrumentType) {

    /** Parse {@code EXCHANGE:SYMBOL:INSTRUMENT_TYPE}, e.g. {@code BINANCE_SPOT:SOL:CRYPTO_SPOT}. */
    public static InstrumentSpec parse(String token) {
        String[] parts = token.trim().split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Bad instrument spec '" + token + "', expected EXCHANGE:SYMBOL:INSTRUMENT_TYPE");
        }
        return new InstrumentSpec(
                exchangeByName(parts[0].trim()),
                parts[1].trim(),
                InstrumentType.valueOf(parts[2].trim().toUpperCase()));
    }

    private static Exchange exchangeByName(String name) {
        return switch (name.toUpperCase()) {
            case "PARADEX" -> Exchange.PARADEX;
            case "HIBACHI" -> Exchange.HIBACHI;
            case "BINANCE_FUTURES" -> Exchange.BINANCE_FUTURES;
            case "BINANCE_SPOT" -> Exchange.BINANCE_SPOT;
            case "OKX" -> Exchange.OKX;
            default -> throw new IllegalArgumentException("Unknown / unsupported exchange: " + name);
        };
    }
}
