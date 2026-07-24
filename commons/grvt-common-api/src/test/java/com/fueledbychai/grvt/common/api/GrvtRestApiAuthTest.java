package com.fueledbychai.grvt.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class GrvtRestApiAuthTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void loginUsesRoutingCookieAndPrivateRequestReauthenticatesOnceOn401() throws Exception {
        server.enqueue(loginResponse("session-one", "account-one"));
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"code\":1000,\"message\":\"You need to authenticate\"}"));
        server.enqueue(loginResponse("session-two", "account-two"));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"result\":{\"total_equity\":\"123.45\"}}"));

        TestGrvtRestApi api = new TestGrvtRestApi(server);
        JsonNode summary = api.getAccountSummary();

        assertEquals("123.45", summary.path("total_equity").asText());

        RecordedRequest loginOne = server.takeRequest();
        assertEquals("rm=true;", loginOne.getHeader("Cookie"));
        assertEquals("/auth/api_key/login", loginOne.getPath());

        RecordedRequest privateOne = server.takeRequest();
        assertEquals("gravity=session-one", privateOne.getHeader("Cookie"));
        assertEquals("account-one", privateOne.getHeader("X-Grvt-Account-Id"));

        RecordedRequest loginTwo = server.takeRequest();
        assertEquals("rm=true;", loginTwo.getHeader("Cookie"));

        RecordedRequest privateTwo = server.takeRequest();
        assertEquals("gravity=session-two", privateTwo.getHeader("Cookie"));
        assertEquals("account-two", privateTwo.getHeader("X-Grvt-Account-Id"));
        assertNotNull(privateTwo.getBody());
    }

    private static MockResponse loginResponse(String cookie, String accountId) {
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .addHeader("Set-Cookie", "gravity=" + cookie + "; Path=/; HttpOnly")
                .addHeader("X-Grvt-Account-Id", accountId)
                .setBody("{\"status\":\"success\"}");
    }

    private static final class TestGrvtRestApi extends GrvtRestApi {
        private final MockWebServer server;

        private TestGrvtRestApi(MockWebServer server) {
            super(GrvtEnvironment.forName("prod"), "api-key",
                    "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "sub-account");
            this.server = server;
        }

        @Override
        protected String authLoginUrl() {
            return server.url("/auth/api_key/login").toString();
        }

        @Override
        protected String tradeRestUrl() {
            String value = server.url("/").toString();
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }
    }
}
