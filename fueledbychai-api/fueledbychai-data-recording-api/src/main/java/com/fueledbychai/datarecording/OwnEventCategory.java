package com.fueledbychai.datarecording;

/**
 * Coarse classification of an own-order lifecycle event, normalized across brokers. The
 * raw venue/broker status string is preserved alongside it in
 * {@link RecordedOwnEvent#rawStatus()} for exact analysis.
 */
public enum OwnEventCategory {
    SUBMIT, ACK, PARTIAL_FILL, FILL, CANCEL, REJECT, REPLACE, OTHER
}
