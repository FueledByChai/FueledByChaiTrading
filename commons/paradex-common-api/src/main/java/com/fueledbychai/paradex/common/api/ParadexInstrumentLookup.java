package com.fueledbychai.paradex.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.IInstrumentLookup;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.Util;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

public class ParadexInstrumentLookup implements IInstrumentLookup {

    private static final Logger logger = LoggerFactory.getLogger(ParadexInstrumentLookup.class);

    private final IParadexRestApi api;
    private final Retry retry;

    public ParadexInstrumentLookup() {
        this.api = ExchangeRestApiFactory.getPublicApi(Exchange.PARADEX, IParadexRestApi.class);
        this.retry = createRetryConfig(ParadexLookupRetryConfig.defaultConfig());
    }

    /**
     * Constructor for dependency injection or testing
     */
    public ParadexInstrumentLookup(IParadexRestApi api) {
        this.api = api;
        this.retry = createRetryConfig(ParadexLookupRetryConfig.defaultConfig());
    }

    /**
     * Constructor with custom retry configuration
     */
    public ParadexInstrumentLookup(IParadexRestApi api, ParadexLookupRetryConfig retryConfig) {
        this.api = api;
        this.retry = createRetryConfig(retryConfig);
    }

    private Retry createRetryConfig(ParadexLookupRetryConfig retryConfig) {
        RetryConfig.Builder<Object> configBuilder = RetryConfig.custom().maxAttempts(retryConfig.getMaxAttempts())
                .waitDuration(retryConfig.getWaitDuration())
                .retryOnException(throwable -> Util.isRetryableException(throwable));

        // Add exponential backoff if enabled
        if (retryConfig.isExponentialBackoff()) {
            // Note: In resilience4j 2.x, exponential backoff is handled differently
            // For now, we'll use a simple fixed wait duration
            logger.debug("Exponential backoff requested but using fixed duration for simplicity");
        }

        RetryConfig config = configBuilder.build();

        Retry retry = Retry.of("paradex-instrument-lookup", config);

        // Add event listeners for monitoring
        retry.getEventPublisher().onRetry(event -> logger.warn("Instrument lookup retry #{} for operation due to: {}",
                event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));

        retry.getEventPublisher().onSuccess(event -> {
            if (event.getNumberOfRetryAttempts() > 0) {
                logger.info("Instrument lookup succeeded after {} retries", event.getNumberOfRetryAttempts());
            }
        });

        return retry;
    }

    @Override
    public InstrumentDescriptor lookupByCommonSymbol(String commonSymbol) {
        return lookupByExchangeSymbol(ParadexUtil.commonSymbolToParadexSymbol(commonSymbol));
    }

    @Override
    public InstrumentDescriptor lookupByExchangeSymbol(String exchangeSymbol) {
        try {
            return Retry.decorateSupplier(retry, () -> {
                try {
                    return api.getInstrumentDescriptor(exchangeSymbol);
                } catch (FueledByChaiException e) {
                    throw new RuntimeException(e);
                }
            }).get();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof FueledByChaiException) {
                throw new FueledByChaiException("Failed to lookup instrument: " + exchangeSymbol, e.getCause());
            }
            throw e;
        }
    }

    @Override
    public InstrumentDescriptor lookupByTicker(Ticker ticker) {
        return lookupByExchangeSymbol(ticker.getSymbol());
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES && instrumentType != InstrumentType.CRYPTO_SPOT) {
            throw new IllegalArgumentException("Only perpetual futures and spot are supported at this time.");
        }

        try {
            return Retry.decorateSupplier(retry, () -> {
                try {
                    return api.getAllInstrumentsForType(instrumentType);
                } catch (FueledByChaiException e) {
                    throw new RuntimeException(e);
                }
            }).get();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof FueledByChaiException) {
                throw new FueledByChaiException("Failed to get instruments for type: " + instrumentType, e.getCause());
            }
            throw e;
        }
    }
}
