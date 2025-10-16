/**
 * MIT License

Copyright (c) 2015  FueledByChai Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software 
and associated documentation files (the "Software"), to deal in the Software without restriction, 
including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, 
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING 
BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, 
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE 
OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.fueledbychai.broker.ib;

import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.ib.IbUtils;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 *
 * @author FueledByChai Contributors
 */
public class OrderManagmentUtil {

    /**
     * Constructs an OrderEvent and also updates the TradeOrder object with the
     * correct status and fill quantity.
     * 
     * @param order
     * @param statusString
     * @param filled
     * @param remaining
     * @param avgFillPrice
     * @param permId
     * @param parentId
     * @param lastFillPrice
     * @param clientId
     * @param whyHeld
     * @return
     */
    public static OrderEvent createOrderEvent(OrderTicket order, String statusString, BigDecimal filled,
            BigDecimal remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId,
            String whyHeld, ZonedDateTime timestamp) {
        OrderStatus.Status status = IbUtils.getOrderStatus(statusString);

        OrderStatus orderStatus = new OrderStatus(status, order.getOrderId(), filled, remaining,
                new BigDecimal(avgFillPrice), order.getTicker(), timestamp);
        OrderEvent orderEvent = new OrderEvent(order, orderStatus);

        return orderEvent;
    }
}
