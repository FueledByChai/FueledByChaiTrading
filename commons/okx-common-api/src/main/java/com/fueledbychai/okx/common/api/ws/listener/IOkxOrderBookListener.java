package com.fueledbychai.okx.common.api.ws.listener;

import com.fueledbychai.okx.common.api.ws.model.OkxOrderBookUpdate;

public interface IOkxOrderBookListener {

    void onOrderBook(OkxOrderBookUpdate update);
}
