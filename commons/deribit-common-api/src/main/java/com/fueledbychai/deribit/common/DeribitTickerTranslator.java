package com.fueledbychai.deribit.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import com.fueledbychai.data.ITickerTranslator;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.data.Ticker.Right;
import com.fueledbychai.data.TickerTranslator;

public class DeribitTickerTranslator implements ITickerTranslator {

    protected static final DateTimeFormatter COMMON_SYMBOL_OPTION_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    protected static final DateTimeFormatter DERIBIT_OPTION_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dMMMyy")
            .toFormatter(Locale.US);
    protected final TickerTranslator delegate = new TickerTranslator();

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        Ticker ticker = delegate.translateTicker(descriptor);
        if (descriptor == null || descriptor.getInstrumentType() != InstrumentType.OPTION) {
            return ticker;
        }

        LocalDate expiry = parseExpiry(descriptor);
        if (expiry != null) {
            ticker.setExpiryYear(expiry.getYear());
            ticker.setExpiryMonth(expiry.getMonthValue());
            ticker.setExpiryDay(expiry.getDayOfMonth());
        }

        String symbol = descriptor.getExchangeSymbol();
        if (symbol == null || symbol.isBlank()) {
            return ticker;
        }

        String[] parts = symbol.trim().toUpperCase(Locale.US).split("-");
        if (parts.length != 4) {
            return ticker;
        }

        try {
            ticker.setStrike(new BigDecimal(parts[2]));
        } catch (NumberFormatException ignored) {
            // Leave strike unset when the symbol is malformed.
        }

        if ("C".equals(parts[3])) {
            ticker.setRight(Right.CALL);
        } else if ("P".equals(parts[3])) {
            ticker.setRight(Right.PUT);
        }

        return ticker;
    }

    protected LocalDate parseExpiry(InstrumentDescriptor descriptor) {
        LocalDate expiry = parseCommonSymbolExpiry(descriptor.getCommonSymbol());
        if (expiry != null) {
            return expiry;
        }
        return parseExchangeSymbolExpiry(descriptor.getExchangeSymbol());
    }

    protected LocalDate parseCommonSymbolExpiry(String commonSymbol) {
        if (commonSymbol == null || commonSymbol.isBlank()) {
            return null;
        }

        String normalized = commonSymbol.trim().toUpperCase(Locale.US);
        int slashIndex = normalized.indexOf('/');
        if (slashIndex < 0 || slashIndex + 1 >= normalized.length()) {
            return null;
        }

        String[] contractParts = normalized.substring(slashIndex + 1).split("-");
        if (contractParts.length != 4 || !contractParts[1].matches("\\d{8}")) {
            return null;
        }

        try {
            return LocalDate.parse(contractParts[1], COMMON_SYMBOL_OPTION_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    protected LocalDate parseExchangeSymbolExpiry(String exchangeSymbol) {
        if (exchangeSymbol == null || exchangeSymbol.isBlank()) {
            return null;
        }

        String[] contractParts = exchangeSymbol.trim().toUpperCase(Locale.US).split("-");
        if (contractParts.length != 4) {
            return null;
        }

        try {
            return LocalDate.parse(contractParts[1], DERIBIT_OPTION_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
