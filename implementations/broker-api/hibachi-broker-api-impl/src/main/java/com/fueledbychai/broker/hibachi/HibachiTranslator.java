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
                                        long creationDeadlineMicros,
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
        // creationDeadline is JSON-only (not in signed bytes). Microseconds
        // since epoch — same unit as nonce. The venue compares it against its
        // process_time in micros and rejects with errorCode=4 / "Creation
        // deadline exceeded" when smaller; a far-future value lets the order
        // rest until normal cancel/fill.
        if (creationDeadlineMicros > 0L) {
            params.put("creationDeadline", creationDeadlineMicros);
        }
        params.put("accountId", accountId);
        // Echo the caller's clientOrderId as Hibachi's optional `clientId`.
        // Without this, every account-WS event ({order_creation,
        // order_matched, order_cancellation, ...}) arrives with
        // clientId=null, and our matcher in HibachiBroker can't find the
        // OrderTicket in the registry — causing place/modify/cancel state
        // to silently diverge from reality. Hibachi's clientId rules:
        // 1-32 chars, [A-Za-z0-9-]. chaiwala uses millisecond timestamps
        // which fit cleanly.
        String clientId = sanitizeClientId(order.getClientOrderId());
        if (clientId != null) {
            params.put("clientId", clientId);
        }
        return new SignedRequest(signedBytes, params, signature);
    }

    /**
     * Hibachi rejects clientIds outside [1,32] chars or with chars other than
     * ASCII letters/digits/dash. We don't want to fail the place because of a
     * cosmetic mismatch — return null to skip the field if the caller's id
     * isn't compliant. chaiwala's millisecond-timestamp client ids satisfy
     * the rule by construction; this is defensive.
     */
    private static String sanitizeClientId(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 32) return null;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-';
            if (!ok) return null;
        }
        return trimmed;
    }

    public SignedRequest translateModify(OrderTicket order,
                                         HibachiContract contract,
                                         long accountId,
                                         long nonce,
                                         long creationDeadlineMicros,
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

        // Hibachi modify body per the venue's example payload:
        //   { orderId|clientId, accountId, nonce, updatedQuantity,
        //     updatedPrice, maxFeesPercent, signature }
        // The docs prose says "you can only update quantity and price" but the
        // wire field names are `updatedQuantity` / `updatedPrice`, not
        // `quantity` / `price`. accountId is required despite not being listed
        // explicitly in the docs body section — Hibachi returns errorCode=4
        // ("Missing accountId") if absent.
        Map<String, Object> params = new LinkedHashMap<>();
        if (order.getOrderId() != null && !order.getOrderId().isBlank()) {
            params.put("orderId", order.getOrderId());
        } else {
            String clientId = sanitizeClientId(order.getClientOrderId());
            if (clientId != null) {
                params.put("clientId", clientId);
            }
        }
        params.put("accountId", accountId);
        params.put("nonce", nonce);
        params.put("updatedQuantity", qty.toPlainString());
        if (price != null) {
            params.put("updatedPrice", price.toPlainString());
        }
        params.put("maxFeesPercent", maxFeesPercent.toPlainString());
        // Refresh the order's deadline on every modify so the venue doesn't
        // self-cancel mid-life. JSON-only, not in signed bytes.
        if (creationDeadlineMicros > 0L) {
            params.put("creationDeadline", creationDeadlineMicros);
        }
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
