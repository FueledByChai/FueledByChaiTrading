package com.fueledbychai.hibachi.common.api;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

/**
 * Public + private REST contract for the Hibachi exchange.
 *
 * <p>Public market endpoints live on {@code https://data-api.hibachi.xyz}; private trading
 * endpoints live on {@code https://api.hibachi.xyz}. Order place/modify/cancel are NOT on
 * the REST surface — they are sent over the trade WebSocket. See the broker module.
 */
public interface IHibachiRestApi {

    /** Returns known instrument descriptors for the requested instrument type. */
    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    /** Resolves a single instrument descriptor by exchange or common symbol. */
    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    /**
     * Returns the {@link HibachiContract} for a symbol — required by the trade-WS signer
     * for {@code contractId} and decimal scales.
     */
    HibachiContract getContract(String symbol);

    /** Returns all {@link HibachiContract}s by symbol. */
    Map<String, HibachiContract> getAllContracts();

    /** Indicates whether this API instance was created without private credentials. */
    boolean isPublicApiOnly();

    /** Exchange UTC server time. */
    Date getServerTime();

    // ---------- public market data ----------

    /** Top-of-book and ticker stats per symbol. */
    JsonNode getMarketStats();

    /** Recent trades for a symbol. */
    JsonNode getRecentTrades(String symbol);

    /** Order book snapshot for a symbol. */
    JsonNode getOrderBookSnapshot(String symbol);

    /** Historical klines for a symbol. */
    JsonNode getKlines(String symbol, String interval, Integer limit);

    /** Open interest for a symbol. */
    JsonNode getOpenInterest(String symbol);

    /** Funding rates for a symbol. */
    JsonNode getFundingRates(String symbol);

    /** Mark-price + spot-price snapshot. */
    JsonNode getPrices();

    // ---------- private trading ----------

    /** Account information (balance, positions, margin). */
    JsonNode getTradeAccountInfo();

    /** Recent trades for the authenticated account. */
    JsonNode getAccountTrades();

    /** Open orders for the authenticated account. */
    JsonNode getOpenOrders();

    /** Single order by id. */
    JsonNode getOrder(String orderId);

    /** Capital balance (deposits/withdrawals). */
    JsonNode getCapitalBalance();
}
