package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.auth.LighterAccountTier;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierRequest;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierResponse;

class LighterRestApiAccountTierTest {

    @Test
    void parseChangeAccountTierResponseReadsFields() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        String response = "{"
                + "\"code\":200,"
                + "\"message\":\"ok\""
                + "}";

        LighterChangeAccountTierResponse parsed = api.parseTierResponse(response);

        assertEquals(200, parsed.getCode());
        assertEquals("ok", parsed.getMessage());
    }

    @Test
    void changeAccountTierRequestValidationRequiresFields() {
        LighterChangeAccountTierRequest request = new LighterChangeAccountTierRequest();
        request.setAccountIndex(-1L);
        request.setNewTier(LighterAccountTier.PREMIUM);
        assertThrows(IllegalArgumentException.class, request::validate);

        request.setAccountIndex(1L);
        request.setNewTier(null);
        assertThrows(IllegalArgumentException.class, request::validate);
    }

    private static class TestableLighterRestApi extends LighterRestApi {

        TestableLighterRestApi() {
            super("https://example.com", true);
        }

        LighterChangeAccountTierResponse parseTierResponse(String responseBody) {
            return parseChangeAccountTierResponse(responseBody);
        }
    }
}
