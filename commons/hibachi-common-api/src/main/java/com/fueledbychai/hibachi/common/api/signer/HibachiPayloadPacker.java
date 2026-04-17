package com.fueledbychai.hibachi.common.api.signer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.fueledbychai.hibachi.common.api.HibachiContract;
import com.fueledbychai.hibachi.common.api.order.HibachiSide;

/**
 * Assembles the raw binary payloads that Hibachi signs.
 *
 * <p>All fields are big-endian. Layout ported from the Python SDK at
 * {@code hibachi_xyz/api.py:__create_or_update_order_payload} (api.py:1989-2039) and
 * {@code __cancel_order_payload} (api.py:2173-2193).
 *
 * <h2>Place / Modify order (identical bytes)</h2>
 * <ol>
 *   <li>{@code nonce} — 8 bytes (u64 BE)</li>
 *   <li>{@code contractId} — 4 bytes (u32 BE)</li>
 *   <li>{@code quantity} scaled — 8 bytes (u64 BE) = {@code int(quantity * 10^underlyingDecimals)}</li>
 *   <li>{@code side} — 4 bytes (u32 BE): ASK=0, BID=1</li>
 *   <li>{@code price} scaled — 8 bytes (u64 BE) = {@code int(price * 2^32 * 10^(settlementDecimals - underlyingDecimals))}.
 *       <b>Omitted entirely for MARKET orders</b> (not zero-padded).</li>
 *   <li>{@code maxFeesPercent} scaled — 8 bytes (u64 BE) = {@code int(maxFeesPercent * 10^8)}. Scale is hard-coded 1e8.</li>
 * </ol>
 *
 * <p>Limit payload = 40 bytes. Market payload = 32 bytes.
 *
 * <h2>Cancel order</h2>
 * <p>8 bytes, either {@code orderId} or {@code nonce} (u64 BE). No tag byte — the server
 * distinguishes by the JSON envelope.
 *
 * <p><b>{@code triggerPrice}, {@code triggerDirection}, {@code creationDeadline},
 * {@code orderFlags}, {@code twapConfig}, and {@code parentOrder} are NEVER in the signed bytes</b>
 * — they are only JSON envelope fields. See api.py:2099-2110.
 */
public final class HibachiPayloadPacker {

    private static final BigDecimal POW_2_32 = new BigDecimal(BigInteger.ONE.shiftLeft(32));
    private static final BigDecimal MAX_FEES_SCALE = new BigDecimal(BigInteger.TEN.pow(8));

    private HibachiPayloadPacker() {
    }

    /**
     * Packs a place- or modify-order payload.
     *
     * @param nonce          strictly monotonic per account
     * @param contract       from {@code /market/exchange-info}
     * @param quantity       base quantity (scaled by underlyingDecimals)
     * @param side           BID or ASK
     * @param price          limit price; {@code null} for MARKET orders (price bytes are omitted)
     * @param maxFeesPercent max fees as a percent (e.g. 0.5 for 0.5%); scaled by 1e8
     * @return packed bytes (40 if price != null, else 32)
     */
    public static byte[] packPlaceOrder(long nonce,
                                        HibachiContract contract,
                                        BigDecimal quantity,
                                        HibachiSide side,
                                        BigDecimal price,
                                        BigDecimal maxFeesPercent) {
        if (contract == null) throw new IllegalArgumentException("contract is required");
        if (quantity == null) throw new IllegalArgumentException("quantity is required");
        if (side == null) throw new IllegalArgumentException("side is required");
        if (maxFeesPercent == null) throw new IllegalArgumentException("maxFeesPercent is required");

        long quantityScaled = scaleQuantity(quantity, contract);
        long maxFeesScaled = scaleMaxFeesPercent(maxFeesPercent);
        Long priceScaled = price == null ? null : scalePrice(price, contract);

        int size = priceScaled == null ? 32 : 40;
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        buf.putLong(nonce);
        buf.putInt(contract.getId());
        buf.putLong(quantityScaled);
        buf.putInt(side.getByteValue());
        if (priceScaled != null) {
            buf.putLong(priceScaled);
        }
        buf.putLong(maxFeesScaled);
        return buf.array();
    }

    /**
     * Packs a cancel-order payload.
     *
     * <p>8 bytes, either {@code orderId} or {@code nonce}. If both are non-null, {@code orderId}
     * takes precedence (matches Python SDK precedence at api.py:2173-2193).
     */
    public static byte[] packCancelOrder(Long orderId, Long nonce) {
        long value;
        if (orderId != null) {
            value = orderId;
        } else if (nonce != null) {
            value = nonce;
        } else {
            throw new IllegalArgumentException("either orderId or nonce must be provided");
        }
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array();
    }

    /**
     * Packs a cancel-all (orders.cancel) payload — 8-byte big-endian nonce.
     */
    public static byte[] packCancelAll(long nonce) {
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(nonce).array();
    }

    /**
     * Scales quantity: {@code int(quantity * 10^underlyingDecimals)}, truncating toward zero.
     * Matches api.py:2017-2019.
     */
    public static long scaleQuantity(BigDecimal quantity, HibachiContract contract) {
        BigDecimal multiplier = BigDecimal.TEN.pow(contract.getUnderlyingDecimals());
        BigDecimal scaled = quantity.multiply(multiplier);
        return toLongTruncating(scaled, "quantity");
    }

    /**
     * Scales price: {@code int(price * 2^32 * 10^(settlementDecimals - underlyingDecimals))},
     * truncating toward zero. Matches api.py:211-229 ({@code price_to_bytes}).
     */
    public static long scalePrice(BigDecimal price, HibachiContract contract) {
        int decimalDelta = contract.getSettlementDecimals() - contract.getUnderlyingDecimals();
        BigDecimal decimalShift;
        if (decimalDelta >= 0) {
            decimalShift = BigDecimal.TEN.pow(decimalDelta);
        } else {
            decimalShift = BigDecimal.ONE.divide(BigDecimal.TEN.pow(-decimalDelta));
        }
        BigDecimal scaled = price.multiply(POW_2_32).multiply(decimalShift);
        return toLongTruncating(scaled, "price");
    }

    /**
     * Scales max-fees-percent: {@code int(maxFeesPercent * 10^8)}, truncating toward zero.
     * Matches api.py:2020-2022 — the 1e8 scale is hard-coded, NOT derived from the contract.
     */
    public static long scaleMaxFeesPercent(BigDecimal maxFeesPercent) {
        return toLongTruncating(maxFeesPercent.multiply(MAX_FEES_SCALE), "maxFeesPercent");
    }

    private static long toLongTruncating(BigDecimal value, String field) {
        BigInteger asInt = value.setScale(0, RoundingMode.DOWN).toBigInteger();
        if (asInt.bitLength() > 63) {
            throw new IllegalArgumentException(field + " overflows 64-bit signed: " + asInt);
        }
        return asInt.longValueExact();
    }
}
