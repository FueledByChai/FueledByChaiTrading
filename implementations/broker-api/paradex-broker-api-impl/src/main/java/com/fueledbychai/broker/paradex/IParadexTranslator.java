package com.fueledbychai.broker.paradex;

import java.math.BigDecimal;

import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderStatus.Status;
import com.fueledbychai.paradex.common.api.ws.fills.ParadexFill;
import com.fueledbychai.paradex.common.api.ws.orderstatus.CancelReason;
import com.fueledbychai.paradex.common.api.ws.orderstatus.IParadexOrderStatusUpdate;
import com.fueledbychai.paradex.common.api.ws.orderstatus.ParadexOrderStatus;

public interface IParadexTranslator {

    OrderStatus translateOrderStatus(IParadexOrderStatusUpdate paradexStatus);

    Status translateStatusCode(ParadexOrderStatus paradexStatus, CancelReason cancelReason, BigDecimal originalSize,
            BigDecimal remainingSize);

    Fill translateFill(ParadexFill paradexFill);

}