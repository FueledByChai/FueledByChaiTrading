package com.fueledbychai.lighter.common.api.signer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterOrderType;
import com.fueledbychai.lighter.common.api.order.LighterTimeInForce;

class LighterNativeTransactionSignerTest {

    private static final String TEST_REST_URL = "https://testnet.zklighter.elliot.ai/api/v1";
    private static final String TEST_PRIVATE_KEY = "0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728";
    private static final int CONFIGURED_API_KEY_INDEX = 3;
    private static final long CONFIGURED_ACCOUNT_INDEX = 9L;
    private static final long FIXED_NOW_MILLIS = 1_700_000_000_000L;
    private static final long FIXED_NONCE = 777L;

    @Test
    void signCreateOrderUsesConfiguredAccountApiKeyAndComputedDefaults() {
        TestableSigner signer = new TestableSigner();

        LighterCreateOrderRequest request = new LighterCreateOrderRequest();
        request.setMarketIndex(2);
        request.setClientOrderIndex(11L);
        request.setBaseAmount(1_500L);
        request.setPrice(90_000);
        request.setAsk(false);
        request.setOrderType(LighterOrderType.LIMIT);
        request.setTimeInForce(LighterTimeInForce.GTT);

        LighterSignedTransaction signed = signer.signCreateOrder(request);

        assertEquals(14, signed.getTxType());
        assertHex(signed.getTxHash(), 80);

        JSONObject txInfo = signed.getTxInfo();
        assertEquals(CONFIGURED_ACCOUNT_INDEX, txInfo.getLong("AccountIndex"));
        assertEquals(CONFIGURED_API_KEY_INDEX, txInfo.getInt("ApiKeyIndex"));
        assertEquals(2, txInfo.getInt("MarketIndex"));
        assertEquals(11L, txInfo.getLong("ClientOrderIndex"));
        assertEquals(1_500L, txInfo.getLong("BaseAmount"));
        assertEquals(90_000L, txInfo.getLong("Price"));
        assertEquals(FIXED_NONCE, txInfo.getLong("Nonce"));
        assertEquals(FIXED_NOW_MILLIS + 599_000L, txInfo.getLong("ExpiredAt"));
        assertEquals(FIXED_NOW_MILLIS + 2_419_200_000L, txInfo.getLong("OrderExpiry"));
        assertNotNull(txInfo.getString("Sig"));
        assertFalse(txInfo.getString("Sig").isBlank());
    }

    @Test
    void marketOrderHelperSetsOrderExpiryZeroForIocMarketOrder() {
        TestableSigner signer = new TestableSigner();
        LighterCreateOrderRequest request = LighterCreateOrderRequest.marketOrder(2, 12L, 500L, 91_000, true);
        request.setNonce(100L);

        LighterSignedTransaction signed = signer.signCreateOrder(request);

        assertEquals(14, signed.getTxType());
        assertEquals(0L, signed.getTxInfo().getLong("OrderExpiry"));
        assertEquals(0, signed.getTxInfo().getInt("TimeInForce"));
        assertEquals(1, signed.getTxInfo().getInt("Type"));
    }

    @Test
    void signCancelOrderProducesL2CancelOrderShape() {
        TestableSigner signer = new TestableSigner();

        LighterCancelOrderRequest request = new LighterCancelOrderRequest();
        request.setMarketIndex(4);
        request.setOrderIndex(123_456L);

        LighterSignedTransaction signed = signer.signCancelOrder(request);

        assertEquals(15, signed.getTxType());
        assertHex(signed.getTxHash(), 80);

        JSONObject txInfo = signed.getTxInfo();
        assertEquals(CONFIGURED_ACCOUNT_INDEX, txInfo.getLong("AccountIndex"));
        assertEquals(CONFIGURED_API_KEY_INDEX, txInfo.getInt("ApiKeyIndex"));
        assertEquals(4, txInfo.getInt("MarketIndex"));
        assertEquals(123_456L, txInfo.getLong("Index"));
        assertEquals(FIXED_NONCE, txInfo.getLong("Nonce"));
        assertNotNull(txInfo.getString("Sig"));
    }

    @Test
    void signModifyOrderUsesReducedLighterGoPayloadShape() {
        TestableSigner signer = new TestableSigner();

        LighterModifyOrderRequest request = new LighterModifyOrderRequest();
        request.setMarketIndex(5);
        request.setOrderIndex(222L);
        request.setBaseAmount(1_250L);
        request.setPrice(93_000);
        request.setTriggerPrice(92_500);
        request.setAsk(true);
        request.setReduceOnly(true);
        request.setOrderType(LighterOrderType.LIMIT);
        request.setTimeInForce(LighterTimeInForce.GTT);

        LighterSignedTransaction signed = signer.signModifyOrder(request);

        assertEquals(17, signed.getTxType());
        assertHex(signed.getTxHash(), 80);

        JSONObject txInfo = signed.getTxInfo();
        assertEquals(CONFIGURED_ACCOUNT_INDEX, txInfo.getLong("AccountIndex"));
        assertEquals(CONFIGURED_API_KEY_INDEX, txInfo.getInt("ApiKeyIndex"));
        assertEquals(5, txInfo.getInt("MarketIndex"));
        assertEquals(222L, txInfo.getLong("Index"));
        assertEquals(1_250L, txInfo.getLong("BaseAmount"));
        assertEquals(93_000L, txInfo.getLong("Price"));
        assertEquals(92_500L, txInfo.getLong("TriggerPrice"));
        assertFalse(txInfo.has("IsAsk"));
        assertFalse(txInfo.has("Type"));
        assertFalse(txInfo.has("TimeInForce"));
        assertFalse(txInfo.has("ReduceOnly"));
        assertFalse(txInfo.has("OrderExpiry"));
    }

    @Test
    void createAuthTokenWithExpiryBuildsExpectedMessagePrefix() {
        TestableSigner signer = new TestableSigner();

        long timestamp = 1_700_000_000L;
        long expiry = timestamp + 600L;

        String token = signer.createAuthTokenWithExpiry(timestamp, expiry,
                LighterCreateOrderRequest.DEFAULT_API_KEY_INDEX,
                LighterCreateOrderRequest.DEFAULT_ACCOUNT_INDEX);

        String[] parts = token.split(":");
        assertEquals(4, parts.length);
        assertEquals(Long.toString(expiry), parts[0]);
        assertEquals(Long.toString(CONFIGURED_ACCOUNT_INDEX), parts[1]);
        assertEquals(Integer.toString(CONFIGURED_API_KEY_INDEX), parts[2]);
        assertTrue(parts[3].matches("[0-9a-f]{160}"));
    }

    @Test
    void signerRejectsDifferentAccountOrApiKeyPair() {
        TestableSigner signer = new TestableSigner();

        LighterCancelOrderRequest request = new LighterCancelOrderRequest();
        request.setMarketIndex(1);
        request.setOrderIndex(1L);
        request.setApiKeyIndex(44);

        assertThrows(IllegalArgumentException.class, () -> signer.signCancelOrder(request));
    }

    private void assertHex(String value, int expectedLength) {
        assertNotNull(value);
        assertEquals(expectedLength, value.length());
        assertTrue(value.matches("[0-9a-f]+"));
    }

    private static final class TestableSigner extends LighterNativeTransactionSigner {

        TestableSigner() {
            super(TEST_REST_URL, TEST_PRIVATE_KEY, CONFIGURED_API_KEY_INDEX, CONFIGURED_ACCOUNT_INDEX,
                    new SecureRandom());
        }

        @Override
        protected long nowMillis() {
            return FIXED_NOW_MILLIS;
        }

        @Override
        protected long fetchNextNonce(long accountIndex, int apiKeyIndex) {
            return FIXED_NONCE;
        }
    }
}
