package com.fueledbychai.okx.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.okx.common.api.IOkxRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class OkxTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static final DateTimeFormatter COMMON_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    protected static final DateTimeFormatter OKX_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    protected static ITickerRegistry instance;
    protected final IOkxRestApi restApi;

    public static ITickerRegistry getInstance(IOkxRestApi restApi) {
        if (instance == null) {
            instance = new OkxTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new OkxTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.OKX, IOkxRestApi.class));
        }
        return instance;
    }

    protected OkxTickerRegistry(IOkxRestApi restApi) {
        super(new OkxTickerTranslator());
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        this.restApi = restApi;
        initialize();
    }

    protected void initialize() {
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT));
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES));
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.FUTURES));
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.OPTION));
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.CRYPTO_SPOT || instrumentType == InstrumentType.PERPETUAL_FUTURES
                || instrumentType == InstrumentType.FUTURES || instrumentType == InstrumentType.OPTION;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }

        String trimmed = commonSymbol.trim().toUpperCase(Locale.US);
        if (trimmed.isEmpty()) {
            return null;
        }

        return switch (instrumentType) {
            case CRYPTO_SPOT -> toSpotSymbol(trimmed);
            case PERPETUAL_FUTURES -> toPerpetualSymbol(trimmed);
            case FUTURES -> toFuturesSymbol(trimmed);
            case OPTION -> toOptionSymbol(trimmed);
            default -> trimmed;
        };
    }

    protected String toSpotSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        if (symbol.contains("/")) {
            return symbol.replace('/', '-');
        }
        return symbol;
    }

    protected String toPerpetualSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        if (symbol.endsWith("-SWAP")) {
            return symbol;
        }
        if (symbol.endsWith("-PERP")) {
            return symbol.substring(0, symbol.length() - 5) + "-SWAP";
        }
        if (symbol.contains("/")) {
            String normalized = symbol;
            if (normalized.endsWith("-PERP")) {
                normalized = normalized.substring(0, normalized.length() - 5);
            }
            String[] split = normalized.split("/", 2);
            if (split.length == 2) {
                return split[0] + "-" + split[1] + "-SWAP";
            }
        }
        if (symbol.matches("^[A-Z0-9]+-[A-Z0-9]+$")) {
            return symbol + "-SWAP";
        }
        return symbol;
    }

    protected String toFuturesSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        if (symbol.matches("^[A-Z0-9]+-[A-Z0-9]+-\\d{6}$")) {
            return symbol;
        }

        if (!symbol.contains("/")) {
            return symbol;
        }

        String[] split = symbol.split("/", 2);
        if (split.length != 2 || !split[1].contains("-")) {
            return symbol;
        }

        String[] contractParts = split[1].split("-");
        if (contractParts.length < 2) {
            return symbol;
        }

        String quote = contractParts[0];
        String date = contractParts[1];
        String okxDate = toOkxDate(date);
        if (okxDate == null) {
            return symbol;
        }

        return split[0] + "-" + quote + "-" + okxDate;
    }

    protected String toOptionSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        if (symbol.matches("^[A-Z0-9]+-[A-Z0-9]+-\\d{6}-[0-9.]+-[CP]$")) {
            return symbol;
        }

        if (!symbol.contains("/")) {
            return symbol;
        }

        String[] split = symbol.split("/", 2);
        if (split.length != 2 || !split[1].contains("-")) {
            return symbol;
        }

        String[] contractParts = split[1].split("-");
        if (contractParts.length < 4) {
            return symbol;
        }

        String quote = contractParts[0];
        String date = contractParts[1];
        String strike = contractParts[2];
        String right = contractParts[3];

        String okxDate = toOkxDate(date);
        if (okxDate == null) {
            return symbol;
        }

        return split[0] + "-" + quote + "-" + okxDate + "-" + strike + "-" + normalizeRight(right);
    }

    protected String normalizeRight(String right) {
        if (right == null) {
            return "";
        }
        String normalized = right.trim().toUpperCase(Locale.US);
        if ("CALL".equals(normalized)) {
            return "C";
        }
        if ("PUT".equals(normalized)) {
            return "P";
        }
        return normalized;
    }

    protected String toOkxDate(String date) {
        if (date == null) {
            return null;
        }
        String normalized = date.trim();
        if (normalized.matches("\\d{6}")) {
            return normalized;
        }
        if (!normalized.matches("\\d{8}")) {
            return null;
        }
        try {
            LocalDate parsed = LocalDate.parse(normalized, COMMON_DATE);
            return parsed.format(OKX_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
