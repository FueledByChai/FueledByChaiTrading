package com.fueledbychai.broker.hibachi;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.hibachi.common.api.HibachiContract;
import com.fueledbychai.hibachi.common.api.order.HibachiOrderFlag;
import com.fueledbychai.hibachi.common.api.order.HibachiOrderType;
import com.fueledbychai.hibachi.common.api.order.HibachiSide;
import com.fueledbychai.hibachi.common.api.signer.HibachiPayloadPacker;
import com.fueledbychai.hibachi.common.api.signer.IHibachiSigner;

/**
 * Translates {@link OrderTicket}s to Hibachi trade-WebSocket payloads + signatures.
 *
 * <p>For PLACE/MODIFY: produces (signedBytes, jsonParams) where signedBytes is the byte
 * layout from {@link HibachiPayloadPacker} and jsonParams is the trade-WS {@code params}
 * map (full-precision strings via {@link BigDecimal#toPlainString()}).
 */
public class HibachiTranslator {

    private static final Logger logger = LoggerFactory.getLogger(HibachiTranslator.class);

    public static class SignedRequest {
        public final byte[] signedBytes;
        public final Map<String, Object> params;
        public final String signature;

        public SignedRequest(byte[] signedBytes, Map<String, Object> params, String signature) {
            this.signedBytes = signedBytes;
            this.params = params;
            this.signature = signature;
        }
    }

    public SignedRequest translatePlace(OrderTicket order,
                                        HibachiContract contract,
                                        long accountId,
                                        long nonce,
                                        BigDecimal maxFeesPercent,
                                        IHibachiSigner signer) {
        if (order == null) throw new IllegalArgumentException("order is required");
        if (contract == null) throw new IllegalArgumentException("contract is required");
        if (signer == null) throw new IllegalArgumentException("signer is required");

        HibachiSide side = toSide(order.getTradeDirection());
        HibachiOrderType orderType = toOrderType(order);
        BigDecimal price = orderType == HibachiOrderType.LIMIT ? order.getLimitPrice() : null;
        BigDecimal qty = order.getSize();
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("order size must be > 0");
        }

        byte[] signedBytes = HibachiPayloadPacker.packPlaceOrder(nonce, contract, qty, side, price, maxFeesPercent);
        String signature = signer.sign(signedBytes);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nonce", nonce);
        params.put("symbol", contract.getSymbol());
        params.put("quantity", qty.toPlainString());
        params.put("orderType", orderType.getWireValue());
        params.put("side", side.getWireValue());
        params.put("maxFeesPercent", maxFeesPercent.toPlainString());
        params.put("signature", signature);
        if (price != null) {
            params.put("price", price.toPlainString());
        }
        HibachiOrderFlag flag = toFlag(order);
        if (flag != null) {
            params.put("orderFlags", flag.getWireValue());
        }
        params.put("accountId", accountId);
        return new SignedRequest(signedBytes, params, signature);
    }

    public SignedRequest translateModify(OrderTicket order,
                                         HibachiContract contract,
                                         long accountId,
                                         long nonce,
                                         BigDecimal maxFeesPercent,
                                         IHibachiSigner signer) {
        if (order == null) throw new IllegalArgumentException("order is required");
        if (contract == null) throw new IllegalArgumentException("contract is required");
        if (signer == null) throw new IllegalArgumentException("signer is required");

        HibachiSide side = toSide(order.getTradeDirection());
        HibachiOrderType orderType = toOrderType(order);
        BigDecimal price = orderType == HibachiOrderType.LIMIT ? order.getLimitPrice() : null;
        BigDecimal qty = order.getSize();
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("order size must be > 0");
        }

        byte[] signedBytes = HibachiPayloadPacker.packPlaceOrder(nonce, contract, qty, side, price, maxFeesPercent);
        String signature = signer.sign(signedBytes);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nonce", nonce);
        params.put("maxFeesPercent", maxFeesPercent.toPlainString());
        if (order.getOrderId() != null && !order.getOrderId().isBlank()) {
            params.put("orderId", order.getOrderId());
        }
        // SDK quirk: emit both `quantity` and `updatedQuantity` (same value); same for price.
        params.put("quantity", qty.toPlainString());
        params.put("updatedQuantity", qty.toPlainString());
        if (price != null) {
            params.put("price", price.toPlainString());
            params.put("updatedPrice", price.toPlainString());
        }
        HibachiOrderFlag flag = toFlag(order);
        if (flag != null) {
            params.put("orderFlags", flag.getWireValue());
        }
        params.put("accountId", accountId);
        return new SignedRequest(signedBytes, params, signature);
    }

    public SignedRequest translateCancel(OrderTicket order,
                                         long accountId,
                                         Long nonce,
                                         IHibachiSigner signer) {
        if (order == null) throw new IllegalArgumentException("order is required");
        if (signer == null) throw new IllegalArgumentException("signer is required");

        Long orderId = parseLongOrNull(order.getOrderId());
        byte[] signedBytes = HibachiPayloadPacker.packCancelOrder(orderId, nonce);
        String signature = signer.sign(signedBytes);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("accountId", accountId);
        if (orderId != null) {
            params.put("orderId", String.valueOf(orderId));
        } else if (nonce != null) {
            params.put("nonce", String.valueOf(nonce));
        }
        return new SignedRequest(signedBytes, params, signature);
    }

    public HibachiSide toSide(TradeDirection direction) {
        if (direction == null) {
            throw new IllegalArgumentException("trade direction is required");
        }
        // Map LONG/BUY → BID, SHORT/SELL → ASK.
        switch (direction) {
            case BUY:
            case BUY_TO_COVER:
                return HibachiSide.BID;
            case SELL:
            case SELL_SHORT:
                return HibachiSide.ASK;
            default:
                throw new IllegalArgumentException("Unsupported trade direction: " + direction);
        }
    }

    public HibachiOrderType toOrderType(OrderTicket order) {
        if (order == null || order.getType() == null) {
            return HibachiOrderType.MARKET;
        }
        switch (order.getType()) {
            case LIMIT:
            case STOP_LIMIT:
                return HibachiOrderType.LIMIT;
            case MARKET:
            case STOP:
            default:
                return HibachiOrderType.MARKET;
        }
    }

    public HibachiOrderFlag toFlag(OrderTicket order) {
        if (order == null) {
            return null;
        }
        // Hibachi's `orderFlags` is a single value, not a list. Pick by priority and warn
        // if the caller asked for more than one — POST_ONLY beats IOC (they're contradictory)
        // and both beat REDUCE_ONLY (orthogonal but only one slot on the wire).
        boolean postOnly = hasModifier(order, OrderTicket.Modifier.POST_ONLY);
        boolean ioc = order.getDuration() == OrderTicket.Duration.IMMEDIATE_OR_CANCEL
                || order.getDuration() == OrderTicket.Duration.FILL_OR_KILL;
        boolean reduceOnly = hasModifier(order, OrderTicket.Modifier.REDUCE_ONLY);

        int set = (postOnly ? 1 : 0) + (ioc ? 1 : 0) + (reduceOnly ? 1 : 0);
        if (set > 1) {
            logger.warn("Hibachi accepts only one orderFlag; got postOnly={} ioc={} reduceOnly={} — sending POST_ONLY > IOC > REDUCE_ONLY",
                    postOnly, ioc, reduceOnly);
        }
        if (postOnly) return HibachiOrderFlag.POST_ONLY;
        if (ioc) return HibachiOrderFlag.IOC;
        if (reduceOnly) return HibachiOrderFlag.REDUCE_ONLY;
        return null;
    }

    private static boolean hasModifier(OrderTicket order, OrderTicket.Modifier modifier) {
        java.util.List<OrderTicket.Modifier> mods = order.getModifiers();
        return mods != null && mods.contains(modifier);
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
