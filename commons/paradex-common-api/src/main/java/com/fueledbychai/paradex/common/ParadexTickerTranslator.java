package com.fueledbychai.paradex.common;

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

/**
 * Translates {@link InstrumentDescriptor}s produced by the Paradex REST API
 * into {@link Ticker}s, populating option-specific fields (strike, expiry,
 * right) for OPTION instruments.
 *
 * <p>
 * Paradex option common symbols are produced by
 * {@code ParadexRestApi.parseInstrumentDescriptors} in the form
 * {@code BASE/QUOTE-YYYYMMDD-STRIKE-RIGHT}, e.g. {@code BTC/USD-20260626-67000-C}.
 * The exchange symbol uses Paradex's native format, e.g.
 * {@code BTC-USD-26JUN26-67000-C}.
 */
public class ParadexTickerTranslator implements ITickerTranslator {

    protected static final DateTimeFormatter COMMON_SYMBOL_OPTION_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    protected final TickerTranslator delegate = new TickerTranslator();

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        Ticker ticker = delegate.translateTicker(descriptor);
        if (descriptor == null || descriptor.getInstrumentType() != InstrumentType.OPTION) {
            return ticker;
        }

        String commonSymbol = descriptor.getCommonSymbol();
        if (commonSymbol == null || commonSymbol.isBlank()) {
            return ticker;
        }

        String normalized = commonSymbol.trim().toUpperCase(Locale.US);
        int slashIndex = normalized.indexOf('/');
        if (slashIndex < 0 || slashIndex + 1 >= normalized.length()) {
            return ticker;
        }

        String[] contractParts = normalized.substring(slashIndex + 1).split("-");
        // Expected: QUOTE-YYYYMMDD-STRIKE-RIGHT, length 4
        if (contractParts.length != 4) {
            return ticker;
        }

        LocalDate expiry = parseExpiry(contractParts[1]);
        if (expiry != null) {
            ticker.setExpiryYear(expiry.getYear());
            ticker.setExpiryMonth(expiry.getMonthValue());
            ticker.setExpiryDay(expiry.getDayOfMonth());
        }

        try {
            ticker.setStrike(new BigDecimal(contractParts[2]));
        } catch (NumberFormatException ignored) {
            // Leave strike unset when malformed.
        }

        if ("C".equals(contractParts[3])) {
            ticker.setRight(Right.CALL);
        } else if ("P".equals(contractParts[3])) {
            ticker.setRight(Right.PUT);
        }

        return ticker;
    }

    protected LocalDate parseExpiry(String expiryToken) {
        if (expiryToken == null || expiryToken.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(expiryToken, COMMON_SYMBOL_OPTION_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
