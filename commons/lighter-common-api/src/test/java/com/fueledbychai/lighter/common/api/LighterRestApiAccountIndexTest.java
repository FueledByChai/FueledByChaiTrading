package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.ResponseException;
import com.fueledbychai.lighter.common.api.auth.LighterApiTokenResponse;
import com.fueledbychai.lighter.common.api.auth.LighterCreateApiTokenRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class LighterRestApiAccountIndexTest {

    @Test
    void resolveAccountIndexUsesAlternateLookupFieldWhenPrimaryFails() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        api.setLookupResponse("address", "{"
                + "\"code\":200,"
                + "\"accounts\":[{"
                + "\"account_index\":255,"
                + "\"address\":\"0xabc123\""
                + "}]"
                + "}");

        long resolved = api.resolveAccountIndex("0xabc123");

        assertEquals(255L, resolved);
    }

    @Test
    void parseAccountIndexFromLookupAllowsSingleAccountWithoutAddressField() {
        TestableLighterRestApi api = new TestableLighterRestApi();

        Long resolved = api.parseAccountIndex("{"
                + "\"code\":200,"
                + "\"accounts\":[{"
                + "\"account_index\":42"
                + "}]"
                + "}", "0xfeedbeef");

        assertEquals(42L, resolved.longValue());
    }

    @Test
    void getApiTokenUsesProvidedAccountIndex() {
        TestableLighterRestApi api = new TestableLighterRestApi();

        String token = api.getApiToken(77L);

        assertEquals("token-77", token);
        assertEquals(77L, api.lastTokenRequest.getAccountIndex());
    }

    private static class TestableLighterRestApi extends LighterRestApi {
        private final Map<String, JsonObject> responsesByLookupField = new HashMap<>();
        private LighterCreateApiTokenRequest lastTokenRequest;

        TestableLighterRestApi() {
            super("https://example.com", true);
        }

        void setLookupResponse(String byField, String responseJson) {
            responsesByLookupField.put(byField, JsonParser.parseString(responseJson).getAsJsonObject());
        }

        Long parseAccountIndex(String responseJson, String accountAddress) {
            JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
            return parseAccountIndexFromAccountLookup(root, accountAddress);
        }

        @Override
        protected JsonObject requestAccountLookup(String byField, String value) {
            JsonObject root = responsesByLookupField.get(byField);
            if (root == null) {
                throw new ResponseException("No lookup response for by=" + byField, 404);
            }
            return root;
        }

        @Override
        public LighterApiTokenResponse createApiToken(LighterCreateApiTokenRequest request) {
            lastTokenRequest = request;
            LighterApiTokenResponse response = new LighterApiTokenResponse();
            response.setCode(200);
            response.setApiToken("token-" + request.getAccountIndex());
            return response;
        }
    }
}
