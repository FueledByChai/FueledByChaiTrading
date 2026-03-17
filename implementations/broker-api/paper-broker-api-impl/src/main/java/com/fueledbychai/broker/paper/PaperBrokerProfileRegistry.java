package com.fueledbychai.broker.paper;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public final class PaperBrokerProfileRegistry {

    private static final Set<Exchange> CONFIGURED_EXCHANGES = Collections.unmodifiableSet(new LinkedHashSet<>(
            List.of(Exchange.DYDX, Exchange.HYPERLIQUID, Exchange.PARADEX, Exchange.LIGHTER, Exchange.BINANCE_SPOT,
                    Exchange.BINANCE_FUTURES, Exchange.DERIBIT, Exchange.OKX, Exchange.BYBIT, Exchange.ASTER)));

    private static final Set<InstrumentType> COMMISSION_INSTRUMENT_TYPES = Collections.unmodifiableSet(
            Set.of(InstrumentType.PERPETUAL_FUTURES, InstrumentType.CRYPTO_SPOT, InstrumentType.OPTION));

    private static final PaperBrokerLatency DEFAULT_LATENCY_PROFILE = new PaperBrokerLatency(0, 0, 0, 0);
    private static final PaperBrokerCommission DEFAULT_COMMISSION_PROFILE = new PaperBrokerCommission(0.0, 0.0);

    private static final Map<Exchange, PaperBrokerLatency> LATENCY_PROFILES = new ConcurrentHashMap<>();
    private static final Map<Exchange, Map<InstrumentType, PaperBrokerCommission>> COMMISSION_PROFILES = new ConcurrentHashMap<>();

    static {
        registerDefaultProfiles();
    }

    private PaperBrokerProfileRegistry() {
    }

    public static Set<Exchange> getConfiguredExchanges() {
        return CONFIGURED_EXCHANGES;
    }

    public static Set<InstrumentType> getCommissionInstrumentTypes() {
        return COMMISSION_INSTRUMENT_TYPES;
    }

    public static void registerLatencyProfile(Exchange exchange, PaperBrokerLatency latencyProfile) {
        LATENCY_PROFILES.put(Objects.requireNonNull(exchange, "exchange"),
                new PaperBrokerLatency(Objects.requireNonNull(latencyProfile, "latencyProfile")));
    }

    public static void registerCommissionProfile(Exchange exchange, InstrumentType instrumentType,
            PaperBrokerCommission commissionProfile) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(instrumentType, "instrumentType");
        Objects.requireNonNull(commissionProfile, "commissionProfile");
        COMMISSION_PROFILES.computeIfAbsent(exchange, ignored -> new ConcurrentHashMap<>()).put(instrumentType,
                new PaperBrokerCommission(commissionProfile));
    }

    public static PaperBrokerLatency getLatencyProfile(Exchange exchange) {
        if (exchange == null) {
            return new PaperBrokerLatency(DEFAULT_LATENCY_PROFILE);
        }

        return new PaperBrokerLatency(LATENCY_PROFILES.getOrDefault(exchange, DEFAULT_LATENCY_PROFILE));
    }

    public static PaperBrokerLatency getLatencyProfile(Ticker ticker) {
        return ticker == null ? new PaperBrokerLatency(DEFAULT_LATENCY_PROFILE) : getLatencyProfile(ticker.getExchange());
    }

    public static PaperBrokerCommission getCommissionProfile(Exchange exchange, InstrumentType instrumentType) {
        if (exchange == null || instrumentType == null) {
            return new PaperBrokerCommission(DEFAULT_COMMISSION_PROFILE);
        }

        Map<InstrumentType, PaperBrokerCommission> commissionsByType = COMMISSION_PROFILES.get(exchange);
        if (commissionsByType == null) {
            return new PaperBrokerCommission(DEFAULT_COMMISSION_PROFILE);
        }

        return new PaperBrokerCommission(
                commissionsByType.getOrDefault(instrumentType, DEFAULT_COMMISSION_PROFILE));
    }

    public static PaperBrokerCommission getCommissionProfile(Ticker ticker) {
        return ticker == null ? new PaperBrokerCommission(DEFAULT_COMMISSION_PROFILE)
                : getCommissionProfile(ticker.getExchange(), ticker.getInstrumentType());
    }

    private static void registerDefaultProfiles() {
        // Edit the exchange blocks below to change the paper broker's built-in defaults.
        registerExchangeDefaults(Exchange.DYDX, DEFAULT_LATENCY_PROFILE, DEFAULT_COMMISSION_PROFILE,
                DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);

        registerExchangeDefaults(Exchange.HYPERLIQUID, PaperBrokerLatency.HYPERLIQUID_LATENCY,
                PaperBrokerCommission.HYPERLIQUID_COMMISSION, DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);

        registerExchangeDefaults(Exchange.PARADEX, PaperBrokerLatency.PARDEX_LATENCY,
                PaperBrokerCommission.PARADEX_COMMISSION, DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);

        registerExchangeDefaults(Exchange.LIGHTER, DEFAULT_LATENCY_PROFILE, DEFAULT_COMMISSION_PROFILE,
                DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);

        registerExchangeDefaults(Exchange.BINANCE_SPOT, DEFAULT_LATENCY_PROFILE, DEFAULT_COMMISSION_PROFILE,
                DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);

        registerExchangeDefaults(Exchange.BINANCE_FUTURES, DEFAULT_LATENCY_PROFILE, DEFAULT_COMMISSION_PROFILE,
                DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);

        registerExchangeDefaults(Exchange.DERIBIT, DEFAULT_LATENCY_PROFILE, DEFAULT_COMMISSION_PROFILE,
                DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);

        registerExchangeDefaults(Exchange.OKX, DEFAULT_LATENCY_PROFILE, DEFAULT_COMMISSION_PROFILE,
                DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);

        registerExchangeDefaults(Exchange.BYBIT, DEFAULT_LATENCY_PROFILE, DEFAULT_COMMISSION_PROFILE,
                DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);

        registerExchangeDefaults(Exchange.ASTER, PaperBrokerLatency.ASTER_LATENCY,
                PaperBrokerCommission.ASTER_COMMISSION, DEFAULT_COMMISSION_PROFILE, DEFAULT_COMMISSION_PROFILE);
    }

    private static void registerExchangeDefaults(Exchange exchange, PaperBrokerLatency latencyProfile,
            PaperBrokerCommission perpetualFuturesCommission, PaperBrokerCommission spotCommission,
            PaperBrokerCommission optionCommission) {
        registerLatencyProfile(exchange, latencyProfile);
        registerCommissionProfile(exchange, InstrumentType.PERPETUAL_FUTURES, perpetualFuturesCommission);
        registerCommissionProfile(exchange, InstrumentType.CRYPTO_SPOT, spotCommission);
        registerCommissionProfile(exchange, InstrumentType.OPTION, optionCommission);
    }
}
