package com.fueledbychai.lighter.common.api.ws.listener;

import com.fueledbychai.lighter.common.api.ws.model.LighterOrderBookUpdate;
import com.fueledbychai.websocket.IWebSocketEventListener;

public interface ILighterOrderBookListener extends IWebSocketEventListener<LighterOrderBookUpdate> {
}
