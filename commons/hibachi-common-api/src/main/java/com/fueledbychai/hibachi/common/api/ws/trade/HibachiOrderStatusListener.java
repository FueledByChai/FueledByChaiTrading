package com.fueledbychai.hibachi.common.api.ws.trade;

@FunctionalInterface
public interface HibachiOrderStatusListener {
    void onOrderStatus(HibachiOrderStatusUpdate update);
}
