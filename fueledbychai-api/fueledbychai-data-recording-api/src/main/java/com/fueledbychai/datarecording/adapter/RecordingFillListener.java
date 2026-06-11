package com.fueledbychai.datarecording.adapter;

import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.FillEventListener;
import com.fueledbychai.datarecording.MarketDataRecorder;
import com.fueledbychai.datarecording.OwnEventCategory;
import com.fueledbychai.datarecording.RecordTimes;
import com.fueledbychai.datarecording.RecordedOwnEvent;

/**
 * Bridges broker {@link FillEventListener} executions into {@link RecordedOwnEvent} rows
 * (category {@link OwnEventCategory#FILL}) on a {@link MarketDataRecorder}, so our fills join
 * against recorded market data for markout analysis.
 */
public final class RecordingFillListener implements FillEventListener {

    private final MarketDataRecorder recorder;

    public RecordingFillListener(MarketDataRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void fillReceived(Fill fill) {
        long recv = RecordTimes.nowMicros();
        long evt = RecordTimes.micros(fill.getTime());
        double size = fill.getSize() == null ? 0.0 : fill.getSize().doubleValue();
        RecordedOwnEvent event = new RecordedOwnEvent(
                fill.getTicker().getExchange().getExchangeName(),
                fill.getTicker().getSymbol(),
                recv,
                evt,
                OwnEventCategory.FILL,
                null,
                fill.getClientOrderId(),
                fill.getOrderId(),
                fill.getFillId(),
                OwnEventMapping.toSide(fill.getSide()),
                fill.getPrice(),
                size,
                fill.getCommission(),
                fill.isTaker());
        recorder.recordOwnEvent(event);
    }
}
