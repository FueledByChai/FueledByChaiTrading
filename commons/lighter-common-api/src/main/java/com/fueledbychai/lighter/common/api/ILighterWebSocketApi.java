package com.fueledbychai.lighter.common.api;

import com.fueledbychai.lighter.common.api.ws.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.LighterWebSocketClient;

public interface ILighterWebSocketApi {

    LighterWebSocketClient subscribeMarketStats(int marketId, ILighterMarketStatsListener listener);

    LighterWebSocketClient subscribeAllMarketStats(ILighterMarketStatsListener listener);

    void disconnectAll();
}

