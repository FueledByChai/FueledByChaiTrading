package com.fueledbychai.hyperliquid.ws;

import com.fueledbychai.hyperliquid.ws.json.OrderAction;
import com.fueledbychai.hyperliquid.ws.json.ws.SubmitPostResponse;

public interface IHyperliquidWebsocketApi {

    SubmitPostResponse submitOrders(OrderAction orderAction);

    void connect();

}