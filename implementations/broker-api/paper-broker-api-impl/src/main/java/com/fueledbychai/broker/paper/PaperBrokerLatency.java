package com.fueledbychai.broker.paper;

import java.util.Objects;

/**
 * Latency profile for the paper broker. Holds a base REST/WS round-trip range
 * plus optional per-operation overrides for placeOrder, cancelOrder,
 * modifyOrder and read-only query calls. When a per-op range is unset, the
 * getter falls back to the base REST range so existing profiles keep working.
 *
 * <p>Many DEXes route different operations through different paths — e.g.
 * cancelOrder may use a fast WS or sequencer path while placeOrder goes
 * through a slower REST path — so a single rest range is not realistic.
 */
public class PaperBrokerLatency {

    // Hyperliquid: place/modify go through the slower exchange path; cancels are fast.
    public static final PaperBrokerLatency HYPERLIQUID_LATENCY = builder(900, 2000, 200, 300)
            .place(900, 2000)
            .cancel(150, 350)
            .modify(900, 2000)
            .query(200, 400)
            .build();

    // Paradex: cancels noticeably faster than place/modify on Starknet sequencer.
    public static final PaperBrokerLatency PARDEX_LATENCY = builder(350, 550, 200, 300)
            .place(350, 550)
            .cancel(150, 280)
            .modify(350, 550)
            .query(200, 350)
            .build();

    // Drift: slot-bound; place blocks on Solana confirmation, cancels are quicker.
    public static final PaperBrokerLatency DRIFT_LATENCY = builder(650, 1200, 200, 350)
            .place(650, 1200)
            .cancel(300, 600)
            .modify(650, 1200)
            .query(200, 400)
            .build();

    // Aster: CEX-like; cancels still cheaper than place.
    public static final PaperBrokerLatency ASTER_LATENCY = builder(250, 450, 120, 220)
            .place(250, 450)
            .cancel(100, 200)
            .modify(250, 450)
            .query(120, 250)
            .build();

    private static final int UNSET = -1;

    protected int restLatencyMsMin = 0;
    protected int restLatencyMsMax = 0;
    protected int wsLatencyMsMin = 0;
    protected int wsLatencyMsMax = 0;

    protected int placeLatencyMsMin = UNSET;
    protected int placeLatencyMsMax = UNSET;
    protected int cancelLatencyMsMin = UNSET;
    protected int cancelLatencyMsMax = UNSET;
    protected int modifyLatencyMsMin = UNSET;
    protected int modifyLatencyMsMax = UNSET;
    protected int queryLatencyMsMin = UNSET;
    protected int queryLatencyMsMax = UNSET;

    public PaperBrokerLatency(int restLatencyMsMin, int restLatencyMsMax, int wsLatencyMsMin, int wsLatencyMsMax) {
        this.restLatencyMsMin = restLatencyMsMin;
        this.restLatencyMsMax = restLatencyMsMax;
        this.wsLatencyMsMin = wsLatencyMsMin;
        this.wsLatencyMsMax = wsLatencyMsMax;
    }

    public PaperBrokerLatency(PaperBrokerLatency other) {
        Objects.requireNonNull(other, "other");
        this.restLatencyMsMin = other.restLatencyMsMin;
        this.restLatencyMsMax = other.restLatencyMsMax;
        this.wsLatencyMsMin = other.wsLatencyMsMin;
        this.wsLatencyMsMax = other.wsLatencyMsMax;
        this.placeLatencyMsMin = other.placeLatencyMsMin;
        this.placeLatencyMsMax = other.placeLatencyMsMax;
        this.cancelLatencyMsMin = other.cancelLatencyMsMin;
        this.cancelLatencyMsMax = other.cancelLatencyMsMax;
        this.modifyLatencyMsMin = other.modifyLatencyMsMin;
        this.modifyLatencyMsMax = other.modifyLatencyMsMax;
        this.queryLatencyMsMin = other.queryLatencyMsMin;
        this.queryLatencyMsMax = other.queryLatencyMsMax;
    }

    public static Builder builder(int restLatencyMsMin, int restLatencyMsMax, int wsLatencyMsMin, int wsLatencyMsMax) {
        return new Builder(restLatencyMsMin, restLatencyMsMax, wsLatencyMsMin, wsLatencyMsMax);
    }

    public int getRestLatencyMsMin() {
        return restLatencyMsMin;
    }

    public void setRestLatencyMsMin(int restLatencyMsMin) {
        this.restLatencyMsMin = restLatencyMsMin;
    }

    public int getRestLatencyMsMax() {
        return restLatencyMsMax;
    }

    public void setRestLatencyMsMax(int restLatencyMsMax) {
        this.restLatencyMsMax = restLatencyMsMax;
    }

    public int getWsLatencyMsMin() {
        return wsLatencyMsMin;
    }

    public void setWsLatencyMsMin(int wsLatencyMsMin) {
        this.wsLatencyMsMin = wsLatencyMsMin;
    }

    public int getWsLatencyMsMax() {
        return wsLatencyMsMax;
    }

    public void setWsLatencyMsMax(int wsLatencyMsMax) {
        this.wsLatencyMsMax = wsLatencyMsMax;
    }

    public int getPlaceLatencyMsMin() {
        return placeLatencyMsMin == UNSET ? restLatencyMsMin : placeLatencyMsMin;
    }

    public int getPlaceLatencyMsMax() {
        return placeLatencyMsMax == UNSET ? restLatencyMsMax : placeLatencyMsMax;
    }

    public void setPlaceLatencyMs(int min, int max) {
        this.placeLatencyMsMin = min;
        this.placeLatencyMsMax = max;
    }

    public int getCancelLatencyMsMin() {
        return cancelLatencyMsMin == UNSET ? restLatencyMsMin : cancelLatencyMsMin;
    }

    public int getCancelLatencyMsMax() {
        return cancelLatencyMsMax == UNSET ? restLatencyMsMax : cancelLatencyMsMax;
    }

    public void setCancelLatencyMs(int min, int max) {
        this.cancelLatencyMsMin = min;
        this.cancelLatencyMsMax = max;
    }

    public int getModifyLatencyMsMin() {
        return modifyLatencyMsMin == UNSET ? restLatencyMsMin : modifyLatencyMsMin;
    }

    public int getModifyLatencyMsMax() {
        return modifyLatencyMsMax == UNSET ? restLatencyMsMax : modifyLatencyMsMax;
    }

    public void setModifyLatencyMs(int min, int max) {
        this.modifyLatencyMsMin = min;
        this.modifyLatencyMsMax = max;
    }

    public int getQueryLatencyMsMin() {
        return queryLatencyMsMin == UNSET ? restLatencyMsMin : queryLatencyMsMin;
    }

    public int getQueryLatencyMsMax() {
        return queryLatencyMsMax == UNSET ? restLatencyMsMax : queryLatencyMsMax;
    }

    public void setQueryLatencyMs(int min, int max) {
        this.queryLatencyMsMin = min;
        this.queryLatencyMsMax = max;
    }

    @Override
    public String toString() {
        return "PaperBrokerLatency{rest=[" + restLatencyMsMin + "," + restLatencyMsMax + "], ws=["
                + wsLatencyMsMin + "," + wsLatencyMsMax + "], place=[" + getPlaceLatencyMsMin() + ","
                + getPlaceLatencyMsMax() + "], cancel=[" + getCancelLatencyMsMin() + ","
                + getCancelLatencyMsMax() + "], modify=[" + getModifyLatencyMsMin() + ","
                + getModifyLatencyMsMax() + "], query=[" + getQueryLatencyMsMin() + ","
                + getQueryLatencyMsMax() + "]}";
    }

    public static final class Builder {
        private final PaperBrokerLatency target;

        private Builder(int restMin, int restMax, int wsMin, int wsMax) {
            this.target = new PaperBrokerLatency(restMin, restMax, wsMin, wsMax);
        }

        public Builder place(int min, int max) {
            target.setPlaceLatencyMs(min, max);
            return this;
        }

        public Builder cancel(int min, int max) {
            target.setCancelLatencyMs(min, max);
            return this;
        }

        public Builder modify(int min, int max) {
            target.setModifyLatencyMs(min, max);
            return this;
        }

        public Builder query(int min, int max) {
            target.setQueryLatencyMs(min, max);
            return this;
        }

        public PaperBrokerLatency build() {
            return new PaperBrokerLatency(target);
        }
    }
}
