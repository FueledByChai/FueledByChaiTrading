package com.fueledbychai.hyperliquid.ws;

import java.net.URI;
import java.time.Duration;

import com.fueledbychai.hyperliquid.HyperliquidUtil;
import com.fueledbychai.http.OkHttpClientFactory;
import com.fueledbychai.hyperliquid.ws.json.HLSigner;
import com.fueledbychai.hyperliquid.ws.json.LimitType;
import com.fueledbychai.hyperliquid.ws.json.Mappers;
import com.fueledbychai.hyperliquid.ws.json.OrderAction;
import com.fueledbychai.hyperliquid.ws.json.OrderJson;
import com.fueledbychai.hyperliquid.ws.json.SignableExchangeOrderRequest;
import com.fueledbychai.hyperliquid.ws.json.SignatureFields;
import com.fueledbychai.hyperliquid.ws.json.SubmitExchangeRequest;
import com.fueledbychai.hyperliquid.ws.json.WebServicePostMessage;
import com.fueledbychai.hyperliquid.ws.json.ws.SubmitPostResponse;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HyperliquidClient {
    protected static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HyperliquidClient.class);
    protected static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private final HLSigner signer;
    private final OkHttpClient http = createHttpClient();
    private long startMethodMillis;
    private long completedMillis;
    private long presendMillis;
    private int requestId = 1;
    HyperliquidWebSocketClient client;

    public HyperliquidClient(String apiWalletPrivKeyHex) throws Exception {
        this.signer = new HLSigner(apiWalletPrivKeyHex);
        String wsUrl = "wss://api.hyperliquid-testnet.xyz/ws"; // or mainnet URL
        HyperliquidPostWebSocketProcessor wsProcessor = new HyperliquidPostWebSocketProcessor(() -> {
            System.out.println("WebSocket connected callback");
        });
        this.client = new HyperliquidWebSocketClient(wsUrl, wsProcessor);
        client.connect();

    }

    protected OkHttpClient createHttpClient() {
        return OkHttpClientFactory.create(Duration.ofSeconds(10));
    }

    public SubmitExchangeRequest getJson(SignableExchangeOrderRequest signable) throws Exception {
        // Nonce: typical pattern is current epoch millis
        if (signable.nonceMs == 0) {
            signable.nonceMs = System.currentTimeMillis();
        }

        // Sign
        // boolean isMainnet = apiBaseUrl.equals("https://api.hyperliquid.xyz"); // or
        // however you determine this
        boolean isMainnet = false; // Change as needed
        SignatureFields sig = signer.signL1OrderAction(signable.action, // ActionPayload
                signable.nonceMs, // ms epoch
                signable.vaultAddress, // null or "0x..."
                signable.expiresAfterMs, // nullable
                isMainnet);

        // Attach signature
        SubmitExchangeRequest submit = new SubmitExchangeRequest();
        submit.action = signable.action;
        submit.nonceMs = signable.nonceMs;
        submit.vaultAddress = signable.vaultAddress;
        submit.expiresAfterMs = signable.expiresAfterMs;
        submit.signature = sig;

        return submit;

    }

    public String submitViaRest(SubmitExchangeRequest submit) throws Exception {

        String json = Mappers.JSON.writeValueAsString(submit);

        presendMillis = System.currentTimeMillis();
        Request req = new Request.Builder()
                .url(URI.create("https://api.hyperliquid-testnet.xyz/exchange").toString())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            return resp.body() == null ? null : resp.body().string();
        }
    }

    public void submitOrder(boolean useWebSocket) throws Exception {

        // Example private key; DO NOT USE IN PRODUCTION
        String privKey = "";

        OrderJson buy = new OrderJson();
        buy.assetId = 3;
        buy.isBuy = true;
        buy.price = "113100";
        buy.size = "0.01";
        buy.reduceOnly = false;
        buy.type = new LimitType(LimitType.TimeInForce.GTC);
        // buy.clientOrderId = EncodeUtil.encode128BitHex("Hello World 123");
        // buy.clientOrderId =
        // "289c505c8da38d9d0bb8d43aace34d89a1f96ca2d6963bdba552b195604f0bb";

        OrderAction action = new OrderAction();
        action.orders = java.util.Arrays.asList(buy);

        SignableExchangeOrderRequest signable = new SignableExchangeOrderRequest();
        signable.action = action;
        signable.nonceMs = System.currentTimeMillis();

        logger.info("json: {}", Mappers.JSON.writeValueAsString(signable));

        SubmitExchangeRequest reqest = getJson(signable);
        startMethodMillis = System.currentTimeMillis();

        try {
            String resp;
            if (useWebSocket) {
                submitViaWebSocket(reqest);
                resp = "Submitted via WebSocket";
            } else {
                resp = submitViaRest(reqest);
            }
            completedMillis = System.currentTimeMillis();

            logger.info("Response: {}", resp);
            logger.info("Timings: totalMillis={}", (completedMillis - startMethodMillis));
            logger.info("Timings: startMethodMillis={}, presendMillis={}, completedMillis={}", startMethodMillis,
                    presendMillis, completedMillis);

        } catch (Exception e) {
            logger.error("Error submitting order: ", e);

        }
    }

    public void submitViaWebSocket(SubmitExchangeRequest submit) throws Exception {

        WebServicePostMessage wsRequest = new WebServicePostMessage();
        wsRequest.id = requestId++;
        wsRequest.setPayload(submit);

        String jsonToSend = Mappers.JSON.writeValueAsString(wsRequest);
        client.postMessage(jsonToSend);

    }

    public void submitViaApi() throws Exception {

        IHyperliquidWebsocketApi api = new HyperliquidWebsocketApi();

        api.connect();
        OrderJson buy = new OrderJson();

        buy.isBuy = true;
        buy.price = "113100";
        buy.size = "0.01";
        buy.reduceOnly = false;
        buy.type = new LimitType(LimitType.TimeInForce.GTC);
        buy.clientOrderId = HyperliquidUtil.encode128BitHex("Hello World 123");

        OrderAction action = new OrderAction();
        action.orders = java.util.Arrays.asList(buy);

        SubmitPostResponse

        submitOrders = api.submitOrders(action);

        logger.info("Submit response: {}", submitOrders);

        Thread.sleep(3000000);
    }

    public static void main(String[] args) throws Exception {
        HyperliquidClient client = new HyperliquidClient("123");
        client.submitOrder(false);
    }
}
