package com.fueledbychai.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fueledbychai.data.ITickerTranslator;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public abstract class AbstractTickerRegistry implements ITickerTranslator, ITickerRegistry {

    protected final Map<InstrumentType, Map<String, Ticker>> tickerMap = new HashMap<>();
    protected final Map<InstrumentType, Map<String, Ticker>> instrumentIdMap = new HashMap<>();
    protected final Map<InstrumentType, Map<String, Ticker>> commonSymbolMap = new HashMap<>();
    protected final Map<InstrumentType, Map<InstrumentDescriptor, Ticker>> descriptorMap = new HashMap<>();
    protected ITickerTranslator tickerBuilder;

    protected AbstractTickerRegistry(ITickerTranslator tickerBuilder) {
        if (tickerBuilder == null) {
            throw new IllegalArgumentException("tickerBuilder is required");
        }
        this.tickerBuilder = tickerBuilder;
    }

    protected void requireInstrumentType(InstrumentType instrumentType) {
        if (instrumentType == null) {
            throw new IllegalArgumentException("InstrumentType is required");
        }
    }

    protected void requireSupportedInstrumentType(InstrumentType instrumentType) {
        requireInstrumentType(instrumentType);
        if (!supportsInstrumentType(instrumentType)) {
            throw new IllegalArgumentException("Unsupported instrument type: " + instrumentType);
        }
    }

    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return true;
    }

    protected Map<String, Ticker> getTickerMap(InstrumentType instrumentType) {
        return tickerMap.computeIfAbsent(instrumentType, key -> new HashMap<>());
    }

    protected Map<String, Ticker> getCommonSymbolMap(InstrumentType instrumentType) {
        return commonSymbolMap.computeIfAbsent(instrumentType, key -> new HashMap<>());
    }

    protected Map<String, Ticker> getInstrumentIdMap(InstrumentType instrumentType) {
        return instrumentIdMap.computeIfAbsent(instrumentType, key -> new HashMap<>());
    }

    protected Map<InstrumentDescriptor, Ticker> getDescriptorMap(InstrumentType instrumentType) {
        return descriptorMap.computeIfAbsent(instrumentType, key -> new HashMap<>());
    }

    protected void cacheTicker(InstrumentDescriptor descriptor, Ticker ticker) {
        InstrumentType instrumentType = descriptor.getInstrumentType();
        getDescriptorMap(instrumentType).put(descriptor, ticker);
        getCommonSymbolMap(instrumentType).put(descriptor.getCommonSymbol(), ticker);
        getTickerMap(instrumentType).put(ticker.getSymbol(), ticker);
        String instrumentId = ticker.getId();
        if (instrumentId != null && !instrumentId.isBlank()) {
            getInstrumentIdMap(instrumentType).put(instrumentId, ticker);
        }
    }

    protected void registerDescriptors(InstrumentDescriptor[] descriptors) {
        if (descriptors == null) {
            return;
        }
        for (InstrumentDescriptor descriptor : descriptors) {
            if (descriptor != null) {
                translateTicker(descriptor);
            }
        }
    }

    @Override
    public Ticker[] getAllTickers() {
        List<Ticker> tickers = new ArrayList<>();
        for (Map<String, Ticker> map : tickerMap.values()) {
            if (map != null && !map.isEmpty()) {
                tickers.addAll(map.values());
            }
        }
        sortTickers(tickers);
        return tickers.toArray(new Ticker[0]);
    }

    @Override
    public Ticker[] getAllTickersForType(InstrumentType instrumentType) {
        requireInstrumentType(instrumentType);
        if (!supportsInstrumentType(instrumentType)) {
            return new Ticker[0];
        }

        Map<String, Ticker> map = tickerMap.get(instrumentType);
        if (map == null || map.isEmpty()) {
            return new Ticker[0];
        }

        List<Ticker> tickers = new ArrayList<>(map.values());
        sortTickers(tickers);
        return tickers.toArray(new Ticker[0]);
    }

    @Override
    public Ticker[] getOptionChain(String underlyingSymbol, int expiryYear, int expiryMonth, int expiryDay,
            OptionRightFilter optionRightFilter) {
        String normalizedUnderlying = normalizeOptionUnderlyingSymbol(underlyingSymbol);
        validateExpiryFilter(expiryYear, expiryMonth, expiryDay);
        OptionRightFilter rightFilter = optionRightFilter == null ? OptionRightFilter.ALL : optionRightFilter;

        if (!supportsInstrumentType(InstrumentType.OPTION)) {
            return new Ticker[0];
        }

        Map<InstrumentDescriptor, Ticker> optionMap = descriptorMap.get(InstrumentType.OPTION);
        if (optionMap == null || optionMap.isEmpty()) {
            return new Ticker[0];
        }

        List<Ticker> matches = new ArrayList<>();
        for (Entry<InstrumentDescriptor, Ticker> entry : optionMap.entrySet()) {
            InstrumentDescriptor descriptor = entry.getKey();
            Ticker ticker = entry.getValue();

            if (!matchesOptionUnderlying(descriptor, normalizedUnderlying)) {
                continue;
            }
            if (!matchesOptionExpiry(ticker, expiryYear, expiryMonth, expiryDay)) {
                continue;
            }
            if (!matchesOptionRight(ticker, rightFilter)) {
                continue;
            }

            matches.add(ticker);
        }

        sortOptionChain(matches);
        return matches.toArray(new Ticker[0]);
    }

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("InstrumentDescriptor is required");
        }
        InstrumentType instrumentType = descriptor.getInstrumentType();
        requireSupportedInstrumentType(instrumentType);

        Ticker ticker = tickerBuilder.translateTicker(descriptor);
        cacheTicker(descriptor, ticker);
        return ticker;
    }

    @Override
    public Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString) {
        requireSupportedInstrumentType(instrumentType);
        if (tickerString == null) {
            return null;
        }
        Map<String, Ticker> map = tickerMap.get(instrumentType);
        if (map != null) {
            Ticker ticker = map.get(tickerString);
            if (ticker != null) {
                return ticker;
            }
        }
        Map<String, Ticker> idLookup = instrumentIdMap.get(instrumentType);
        if (idLookup == null) {
            return null;
        }
        return idLookup.get(tickerString);
    }

    @Override
    public Ticker lookupByCommonSymbol(InstrumentType instrumentType, String commonSymbol) {
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }
        Map<String, Ticker> map = commonSymbolMap.get(instrumentType);
        if (map != null) {
            Ticker direct = map.get(commonSymbol);
            if (direct != null) {
                return direct;
            }
            // Prefix search: bare base symbol (e.g. "BTC") matches "BTC/USDT", "BTC/USDT-PERP", etc.
            // Prefer common quote currencies in order: USDT, USDC, USD.
            String prefix = commonSymbol + "/";
            Ticker prefixMatch = findPreferredPrefixMatch(map, prefix);
            if (prefixMatch != null) {
                return prefixMatch;
            }
        }
        String exchangeSymbol = commonSymbolToExchangeSymbol(instrumentType, commonSymbol);
        if (exchangeSymbol == null) {
            return null;
        }
        return lookupByBrokerSymbol(instrumentType, exchangeSymbol);
    }

    protected Ticker findPreferredPrefixMatch(Map<String, Ticker> map, String prefix) {
        // Try preferred quote currencies first
        String[] preferredSuffixes = { "USDT", "USDT-PERP", "USDC", "USDC-PERP", "USD", "USD-PERP" };
        for (String suffix : preferredSuffixes) {
            Ticker ticker = map.get(prefix + suffix);
            if (ticker != null) {
                return ticker;
            }
        }
        // Fall back to first prefix match
        for (Map.Entry<String, Ticker> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.startsWith(prefix)) {
                return entry.getValue();
            }
        }
        return null;
    }

    protected void sortTickers(List<Ticker> tickers) {
        tickers.sort(this::compareTickers);
    }

    protected int compareTickers(Ticker left, Ticker right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }

        int symbolCompare = compareNullableStrings(left.getSymbol(), right.getSymbol());
        if (symbolCompare != 0) {
            return symbolCompare;
        }

        int typeCompare = compareInstrumentTypes(left.getInstrumentType(), right.getInstrumentType());
        if (typeCompare != 0) {
            return typeCompare;
        }

        return compareNullableStrings(left.getId(), right.getId());
    }

    protected int compareInstrumentTypes(InstrumentType left, InstrumentType right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Integer.compare(left.ordinal(), right.ordinal());
    }

    protected int compareNullableStrings(String left, String right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    protected String normalizeOptionUnderlyingSymbol(String underlyingSymbol) {
        if (underlyingSymbol == null) {
            throw new IllegalArgumentException("underlyingSymbol is required");
        }

        String normalized = underlyingSymbol.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("underlyingSymbol is required");
        }

        int slashIndex = normalized.indexOf('/');
        if (slashIndex > 0) {
            normalized = normalized.substring(0, slashIndex);
        }

        int dashIndex = normalized.indexOf('-');
        if (dashIndex > 0) {
            normalized = normalized.substring(0, dashIndex);
        }

        return normalized;
    }

    protected void validateExpiryFilter(int expiryYear, int expiryMonth, int expiryDay) {
        if (expiryYear < 0) {
            throw new IllegalArgumentException("expiryYear must be >= 0");
        }
        if (expiryMonth < 0 || expiryMonth > 12) {
            throw new IllegalArgumentException("expiryMonth must be 0 (no month filter) or between 1 and 12");
        }
        if (expiryDay < 0 || expiryDay > 31) {
            throw new IllegalArgumentException("expiryDay must be between 0 and 31");
        }
    }

    protected boolean matchesOptionUnderlying(InstrumentDescriptor descriptor, String normalizedUnderlying) {
        if (descriptor == null) {
            return false;
        }

        if (normalizedUnderlying.equals(normalizeOptionField(descriptor.getBaseCurrency()))) {
            return true;
        }
        if (normalizedUnderlying.equals(extractUnderlying(descriptor.getCommonSymbol()))) {
            return true;
        }
        return normalizedUnderlying.equals(extractUnderlying(descriptor.getExchangeSymbol()));
    }

    protected String normalizeOptionField(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    protected String extractUnderlying(String value) {
        String normalized = normalizeOptionField(value);
        if (normalized == null) {
            return null;
        }

        int slashIndex = normalized.indexOf('/');
        int dashIndex = normalized.indexOf('-');

        int endIndex = normalized.length();
        if (slashIndex > 0) {
            endIndex = slashIndex;
        }
        if (dashIndex > 0 && dashIndex < endIndex) {
            endIndex = dashIndex;
        }

        return normalized.substring(0, endIndex);
    }

    protected boolean matchesOptionExpiry(Ticker ticker, int expiryYear, int expiryMonth, int expiryDay) {
        if (ticker == null) {
            return false;
        }
        if (expiryYear > 0 && ticker.getExpiryYear() != expiryYear) {
            return false;
        }
        if (expiryMonth > 0 && ticker.getExpiryMonth() != expiryMonth) {
            return false;
        }
        if (expiryDay > 0 && ticker.getExpiryDay() != expiryDay) {
            return false;
        }
        return true;
    }

    protected boolean matchesOptionRight(Ticker ticker, OptionRightFilter optionRightFilter) {
        if (ticker == null) {
            return false;
        }
        if (optionRightFilter == null || optionRightFilter == OptionRightFilter.ALL) {
            return true;
        }

        if (optionRightFilter == OptionRightFilter.CALL) {
            return ticker.getRight() == Ticker.Right.CALL;
        }
        if (optionRightFilter == OptionRightFilter.PUT) {
            return ticker.getRight() == Ticker.Right.PUT;
        }
        return true;
    }

    protected void sortOptionChain(List<Ticker> tickers) {
        tickers.sort(this::compareOptionTickers);
    }

    protected int compareOptionTickers(Ticker left, Ticker right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }

        int expiryCompare = Integer.compare(left.getExpiryYear(), right.getExpiryYear());
        if (expiryCompare != 0) {
            return expiryCompare;
        }

        expiryCompare = Integer.compare(left.getExpiryMonth(), right.getExpiryMonth());
        if (expiryCompare != 0) {
            return expiryCompare;
        }

        expiryCompare = Integer.compare(left.getExpiryDay(), right.getExpiryDay());
        if (expiryCompare != 0) {
            return expiryCompare;
        }

        int strikeCompare = compareNullableBigDecimals(left.getStrike(), right.getStrike());
        if (strikeCompare != 0) {
            return strikeCompare;
        }

        int rightCompare = compareOptionRights(left.getRight(), right.getRight());
        if (rightCompare != 0) {
            return rightCompare;
        }

        return compareTickers(left, right);
    }

    protected int compareNullableBigDecimals(BigDecimal left, BigDecimal right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    protected int compareOptionRights(Ticker.Right left, Ticker.Right right) {
        return Integer.compare(optionRightOrder(left), optionRightOrder(right));
    }

    protected int optionRightOrder(Ticker.Right right) {
        if (right == Ticker.Right.CALL) {
            return 0;
        }
        if (right == Ticker.Right.PUT) {
            return 1;
        }
        return 2;
    }
}
