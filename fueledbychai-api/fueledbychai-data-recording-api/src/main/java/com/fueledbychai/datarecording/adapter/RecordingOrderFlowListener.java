package com.fueledbychai.datarecording.adapter;

import com.fueledbychai.datarecording.MarketDataRecorder;
import com.fueledbychai.datarecording.RecordTimes;
import com.fueledbychai.datarecording.RecordedTrade;
import com.fueledbychai.datarecording.Side;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;

/**
 * Bridges the library's {@link OrderFlowListener} (public trade tape) into a
 * {@link RecordedTrade} on a {@link MarketDataRecorder}.
 */
public final class RecordingOrderFlowListener implements OrderFlowListener {

    private final MarketDataRecorder recorder;

    public RecordingOrderFlowListener(MarketDataRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void orderflowReceived(OrderFlow orderflow) {
        long recv = RecordTimes.nowMicros();
        long evt = RecordTimes.micros(orderflow.getTimestamp());
        double size = orderflow.getSize() == null ? 0.0 : orderflow.getSize().doubleValue();
        RecordedTrade trade = new RecordedTrade(
                orderflow.getTicker().getExchange().getExchangeName(),
                orderflow.getTicker().getSymbol(),
                recv,
                evt,
                null,
                orderflow.getPrice(),
                size,
                toSide(orderflow.getSide()));
        recorder.recordTrade(trade);
    }

    private static Side toSide(OrderFlow.Side side) {
        if (side == null) {
            return Side.UNKNOWN;
        }
        return side == OrderFlow.Side.BUY ? Side.BUY : Side.SELL;
    }
}
