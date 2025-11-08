package com.fueledbychai.broker.paper;

public class PaperBrokerLatency {
    public static final PaperBrokerLatency PARDEX_LATENCY = new PaperBrokerLatency(350, 550, 200, 300);
    public static final PaperBrokerLatency HYPERLIQUID_LATENCY = new PaperBrokerLatency(900, 2000, 200, 300);

    protected int restLatencyMsMin = 0;
    protected int restLatencyMsMax = 0;
    protected int wsLatencyMsMin = 0;
    protected int wsLatencyMsMax = 0;

    public PaperBrokerLatency(int restLatencyMsMin, int restLatencyMsMax, int wsLatencyMsMin, int wsLatencyMsMax) {
        this.restLatencyMsMin = restLatencyMsMin;
        this.restLatencyMsMax = restLatencyMsMax;
        this.wsLatencyMsMin = wsLatencyMsMin;
        this.wsLatencyMsMax = wsLatencyMsMax;
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

    @Override
    public String toString() {
        return "PaperBrokerLatency{" + "restLatencyMsMin=" + restLatencyMsMin + ", restLatencyMsMax=" + restLatencyMsMax
                + ", wsLatencyMsMin=" + wsLatencyMsMin + ", wsLatencyMsMax=" + wsLatencyMsMax + '}';
    }
}