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
 * endpoints live on {@code https://api.hibachi.xyz}. Order place and modify are exposed
 * here; cancel is WS-only.
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

    /** 24h stats (high, low, volume) for a single symbol. */
    JsonNode getMarketStats(String symbol);

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

    /**
     * Single order by clientId. Hibachi's /trade/order endpoint accepts
     * either {@code orderId} or {@code clientId} as a query parameter; this
     * method dispatches to the latter so callers that only retain the
     * client id (e.g. recovering a stuck place where the WS gateway
     * response was lost) can still resolve the order.
     */
    JsonNode getOrderByClientId(String clientId);

    /** Capital balance (deposits/withdrawals). */
    JsonNode getCapitalBalance();

    /**
     * Modify an existing open order via the documented PUT /trade/order endpoint.
     *
     * <p>The trade WebSocket exposes an {@code order.modify} method, but the venue
     * does not actually respond to it — calls hang until the WS-await timer fires.
     * Modifies must therefore go through this REST endpoint. The body should already
     * carry: {@code nonce}, {@code quantity}, {@code price}, {@code signature},
     * {@code maxFeesPercent}, and exactly one of {@code orderId} / {@code clientId} /
     * the original placement nonce. The implementation injects {@code accountId}.
     */
    JsonNode modifyOrder(java.util.Map<String, Object> body);

    /**
     * Place a new order via POST /trade/order. The body should already carry the
     * same fields the WS {@code order.place} call uses (symbol, side, quantity,
     * price, orderType, nonce, maxFeesPercent, signature, clientOrderId, etc.).
     * The implementation injects {@code accountId}.
     */
    JsonNode placeOrder(java.util.Map<String, Object> body);
}
