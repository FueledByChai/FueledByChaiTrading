package com.fueledbychai.bybit.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fueledbychai.bybit.common.api.IBybitRestApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class BybitTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static final DateTimeFormatter COMMON_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    protected static final DateTimeFormatter BYBIT_DATE = DateTimeFormatter.ofPattern("ddMMMyy", Locale.US);

    protected static ITickerRegistry instance;
    protected final IBybitRestApi restApi;
    protected final Set<String> optionBaseLoadAttempts = ConcurrentHashMap.newKeySet();

    public static ITickerRegistry getInstance(IBybitRestApi restApi) {
        if (instance == null) {
            instance = new BybitTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new BybitTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.BYBIT, IBybitRestApi.class));
        }
        return instance;
    }

    protected BybitTickerRegistry(IBybitRestApi restApi) {
        super(new BybitTickerTranslator());
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
        InstrumentDescriptor[] optionDescriptors = restApi.getAllInstrumentsForType(InstrumentType.OPTION);
        registerDescriptors(optionDescriptors);
        markOptionBasesAsLoaded(optionDescriptors);
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.CRYPTO_SPOT || instrumentType == InstrumentType.PERPETUAL_FUTURES
                || instrumentType == InstrumentType.FUTURES || instrumentType == InstrumentType.OPTION;
    }

    @Override
    public Ticker[] getOptionChain(String underlyingSymbol, int expiryYear, int expiryMonth, int expiryDay,
            OptionRightFilter optionRightFilter) {
        Ticker[] optionChain = super.getOptionChain(underlyingSymbol, expiryYear, expiryMonth, expiryDay, optionRightFilter);
        if (optionChain.length > 0) {
            return optionChain;
        }

        String normalizedUnderlying = normalizeOptionUnderlyingSymbol(underlyingSymbol);
        loadOptionDescriptorsForUnderlying(normalizedUnderlying);
        return super.getOptionChain(underlyingSymbol, expiryYear, expiryMonth, expiryDay, optionRightFilter);
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }

        String normalized = commonSymbol.trim().toUpperCase(Locale.US);
        if (normalized.isEmpty()) {
            return null;
        }

        Ticker directMatch = resolveCommonSymbolMapMatch(instrumentType, normalized);
        if (directMatch != null) {
            return directMatch.getSymbol();
        }

        return switch (instrumentType) {
            case CRYPTO_SPOT -> toSpotSymbol(normalized);
            case PERPETUAL_FUTURES -> toPerpetualSymbol(normalized);
            case FUTURES -> toFuturesSymbol(normalized);
            case OPTION -> toOptionSymbol(normalized);
            default -> normalized;
        };
    }

    protected Ticker resolveCommonSymbolMapMatch(InstrumentType instrumentType, String normalizedCommonSymbol) {
        Map<String, Ticker> map = commonSymbolMap.get(instrumentType);
        if (map == null || map.isEmpty()) {
            return null;
        }
        Ticker direct = map.get(normalizedCommonSymbol);
        if (direct != null) {
            return direct;
        }

        for (Map.Entry<String, Ticker> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(normalizedCommonSymbol)) {
                return entry.getValue();
            }
        }
        return null;
    }

    protected String toSpotSymbol(String symbol) {
        if (symbol.contains("/")) {
            String[] split = symbol.split("/", 2);
            if (split.length == 2) {
                return split[0] + split[1];
            }
        }
        return symbol;
    }

    protected String toPerpetualSymbol(String symbol) {
        String normalized = symbol;
        if (normalized.endsWith("-PERP")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        if (normalized.contains("/")) {
            String[] split = normalized.split("/", 2);
            if (split.length == 2) {
                String quote = split[1].contains("-") ? split[1].substring(0, split[1].indexOf('-')) : split[1];
                return split[0] + quote;
            }
        }
        return normalized;
    }

    protected String toFuturesSymbol(String symbol) {
        if (symbol.matches("^[A-Z0-9]+-\\d{2}[A-Z]{3}\\d{2}$")) {
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

        String date = contractParts[1];
        if (!date.matches("\\d{8}")) {
            return symbol;
        }

        try {
            LocalDate parsed = LocalDate.parse(date, COMMON_DATE);
            return split[0] + "-" + parsed.format(BYBIT_DATE).toUpperCase(Locale.US);
        } catch (DateTimeParseException e) {
            return symbol;
        }
    }

    protected String toOptionSymbol(String symbol) {
        if (symbol.matches("^[A-Z0-9]+-\\d{2}[A-Z]{3}\\d{2}-[0-9.]+-[CP]$")) {
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

        String date = contractParts[1];
        String strike = contractParts[2];
        String right = contractParts[3];

        if (!date.matches("\\d{8}")) {
            return symbol;
        }

        try {
            LocalDate parsed = LocalDate.parse(date, COMMON_DATE);
            return split[0] + "-" + parsed.format(BYBIT_DATE).toUpperCase(Locale.US) + "-" + strike + "-" + right;
        } catch (DateTimeParseException e) {
            return symbol;
        }
    }

    protected void loadOptionDescriptorsForUnderlying(String normalizedUnderlying) {
        if (normalizedUnderlying == null || normalizedUnderlying.isBlank()) {
            return;
        }
        if (!optionBaseLoadAttempts.add(normalizedUnderlying)) {
            return;
        }

        try {
            InstrumentDescriptor[] descriptors = restApi.getOptionInstrumentsForBaseCoin(normalizedUnderlying);
            registerDescriptors(descriptors);
            markOptionBasesAsLoaded(descriptors);
        } catch (RuntimeException e) {
            optionBaseLoadAttempts.remove(normalizedUnderlying);
        }
    }

    protected void markOptionBasesAsLoaded(InstrumentDescriptor[] descriptors) {
        if (descriptors == null || descriptors.length == 0) {
            return;
        }

        for (InstrumentDescriptor descriptor : descriptors) {
            if (descriptor == null || descriptor.getInstrumentType() != InstrumentType.OPTION
                    || descriptor.getBaseCurrency() == null) {
                continue;
            }
            optionBaseLoadAttempts.add(descriptor.getBaseCurrency().trim().toUpperCase(Locale.US));
        }
    }
}
