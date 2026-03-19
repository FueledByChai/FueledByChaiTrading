package com.fueledbychai.drift.common.api.ws;

import com.fueledbychai.drift.common.api.model.DriftOrderBookSnapshot;
import com.fueledbychai.websocket.IWebSocketEventListener;

public interface DriftOrderBookListener extends IWebSocketEventListener<DriftOrderBookSnapshot> {
}
