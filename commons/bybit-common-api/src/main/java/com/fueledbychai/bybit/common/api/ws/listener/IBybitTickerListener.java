package com.fueledbychai.bybit.common.api.ws.listener;

import com.fueledbychai.bybit.common.api.ws.model.BybitTickerUpdate;

public interface IBybitTickerListener {

    void onTicker(BybitTickerUpdate update);
}
