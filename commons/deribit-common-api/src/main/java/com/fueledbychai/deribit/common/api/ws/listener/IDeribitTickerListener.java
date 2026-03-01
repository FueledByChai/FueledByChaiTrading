package com.fueledbychai.deribit.common.api.ws.listener;

import com.fueledbychai.deribit.common.api.ws.model.DeribitTickerUpdate;

public interface IDeribitTickerListener {

    void onTicker(DeribitTickerUpdate update);
}
