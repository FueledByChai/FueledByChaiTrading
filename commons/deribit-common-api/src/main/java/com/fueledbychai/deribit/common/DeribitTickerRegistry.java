package com.fueledbychai.deribit.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.deribit.common.api.IDeribitRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class DeribitTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected final IDeribitRestApi restApi;

    public static ITickerRegistry getInstance(IDeribitRestApi restApi) {
        if (instance == null) {
            instance = new DeribitTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new DeribitTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.DERIBIT, IDeribitRestApi.class));
        }
        return instance;
    }

    protected DeribitTickerRegistry(IDeribitRestApi restApi) {
        super(new DeribitTickerTranslator());
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        this.restApi = restApi;
        initialize();
    }

    protected void initialize() {
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT));
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES));
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.OPTION));
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.CRYPTO_SPOT || instrumentType == InstrumentType.PERPETUAL_FUTURES
                || instrumentType == InstrumentType.OPTION;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }

        String trimmed = commonSymbol.trim().toUpperCase();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (instrumentType == InstrumentType.CRYPTO_SPOT) {
            return trimmed.replace('/', '_');
        }

        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            if (trimmed.endsWith("-PERPETUAL")) {
                return trimmed;
            }
            if (trimmed.endsWith("-PERP")) {
                return trimmed.substring(0, trimmed.length() - 5) + "-PERPETUAL";
            }
            if (trimmed.contains("/")) {
                return trimmed.substring(0, trimmed.indexOf('/')) + "-PERPETUAL";
            }
            if (!trimmed.contains("-")) {
                return trimmed + "-PERPETUAL";
            }
            return trimmed;
        }

        if (trimmed.matches("^[A-Z0-9]+-\\d{1,2}[A-Z]{3}\\d{2}-[0-9.]+-[CP]$")) {
            return trimmed;
        }
        if (!trimmed.contains("/")) {
            return trimmed;
        }

        String[] quoteSplit = trimmed.split("/", 2);
        if (quoteSplit.length != 2 || !quoteSplit[1].contains("-")) {
            return trimmed;
        }

        String base = quoteSplit[0];
        String[] contractParts = quoteSplit[1].split("-");
        if (contractParts.length != 4) {
            return trimmed;
        }

        String deribitDate = contractParts[1];
        if (contractParts[1].matches("\\d{8}")) {
            deribitDate = Integer.parseInt(contractParts[1].substring(6, 8))
                    + monthCode(contractParts[1].substring(4, 6))
                    + contractParts[1].substring(2, 4);
        }

        return base + "-" + deribitDate + "-" + contractParts[2] + "-" + contractParts[3];
    }

    protected String monthCode(String monthNumber) {
        return switch (monthNumber) {
            case "01" -> "JAN";
            case "02" -> "FEB";
            case "03" -> "MAR";
            case "04" -> "APR";
            case "05" -> "MAY";
            case "06" -> "JUN";
            case "07" -> "JUL";
            case "08" -> "AUG";
            case "09" -> "SEP";
            case "10" -> "OCT";
            case "11" -> "NOV";
            case "12" -> "DEC";
            default -> monthNumber;
        };
    }
}
