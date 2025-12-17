package com.fueledbychai.paradex.common.api.ws.orderstatus;

import java.math.BigDecimal;

import org.json.JSONObject;

import com.fueledbychai.time.WsLatency;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class OrderStatusWebSocketProcessor extends AbstractWebSocketProcessor<IParadexOrderStatusUpdate> {

    protected static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(OrderStatusWebSocketProcessor.class);

    public OrderStatusWebSocketProcessor(IWebSocketClosedListener closedListener) {
        super(closedListener);
    }

    @Override
    protected IParadexOrderStatusUpdate parseMessage(String message) {
        long recvMs = System.currentTimeMillis();
        logger.info("Paradex OrderStatus WSProcessor: " + message);
        try {
            JSONObject jsonObject = new JSONObject(message);
            if (!jsonObject.has("method")) {
                return null;
            }
            String method = jsonObject.getString("method");

            if ("subscription".equals(method)) {
                JSONObject params = jsonObject.getJSONObject("params");
                JSONObject data = params.getJSONObject("data");
                String orderId = data.getString("id");
                String remainingSizeStr = data.getString("remaining_size");
                String status = data.getString("status");
                String originalSizeStr = data.getString("size");
                String cancelReason = data.getString("cancel_reason");
                String orderType = data.getString("type");
                String averageFillPriceStr = data.getString("avg_fill_price");
                long timestamp = data.getLong("published_at");
                String side = data.getString("side");
                String tickerString = data.getString("market");
                String clientOrderId = data.optString("client_id", "");
                if (averageFillPriceStr.equals("")) {
                    averageFillPriceStr = "0";
                }

                try {
                    WsLatency.onMessage("PD-OrderStatus", clientOrderId, recvMs, timestamp);
                } catch (Exception e) {
                    logger.error("Error processing latency for order status: " + message, e);
                }

                ParadoxOrderStatusUpdate orderStatus = new ParadoxOrderStatusUpdate(tickerString, orderId,
                        clientOrderId, new BigDecimal(remainingSizeStr), new BigDecimal(originalSizeStr), status,
                        cancelReason, new BigDecimal(averageFillPriceStr), orderType, side, timestamp);
                return orderStatus;
            } else {
                return null;
            }
        } catch (

        Exception e) {
            logger.error("Error parsing message: " + message, e);
            return null;
        }
    }

}
