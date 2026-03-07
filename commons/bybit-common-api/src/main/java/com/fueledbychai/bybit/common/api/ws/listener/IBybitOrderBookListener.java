package com.fueledbychai.bybit.common.api.ws.listener;

import com.fueledbychai.bybit.common.api.ws.model.BybitOrderBookUpdate;

public interface IBybitOrderBookListener {

    void onOrderBook(BybitOrderBookUpdate update);
}
