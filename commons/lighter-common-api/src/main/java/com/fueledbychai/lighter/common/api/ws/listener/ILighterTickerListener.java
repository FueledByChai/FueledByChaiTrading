package com.fueledbychai.lighter.common.api.ws.listener;

import com.fueledbychai.lighter.common.api.ws.model.LighterTickerUpdate;
import com.fueledbychai.websocket.IWebSocketEventListener;

public interface ILighterTickerListener extends IWebSocketEventListener<LighterTickerUpdate> {
}
