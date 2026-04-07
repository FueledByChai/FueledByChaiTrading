package com.fueledbychai.paradex.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class ParadexTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static final DateTimeFormatter COMMON_SYMBOL_OPTION_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    protected static final DateTimeFormatter PARADEX_OPTION_DATE = DateTimeFormatter.ofPattern("dMMMyy", Locale.US);

    protected static ITickerRegistry instace;
    protected IParadexRestApi restApi = ExchangeRestApiFactory.getPublicApi(Exchange.PARADEX, IParadexRestApi.class);

    public static ITickerRegistry getInstance() {
        if (instace == null) {
            instace = new ParadexTickerRegistry();
        }
        return instace;
    }

    protected ParadexTickerRegistry() {
        super(new ParadexTickerTranslator());
        initialize();
    }

    protected void initialize() {
        try {
            registerDescriptors(restApi.getAllInstrumentsForTypes(new InstrumentType[] {
                    InstrumentType.PERPETUAL_FUTURES, InstrumentType.CRYPTO_SPOT, InstrumentType.OPTION }));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ParadexTickerRegistry", e);
        }
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES || instrumentType == InstrumentType.CRYPTO_SPOT
                || instrumentType == InstrumentType.OPTION;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }

        if (instrumentType == InstrumentType.OPTION) {
            return optionCommonSymbolToExchangeSymbol(commonSymbol);
        }

        // common symbol is like BTC/USDT, exchange symbol is BTC-USD-PERP, if its
        // BTC/USDC or BTC/USDT, then common symbol is BTC-USD-PERP
        if (commonSymbol.endsWith("/USDC")) {
            commonSymbol = commonSymbol.substring(0, commonSymbol.length() - 5) + "/USD";
        } else if (commonSymbol.endsWith("/USDT")) {
            commonSymbol = commonSymbol.substring(0, commonSymbol.length() - 5) + "/USD";
        }
        String exchangeSymbol = commonSymbol.replace("/", "-");
        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            exchangeSymbol += "-PERP";
        }
        return exchangeSymbol;
    }

    /**
     * Converts a Paradex option common-symbol-ish input into the canonical
     * Paradex exchange symbol {@code BASE-QUOTE-DDMonYY-STRIKE-RIGHT} (e.g.
     * {@code BTC-USD-26JUN26-67000-C}).
     *
     * <p>Two input forms are recognised:
     * <ul>
     *   <li>The structured common-symbol form produced by
     *       {@code ParadexRestApi.parseInstrumentDescriptors} for OPTION
     *       instruments: {@code BASE/QUOTE-YYYYMMDD-STRIKE-RIGHT} (e.g.
     *       {@code BTC/USD-20260626-67000-C}).</li>
     *   <li>A slash-separated mirror of the exchange symbol that some upstream
     *       services use because '/' is more URL-/path-friendly:
     *       {@code BASE/QUOTE/DDMonYY/STRIKE/RIGHT} (e.g.
     *       {@code BTC/USD/26JUN26/67000/C}). This gets normalised by simply
     *       replacing every '/' with '-'.</li>
     * </ul>
     * If the input is already in the exchange-symbol form (or is otherwise
     * unparseable) the trimmed/upper-cased value is returned unchanged so the
     * caller can fall through to a broker-symbol lookup.
     */
    protected String optionCommonSymbolToExchangeSymbol(String commonSymbol) {
        String trimmed = commonSymbol.trim().toUpperCase(Locale.US);
        int slashIndex = trimmed.indexOf('/');
        if (slashIndex < 0 || slashIndex + 1 >= trimmed.length()) {
            return trimmed;
        }

        // Slash-separated exchange symbol form (e.g. BTC/USD/26JUN26/67000/C):
        // five segments separated by '/', with the third segment looking like
        // a Paradex DDMonYY expiry token. Normalise by replacing '/' with '-'.
        String[] slashParts = trimmed.split("/");
        if (slashParts.length == 5 && slashParts[2].matches("\\d{1,2}[A-Z]{3}\\d{2}")) {
            return trimmed.replace('/', '-');
        }

        // Structured common-symbol form (e.g. BTC/USD-20260626-67000-C):
        // BASE / QUOTE - YYYYMMDD - STRIKE - RIGHT
        String base = trimmed.substring(0, slashIndex);
        String[] contractParts = trimmed.substring(slashIndex + 1).split("-");
        if (contractParts.length != 4) {
            return trimmed;
        }

        String quote = contractParts[0];
        String expiryToken = contractParts[1];
        String strike = contractParts[2];
        String right = contractParts[3];

        LocalDate expiry;
        try {
            expiry = LocalDate.parse(expiryToken, COMMON_SYMBOL_OPTION_DATE);
        } catch (DateTimeParseException e) {
            return trimmed;
        }

        String paradexExpiry = expiry.format(PARADEX_OPTION_DATE).toUpperCase(Locale.US);
        return base + "-" + quote + "-" + paradexExpiry + "-" + strike + "-" + right;
    }
}
