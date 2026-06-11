package com.fueledbychai.okx.common.api.ws.model;

import java.util.List;

public class OkxOrderBookUpdate {

    protected final String instrumentId;
    protected final Long timestamp;
    protected final List<OkxOrderBookLevel> bids;
    protected final List<OkxOrderBookLevel> asks;
    // Populated only on the incremental `books` (L2) channel; null on the snapshot-only `books5`.
    protected final String action;
    protected final Long seqId;
    protected final Long prevSeqId;
    protected final Long checksum;

    public OkxOrderBookUpdate(String instrumentId, Long timestamp, List<OkxOrderBookLevel> bids,
            List<OkxOrderBookLevel> asks) {
        this(instrumentId, timestamp, bids, asks, null, null, null, null);
    }

    public OkxOrderBookUpdate(String instrumentId, Long timestamp, List<OkxOrderBookLevel> bids,
            List<OkxOrderBookLevel> asks, String action, Long seqId, Long prevSeqId, Long checksum) {
        this.instrumentId = instrumentId;
        this.timestamp = timestamp;
        this.bids = bids;
        this.asks = asks;
        this.action = action;
        this.seqId = seqId;
        this.prevSeqId = prevSeqId;
        this.checksum = checksum;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public List<OkxOrderBookLevel> getBids() {
        return bids;
    }

    public List<OkxOrderBookLevel> getAsks() {
        return asks;
    }

    /** {@code "snapshot"} or {@code "update"} on the incremental {@code books} channel; {@code null} on {@code books5}. */
    public String getAction() {
        return action;
    }

    /** Monotonic sequence id of this update (incremental {@code books} channel); {@code null} on {@code books5}. */
    public Long getSeqId() {
        return seqId;
    }

    /** The {@code seqId} this update must follow; {@code null} on {@code books5}, {@code -1} on a snapshot/reset. */
    public Long getPrevSeqId() {
        return prevSeqId;
    }

    /** OKX CRC32 book checksum for this frame; {@code null} on {@code books5}. */
    public Long getChecksum() {
        return checksum;
    }
}
