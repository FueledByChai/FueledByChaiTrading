package com.fueledbychai.okx.common.api.ws.listener;

import com.fueledbychai.okx.common.api.ws.model.OkxTickerUpdate;

public interface IOkxTickerListener {

    void onTicker(OkxTickerUpdate update);
}
