package com.fueledbychai.qfex.common.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One entry from {@code GET /refdata}. QFEX publishes no minimum-notional
 * field; orders below the venue's threshold are rejected with
 * {@code REJECTED_LESS_THAN_MIN_NOTIONAL}.
 */
public record QfexContract(
        String symbol,
        String baseAsset,
        String quoteAsset,
        String status,
        String productCategory,
        BigDecimal tickSize,
        BigDecimal lotSize,
        BigDecimal minQuantity,
        BigDecimal maxQuantity,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int maxLeverage) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public static QfexContract fromJson(JsonNode n) {
        return new QfexContract(
                text(n, "symbol"),
                text(n, "base_asset"),
                text(n, "quote_asset"),
                text(n, "status"),
                text(n, "product_category"),
                decimal(n, "tick_size"),
                decimal(n, "lot_size"),
                decimal(n, "min_quantity"),
                decimal(n, "max_quantity"),
                decimal(n, "min_price"),
                decimal(n, "max_price"),
                n.path("default_max_leverage").asInt(0));
    }

    static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    static BigDecimal decimal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
