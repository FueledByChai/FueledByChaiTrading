package com.fueledbychai.okx.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import com.fueledbychai.data.ITickerTranslator;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.data.Ticker.Right;
import com.fueledbychai.data.TickerTranslator;

public class OkxTickerTranslator implements ITickerTranslator {

    protected static final DateTimeFormatter COMMON_SYMBOL_OPTION_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    protected static final DateTimeFormatter OKX_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    protected final TickerTranslator delegate = new TickerTranslator();

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        Ticker ticker = delegate.translateTicker(descriptor);
        if (descriptor == null || descriptor.getInstrumentType() == null) {
            return ticker;
        }

        InstrumentType instrumentType = descriptor.getInstrumentType();
        if (instrumentType == InstrumentType.OPTION) {
            applyOptionFields(descriptor, ticker);
        } else if (instrumentType == InstrumentType.FUTURES) {
            applyFutureExpiry(descriptor, ticker);
        }

        return ticker;
    }

    protected void applyOptionFields(InstrumentDescriptor descriptor, Ticker ticker) {
        LocalDate expiry = parseOptionExpiry(descriptor);
        if (expiry != null) {
            ticker.setExpiryYear(expiry.getYear());
            ticker.setExpiryMonth(expiry.getMonthValue());
            ticker.setExpiryDay(expiry.getDayOfMonth());
        }

        String symbol = descriptor.getExchangeSymbol();
        if (symbol == null || symbol.isBlank()) {
            return;
        }

        String[] parts = symbol.trim().toUpperCase(Locale.US).split("-");
        if (parts.length < 5) {
            return;
        }

        try {
            ticker.setStrike(new BigDecimal(parts[3]));
        } catch (NumberFormatException ignored) {
            // Leave strike unset when the symbol is malformed.
        }

        String rightCode = parts[4];
        if ("C".equals(rightCode)) {
            ticker.setRight(Right.CALL);
        } else if ("P".equals(rightCode)) {
            ticker.setRight(Right.PUT);
        }
    }

    protected void applyFutureExpiry(InstrumentDescriptor descriptor, Ticker ticker) {
        LocalDate expiry = parseExchangeSymbolExpiry(descriptor.getExchangeSymbol());
        if (expiry == null) {
            return;
        }
        ticker.setExpiryYear(expiry.getYear());
        ticker.setExpiryMonth(expiry.getMonthValue());
        ticker.setExpiryDay(expiry.getDayOfMonth());
    }

    protected LocalDate parseOptionExpiry(InstrumentDescriptor descriptor) {
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
        if (contractParts.length < 4 || !contractParts[1].matches("\\d{8}")) {
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

        String[] parts = exchangeSymbol.trim().toUpperCase(Locale.US).split("-");
        if (parts.length < 3 || !parts[2].matches("\\d{6}")) {
            return null;
        }

        try {
            return LocalDate.parse(parts[2], OKX_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
