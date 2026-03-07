package com.fueledbychai.bybit.common;

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

public class BybitTickerTranslator implements ITickerTranslator {

    protected static final DateTimeFormatter COMMON_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    protected static final DateTimeFormatter BYBIT_DATE = DateTimeFormatter.ofPattern("ddMMMyy", Locale.US);
    protected final TickerTranslator delegate = new TickerTranslator();

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        Ticker ticker = delegate.translateTicker(descriptor);
        if (descriptor == null || descriptor.getInstrumentType() == null) {
            return ticker;
        }

        InstrumentType instrumentType = descriptor.getInstrumentType();
        if (instrumentType == InstrumentType.FUTURES) {
            applyFutureExpiry(descriptor, ticker);
        } else if (instrumentType == InstrumentType.OPTION) {
            applyOptionFields(descriptor, ticker);
        }

        return ticker;
    }

    protected void applyFutureExpiry(InstrumentDescriptor descriptor, Ticker ticker) {
        LocalDate expiry = parseExpiry(descriptor);
        if (expiry == null) {
            return;
        }
        ticker.setExpiryYear(expiry.getYear());
        ticker.setExpiryMonth(expiry.getMonthValue());
        ticker.setExpiryDay(expiry.getDayOfMonth());
    }

    protected void applyOptionFields(InstrumentDescriptor descriptor, Ticker ticker) {
        LocalDate expiry = parseExpiry(descriptor);
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
        if (parts.length < 4) {
            return;
        }

        try {
            ticker.setStrike(new BigDecimal(parts[2]));
        } catch (NumberFormatException ignored) {
            // Ignore malformed strike.
        }

        String optionRight = parts[3];
        if ("C".equals(optionRight)) {
            ticker.setRight(Right.CALL);
        } else if ("P".equals(optionRight)) {
            ticker.setRight(Right.PUT);
        }
    }

    protected LocalDate parseExpiry(InstrumentDescriptor descriptor) {
        LocalDate expiry = parseCommonSymbolExpiry(descriptor == null ? null : descriptor.getCommonSymbol());
        if (expiry != null) {
            return expiry;
        }
        return parseExchangeSymbolExpiry(descriptor == null ? null : descriptor.getExchangeSymbol());
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
        if (contractParts.length < 2 || !contractParts[1].matches("\\d{8}")) {
            return null;
        }

        try {
            return LocalDate.parse(contractParts[1], COMMON_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    protected LocalDate parseExchangeSymbolExpiry(String exchangeSymbol) {
        if (exchangeSymbol == null || exchangeSymbol.isBlank()) {
            return null;
        }

        String[] parts = exchangeSymbol.trim().toUpperCase(Locale.US).split("-");
        if (parts.length < 2 || !parts[1].matches("\\d{2}[A-Z]{3}\\d{2}")) {
            return null;
        }

        try {
            return LocalDate.parse(parts[1], BYBIT_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
