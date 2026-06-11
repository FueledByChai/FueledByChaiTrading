package com.fueledbychai.datarecording.adapter;

import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.datarecording.OwnEventCategory;
import com.fueledbychai.datarecording.Side;

/** Shared mappings from broker order types to the recording contract's enums. */
final class OwnEventMapping {

    private OwnEventMapping() {
    }

    static Side toSide(TradeDirection direction) {
        if (direction == null) {
            return Side.UNKNOWN;
        }
        return switch (direction) {
            case BUY, BUY_TO_COVER -> Side.BUY;
            case SELL, SELL_SHORT -> Side.SELL;
        };
    }

    /** Map a broker/venue status string to a coarse lifecycle category (name-based, broker-agnostic). */
    static OwnEventCategory categorize(String rawStatus) {
        if (rawStatus == null) {
            return OwnEventCategory.OTHER;
        }
        String u = rawStatus.toUpperCase();
        if (u.contains("PARTIAL")) {
            return OwnEventCategory.PARTIAL_FILL;
        }
        if (u.contains("FILL")) {
            return OwnEventCategory.FILL;
        }
        if (u.contains("CANCEL")) {
            return OwnEventCategory.CANCEL;
        }
        if (u.contains("REJECT")) {
            return OwnEventCategory.REJECT;
        }
        if (u.contains("REPLACE") || u.contains("MODIFY") || u.contains("AMEND")) {
            return OwnEventCategory.REPLACE;
        }
        if (u.contains("SUBMIT") || u.contains("PENDING") || u.contains("NEW") || u.contains("CREATED")) {
            return OwnEventCategory.SUBMIT;
        }
        if (u.contains("ACK") || u.contains("OPEN") || u.contains("WORKING") || u.contains("ACCEPT")
                || u.contains("ACTIVE")) {
            return OwnEventCategory.ACK;
        }
        return OwnEventCategory.OTHER;
    }
}
