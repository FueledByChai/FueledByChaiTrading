package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.auth.LighterApiTokenResponse;
import com.fueledbychai.lighter.common.api.auth.LighterCreateApiTokenRequest;

class LighterRestApiTokenTest {

    @Test
    void parseApiTokenResponseReadsApiTokenFields() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        String response = "{"
                + "\"code\":200,"
                + "\"message\":\"ok\","
                + "\"token_id\":17,"
                + "\"api_token\":\"ro:6:all:1767139200:a1b2c3\","
                + "\"name\":\"fbc-token\","
                + "\"account_index\":6,"
                + "\"expiry\":1767139200,"
                + "\"sub_account_access\":true,"
                + "\"revoked\":false,"
                + "\"scopes\":\"read.*\""
                + "}";

        LighterApiTokenResponse parsed = api.parseTokenResponse(response);

        assertEquals(200, parsed.getCode());
        assertEquals(17L, parsed.getTokenId());
        assertEquals("ro:6:all:1767139200:a1b2c3", parsed.getApiToken());
        assertEquals("fbc-token", parsed.getName());
        assertEquals(6L, parsed.getAccountIndex());
        assertEquals(1767139200L, parsed.getExpiry());
        assertTrue(parsed.isSubAccountAccess());
        assertFalse(parsed.isRevoked());
        assertEquals("read.*", parsed.getScopes());
    }

    @Test
    void buildDefaultApiTokenRequestUsesExpectedDefaults() {
        TestableLighterRestApi api = new TestableLighterRestApi();

        long now = System.currentTimeMillis() / 1000L;
        LighterCreateApiTokenRequest request = api.defaultTokenRequest();
        long ttl = request.getExpiry() - now;

        assertNotNull(request.getName());
        assertTrue(request.getName().startsWith("fueledbychai-readonly-"));
        assertEquals(LighterCreateApiTokenRequest.DEFAULT_SCOPES, request.getScopes());
        assertFalse(request.isSubAccountAccess());
        assertTrue(ttl >= LighterRestApi.DEFAULT_API_TOKEN_TTL_SECONDS - 2);
        assertTrue(ttl <= LighterRestApi.DEFAULT_API_TOKEN_TTL_SECONDS + 2);
    }

    private static class TestableLighterRestApi extends LighterRestApi {

        TestableLighterRestApi() {
            super("https://example.com", true);
        }

        LighterApiTokenResponse parseTokenResponse(String responseBody) {
            return parseApiTokenResponse(responseBody);
        }

        LighterCreateApiTokenRequest defaultTokenRequest() {
            return buildDefaultApiTokenRequest();
        }
    }
}
