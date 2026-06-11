package com.fueledbychai.datarecording.adapter;

import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderEventListener;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.datarecording.MarketDataRecorder;
import com.fueledbychai.datarecording.RecordTimes;
import com.fueledbychai.datarecording.RecordedOwnEvent;

/**
 * Bridges broker {@link OrderEventListener} lifecycle transitions (submit/ack/cancel/reject/
 * replace) into {@link RecordedOwnEvent} rows on a {@link MarketDataRecorder}. Captures the
 * order's full lifecycle around fills; pair with {@link RecordingFillListener} for executions.
 */
public final class RecordingOrderEventListener implements OrderEventListener {

    private final MarketDataRecorder recorder;

    public RecordingOrderEventListener(MarketDataRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void orderEvent(OrderEvent event) {
        OrderTicket order = event.getOrder();
        String rawStatus = String.valueOf(event.getOrderStatus());
        long recv = RecordTimes.nowMicros();
        double size = order.getSize() == null ? 0.0 : order.getSize().doubleValue();
        RecordedOwnEvent rec = new RecordedOwnEvent(
                order.getTicker().getExchange().getExchangeName(),
                order.getTicker().getSymbol(),
                recv,
                0L,
                OwnEventMapping.categorize(rawStatus),
                rawStatus,
                order.getClientOrderId(),
                order.getOrderId(),
                null,
                OwnEventMapping.toSide(order.getTradeDirection()),
                order.getLimitPrice(),
                size,
                order.getCommission(),
                null);
        recorder.recordOwnEvent(rec);
    }
}
