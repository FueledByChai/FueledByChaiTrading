package com.fueledbychai.broker.grvt;

import java.math.BigDecimal;

import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;

/**
 * Translates between FueledByChai domain orders and GRVT order payloads / states.
 */
public interface IGrvtTranslator {

    /**
     * Builds a signable {@link GrvtOrder} from an {@link OrderTicket} for the given sub-account. The
     * order's nonce and expiration are populated; the EIP-712 signature is applied later by the REST
     * API at submission time.
     */
    GrvtOrder toGrvtOrder(OrderTicket order, String subAccountId);

    /** Maps a GRVT order-state status string to a domain {@link OrderStatus.Status}. */
    OrderStatus.Status toOrderStatus(String grvtStatus, BigDecimal tradedSize, BigDecimal remainingSize);
}
