package com.fueledbychai.grvt.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fueledbychai.grvt.common.api.model.GrvtInstrument;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;
import com.fueledbychai.grvt.common.api.model.GrvtOrderLeg;
import com.fueledbychai.grvt.common.api.model.GrvtTimeInForce;

/**
 * Golden-vector tests for {@link GrvtEip712OrderSigner}, ported verbatim from the GRVT Python SDK
 * ({@code tests/pysdk/test_grvt_raw_signing.py::test_sign_order_table}). A byte-exact match of
 * r/s/v against these fixtures proves our EIP-712 implementation is wire-compatible with GRVT
 * without making any network call.
 */
class GrvtEip712OrderSignerTest {

    private static final String PRIVATE_KEY =
            "f7934647276a6e1fa0af3f4467b4b8ddaf45d25a7368fa1a295eef49a446819d";
    private static final String SUB_ACCOUNT_ID = "8289849667772468";
    private static final String EXPIRY = "1730800479321350000";
    private static final long NONCE = 828700936L;
    // GrvtEnv.TESTNET in the SDK fixture -> chain id 326.
    private static final int CHAIN_ID = 326;

    private static final Map<String, GrvtInstrument> INSTRUMENTS = buildInstruments();

    @Test
    void signsThreeDecimalOrderToGoldenVector() {
        GrvtOrder order = sellOrder("1.013", "68900.5");
        new GrvtEip712OrderSigner(PRIVATE_KEY).signOrder(order, INSTRUMENTS, CHAIN_ID);

        assertEquals("0xb00512d986a718b15136a8ba23de1c1ec84bbdb9958629cbbe4909bae620bb04",
                order.getSignature().getR());
        assertEquals("0x79f706de61c68cc14d7734594b5d8689df2b2a7b25951f9a3f61d799f4327ffc",
                order.getSignature().getS());
        assertEquals(28, order.getSignature().getV());
    }

    @Test
    void signsNineDecimalOrderToGoldenVector() {
        GrvtOrder order = sellOrder("1.123123123", "68900.777123479");
        new GrvtEip712OrderSigner(PRIVATE_KEY).signOrder(order, INSTRUMENTS, CHAIN_ID);

        assertEquals("0x365ec79d299c8bcd5f2acff89faf741a90ca02a4b8a6b1b1a5d4f3d16130f9f0",
                order.getSignature().getR());
        assertEquals("0x465129bca7855f008ea5bc22fe3ee630e4a8e3b9b99c1745631deef29957048a",
                order.getSignature().getS());
        assertEquals(28, order.getSignature().getV());
    }

    @Test
    void truncatesSubNanoPrecisionToGoldenVector() {
        // size/price carry > 9 decimals; the SDK truncates to 9 decimals, yielding the same
        // signature as the 9-decimal case above.
        GrvtOrder order = sellOrder("1.1231231239", "68900.7771234799");
        new GrvtEip712OrderSigner(PRIVATE_KEY).signOrder(order, INSTRUMENTS, CHAIN_ID);

        assertEquals("0x365ec79d299c8bcd5f2acff89faf741a90ca02a4b8a6b1b1a5d4f3d16130f9f0",
                order.getSignature().getR());
        assertEquals("0x465129bca7855f008ea5bc22fe3ee630e4a8e3b9b99c1745631deef29957048a",
                order.getSignature().getS());
        assertEquals(28, order.getSignature().getV());
    }

    private static GrvtOrder sellOrder(String size, String price) {
        GrvtOrder order = new GrvtOrder();
        order.setSubAccountId(SUB_ACCOUNT_ID);
        order.setMarket(false);
        order.setTimeInForce(GrvtTimeInForce.GOOD_TILL_TIME);
        order.setPostOnly(false);
        order.setReduceOnly(false);
        order.setLegs(List.of(new GrvtOrderLeg("BTC_USDT_Perp", new BigDecimal(size), new BigDecimal(price), false)));
        order.getSignature().setNonce(NONCE);
        order.getSignature().setExpiration(EXPIRY);
        order.setClientOrderId("1");
        return order;
    }

    private static Map<String, GrvtInstrument> buildInstruments() {
        GrvtInstrument btc = new GrvtInstrument();
        btc.setInstrument("BTC_USDT_Perp");
        btc.setInstrumentHash("0x030501");
        btc.setBase("BTC");
        btc.setQuote("USDT");
        btc.setKind("PERPETUAL");
        btc.setBaseDecimals(9);
        btc.setQuoteDecimals(9);
        return Collections.singletonMap("BTC_USDT_Perp", btc);
    }
}
