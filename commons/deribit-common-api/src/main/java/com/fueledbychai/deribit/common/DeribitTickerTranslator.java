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

    protected static final DateTimeFormatter DERIBIT_OPTION_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("ddMMMyy")
            .toFormatter(Locale.US);
    protected final TickerTranslator delegate = new TickerTranslator();

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        Ticker ticker = delegate.translateTicker(descriptor);
        if (descriptor == null || descriptor.getInstrumentType() != InstrumentType.OPTION) {
            return ticker;
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
            LocalDate expiry = LocalDate.parse(parts[1], DERIBIT_OPTION_DATE);
            ticker.setExpiryYear(expiry.getYear());
            ticker.setExpiryMonth(expiry.getMonthValue());
            ticker.setExpiryDay(expiry.getDayOfMonth());
        } catch (DateTimeParseException ignored) {
            // Keep the base ticker when the option date cannot be parsed.
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
}
