package com.fueledbychai.lighter.common.api;

import java.util.List;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.google.gson.JsonObject;
import com.fueledbychai.lighter.common.api.account.LighterPosition;
import com.fueledbychai.lighter.common.api.auth.LighterApiTokenResponse;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierRequest;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierResponse;
import com.fueledbychai.lighter.common.api.auth.LighterCreateApiTokenRequest;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;

/**
 * Public REST contract for the Lighter exchange integration.
 *
 * This interface is intended to be the stable boundary used by strategy code and
 * by the shared exchange factories. Implementations should keep the semantics of
 * these methods stable, validate inputs early, and return normalized domain
 * objects instead of raw transport payloads whenever possible.
 */
public interface ILighterRestApi {

    /**
     * Returns the top-of-book (best bid/offer) data for the given market.
     *
     * The response is the raw JSON payload from the Lighter REST API
     * containing order book levels that can be used to extract BBO data.
     *
     * @param marketId the Lighter market identifier
     * @return the parsed JSON response object
     */
    JsonObject getOrderBookBBO(int marketId);

    /**
     * Returns the known instruments for the supplied instrument type.
     *
     * @param instrumentType the instrument type to query
     * @return all matching instrument descriptors
     */
    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    /**
     * Resolves a single instrument descriptor by symbol.
     *
     * @param symbol the exchange symbol
     * @return the resolved instrument descriptor, or {@code null} if not found
     */
    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    /**
     * Creates an API token using a caller-supplied auth token.
     *
     * @param authToken the websocket or account auth token to authorize the request
     * @param request the token creation request
     * @return the created API token response
     */
    LighterApiTokenResponse createApiToken(String authToken, LighterCreateApiTokenRequest request);

    /**
     * Creates an API token using credentials configured on the REST API instance.
     *
     * @param request the token creation request
     * @return the created API token response
     */
    LighterApiTokenResponse createApiToken(LighterCreateApiTokenRequest request);

    /**
     * Changes the account tier using a caller-supplied auth token.
     *
     * @param authToken the websocket or account auth token to authorize the request
     * @param request the requested account tier change
     * @return the exchange response
     */
    LighterChangeAccountTierResponse changeAccountTier(String authToken, LighterChangeAccountTierRequest request);

    /**
     * Changes the account tier using credentials configured on the REST API
     * instance.
     *
     * @param request the requested account tier change
     * @return the exchange response
     */
    LighterChangeAccountTierResponse changeAccountTier(LighterChangeAccountTierRequest request);

    /**
     * Returns an API token for the configured account context.
     *
     * @return the generated or retrieved API token
     */
    String getApiToken();

    /**
     * Returns an API token for the supplied account index.
     *
     * Implementations that do not need an account-specific override may delegate
     * to {@link #getApiToken()}.
     *
     * @param accountIndex the exchange account index
     * @return the generated or retrieved API token
     */
    default String getApiToken(long accountIndex) {
        return getApiToken();
    }

    /**
     * Resolves the exchange account index for an account address.
     *
     * @param accountAddress the account address to look up
     * @return the resolved account index
     */
    default long resolveAccountIndex(String accountAddress) {
        throw new UnsupportedOperationException("resolveAccountIndex is not implemented");
    }

    /**
     * Returns the next nonce to use for signed requests for the supplied account.
     *
     * @param accountIndex the exchange account index
     * @param apiKeyIndex the API key index used for signing
     * @return the next available nonce
     */
    long getNextNonce(long accountIndex, int apiKeyIndex);

    /**
     * Returns the current positions for an account.
     *
     * @param accountIndex the exchange account index
     * @return the current positions
     */
    List<LighterPosition> getPositions(long accountIndex);

    /**
        * Returns active orders for an account across all markets, authorized with a
        * caller-supplied auth token.
        *
        * @param authToken the auth token to authorize the request
        * @param accountIndex the exchange account index
        * @return active orders for the supplied account across all markets
        */
        List<LighterOrder> getAccountActiveOrders(String authToken, long accountIndex);

        /**
     * Returns active orders for an account, authorized with a caller-supplied auth
     * token.
     *
     * @param authToken the auth token to authorize the request
     * @param accountIndex the exchange account index
     * @param marketId the market identifier
     * @return active orders for the supplied account and market
     */
    List<LighterOrder> getAccountActiveOrders(String authToken, long accountIndex, int marketId);

    /**
        * Returns active orders for an account across all markets using credentials
        * configured on the REST API instance.
        *
        * @param accountIndex the exchange account index
        * @return active orders for the supplied account across all markets
        */
        List<LighterOrder> getAccountActiveOrders(long accountIndex);

        /**
     * Returns active orders for an account using credentials configured on the
     * REST API instance.
     *
     * @param accountIndex the exchange account index
     * @param marketId the market identifier
     * @return active orders for the supplied account and market
     */
    List<LighterOrder> getAccountActiveOrders(long accountIndex, int marketId);
}
