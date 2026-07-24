package com.fueledbychai.drift.common.api;

import com.fueledbychai.drift.common.api.model.DriftGatewayWebSocketEvent;
import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.drift.common.api.model.DriftOrderBookSnapshot;
import com.fueledbychai.drift.common.api.ws.DriftGatewayEventListener;
import com.fueledbychai.drift.common.api.ws.DriftOrderBookListener;
import com.fueledbychai.drift.common.api.ws.client.DriftWebSocketClient;
import com.fueledbychai.websocket.ExchangeWebSocketLifecycle;

public interface IDriftWebSocketApi extends ExchangeWebSocketLifecycle {

    DriftWebSocketClient subscribeOrderBook(String marketName, DriftMarketType marketType,
            DriftOrderBookListener listener);

    DriftWebSocketClient subscribeGatewayEvents(int subAccountId, DriftGatewayEventListener listener);

    @Override
    void disconnectAll();
}
