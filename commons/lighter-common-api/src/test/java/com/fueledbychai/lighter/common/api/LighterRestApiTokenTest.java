package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.ResponseException;
import com.fueledbychai.lighter.common.api.auth.LighterApiTokenResponse;
import com.fueledbychai.lighter.common.api.auth.LighterCreateApiTokenRequest;
import com.fueledbychai.lighter.common.api.signer.LighterNativeTransactionSigner;

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

    @Test
    void buildApiErrorResponseExceptionIncludesLighterErrorDetails() {
        TestableLighterRestApi api = new TestableLighterRestApi();

        ResponseException exception = api.toApiError("Unable to create Lighter API token", 401, "Unauthorized",
                "{\"code\":20013,\"message\":\"invalid auth: couldnt find account\"}");

        assertEquals(401, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("invalid auth: couldnt find account"));
        assertTrue(exception.getMessage().contains("api code 20013"));
    }

    @Test
    void getApiTokenRecreatesSignerWhenAccountIndexChanges() {
        SignerAwareLighterRestApi api = new SignerAwareLighterRestApi();

        String token1 = api.getApiToken(999L);
        String token2 = api.getApiToken(255L);

        assertEquals("token-999", token1);
        assertEquals("token-255", token2);
        assertEquals(List.of(Long.valueOf(999L), Long.valueOf(255L)), api.createdSignerAccountIndexes);
        assertEquals(List.of("auth-999", "auth-255"), api.authTokens);
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

        ResponseException toApiError(String operation, int httpStatusCode, String httpMessage, String responseBody) {
            return buildApiErrorResponseException(operation, httpStatusCode, httpMessage, responseBody);
        }
    }

    private static class SignerAwareLighterRestApi extends LighterRestApi {
        private final List<Long> createdSignerAccountIndexes = new ArrayList<>();
        private final List<String> authTokens = new ArrayList<>();

        SignerAwareLighterRestApi() {
            super("https://example.com", "0xabc123", "11".repeat(40), true);
        }

        @Override
        protected LighterNativeTransactionSigner createAuthSigner(String privateKey, int apiKeyIndex, long accountIndex) {
            createdSignerAccountIndexes.add(Long.valueOf(accountIndex));
            LighterNativeTransactionSigner signer = mock(LighterNativeTransactionSigner.class);
            when(signer.createAuthTokenWithExpiry(anyLong(), anyLong(), anyInt(), anyLong()))
                    .thenAnswer(invocation -> "auth-" + invocation.getArgument(3, Long.class).longValue());
            return signer;
        }

        @Override
        public LighterApiTokenResponse createApiToken(String authToken, LighterCreateApiTokenRequest request) {
            authTokens.add(authToken);
            LighterApiTokenResponse response = new LighterApiTokenResponse();
            response.setCode(200);
            response.setApiToken("token-" + request.getAccountIndex());
            return response;
        }
    }
}
