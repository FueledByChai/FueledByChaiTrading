package com.fueledbychai.hibachi.common.api.signer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import com.fueledbychai.hibachi.common.api.HibachiContract;
import com.fueledbychai.hibachi.common.api.order.HibachiSide;

/**
 * Bit-for-bit regression tests for {@link HibachiPayloadPacker} against the Python SDK
 * at {@code /tmp/hibachi_sdk/python/hibachi_xyz/api.py}.
 */
class HibachiPayloadPackerTest {

    private static final HibachiContract ETH_USDT_P = HibachiContract.builder()
            .id(1)
            .symbol("ETH/USDT-P")
            .underlyingDecimals(9)
            .settlementDecimals(6)
            .build();

    @Test
    void packPlaceOrder_limitBid_matchesHandComputedPayload() {
        // Inputs: contract.id=1, underlyingDecimals=9, settlementDecimals=6,
        // nonce=1, qty=0.01, price=2500.0, side=BID, maxFeesPercent=0.5
        byte[] bytes = HibachiPayloadPacker.packPlaceOrder(
                1L,
                ETH_USDT_P,
                new BigDecimal("0.01"),
                HibachiSide.BID,
                new BigDecimal("2500.0"),
                new BigDecimal("0.5"));

        // Expected 40-byte payload:
        //   nonce:    0000000000000001
        //   contract: 00000001
        //   quantity: 0000000000989680   (0.01 * 10^9 = 10_000_000 = 0x989680)
        //   side:     00000001           (BID=1)
        //   price:    0000000280000000   (2500 * 2^32 * 10^-3 = 10_737_418_240 = 0x280000000)
        //   maxFees:  0000000002faf080   (0.5 * 10^8 = 50_000_000 = 0x2faf080)
        String expected = "000000000000000100000001000000000098968000000001000000028000000000000000" +
                "02faf080";
        assertEquals(expected, HexFormat.of().formatHex(bytes));
        assertEquals(40, bytes.length);
    }

    @Test
    void packPlaceOrder_marketAsk_omitsPriceBytes_32ByteLength() {
        byte[] bytes = HibachiPayloadPacker.packPlaceOrder(
                1L,
                ETH_USDT_P,
                new BigDecimal("0.01"),
                HibachiSide.ASK,
                null,
                new BigDecimal("0.5"));
        // Market orders OMIT price bytes entirely — NOT zero-padded.
        assertEquals(32, bytes.length);
        String expected = "00000000000000010000000100000000009896800000000000000000" +
                "02faf080";
        assertEquals(expected, HexFormat.of().formatHex(bytes));
    }

    @Test
    void packCancelOrder_orderId_yields8BytesBE() {
        byte[] bytes = HibachiPayloadPacker.packCancelOrder(12345L, null);
        assertEquals(8, bytes.length);
        assertEquals("0000000000003039", HexFormat.of().formatHex(bytes));
    }

    @Test
    void packCancelOrder_nonceFallback_yields8BytesBE() {
        byte[] bytes = HibachiPayloadPacker.packCancelOrder(null, 1234567890L);
        assertEquals(8, bytes.length);
        assertEquals("00000000499602d2", HexFormat.of().formatHex(bytes));
    }

    @Test
    void packCancelOrder_orderIdWinsOverNonce() {
        byte[] bytes = HibachiPayloadPacker.packCancelOrder(1L, 2L);
        assertEquals("0000000000000001", HexFormat.of().formatHex(bytes));
    }

    @Test
    void packCancelOrder_bothNull_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> HibachiPayloadPacker.packCancelOrder(null, null));
    }

    @Test
    void scaleQuantity_truncatesTowardZero() {
        HibachiContract c = HibachiContract.builder().id(1).underlyingDecimals(9).settlementDecimals(6).build();
        assertEquals(10_000_000L, HibachiPayloadPacker.scaleQuantity(new BigDecimal("0.01"), c));
        // 0.0123456789 * 1e9 = 12_345_678.9 → truncate → 12_345_678
        assertEquals(12_345_678L, HibachiPayloadPacker.scaleQuantity(new BigDecimal("0.0123456789"), c));
    }

    @Test
    void scalePrice_appliesFixedPointAndDecimalDelta() {
        HibachiContract c = HibachiContract.builder().id(1).underlyingDecimals(9).settlementDecimals(6).build();
        // 2500 * 2^32 * 10^-3 = 10_737_418_240
        assertEquals(10_737_418_240L, HibachiPayloadPacker.scalePrice(new BigDecimal("2500.0"), c));
    }

    @Test
    void scaleMaxFeesPercent_hardCoded1e8() {
        assertEquals(50_000_000L, HibachiPayloadPacker.scaleMaxFeesPercent(new BigDecimal("0.5")));
        assertEquals(100_000_000L, HibachiPayloadPacker.scaleMaxFeesPercent(new BigDecimal("1")));
        assertEquals(25_000_000L, HibachiPayloadPacker.scaleMaxFeesPercent(new BigDecimal("0.25")));
    }

    @Test
    void packPlaceOrder_modifyOrderHasIdenticalLayout() {
        // Modify reuses the same packer (api.py:2114-2171 → __create_or_update_order_payload).
        byte[] place = HibachiPayloadPacker.packPlaceOrder(1L, ETH_USDT_P,
                new BigDecimal("0.02"), HibachiSide.BID, new BigDecimal("2600.0"), new BigDecimal("0.5"));
        // A modify with the same inputs produces the same bytes — identity check.
        byte[] modify = HibachiPayloadPacker.packPlaceOrder(1L, ETH_USDT_P,
                new BigDecimal("0.02"), HibachiSide.BID, new BigDecimal("2600.0"), new BigDecimal("0.5"));
        assertEquals(HexFormat.of().formatHex(place), HexFormat.of().formatHex(modify));
    }
}
