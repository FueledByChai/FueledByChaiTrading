package com.fueledbychai.lighter.common.api;

import com.fueledbychai.lighter.common.api.ws.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.ILighterOrderBookListener;
import com.fueledbychai.lighter.common.api.ws.ILighterTradeListener;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.signer.LighterSignedTransaction;
import com.fueledbychai.lighter.common.api.ws.LighterSendTxResponse;
import com.fueledbychai.lighter.common.api.ws.LighterWebSocketClient;
import org.json.JSONObject;

public interface ILighterWebSocketApi {

    LighterWebSocketClient subscribeMarketStats(int marketId, ILighterMarketStatsListener listener);

    LighterWebSocketClient subscribeAllMarketStats(ILighterMarketStatsListener listener);

    LighterWebSocketClient subscribeOrderBook(int marketId, ILighterOrderBookListener listener);

    LighterWebSocketClient subscribeTrades(int marketId, ILighterTradeListener listener);

    LighterSignedTransaction signOrder(LighterCreateOrderRequest orderRequest);

    LighterSendTxResponse submitOrder(LighterCreateOrderRequest orderRequest);

    LighterSignedTransaction signCancelOrder(LighterCancelOrderRequest cancelRequest);

    LighterSendTxResponse cancelOrder(LighterCancelOrderRequest cancelRequest);

    LighterSignedTransaction signModifyOrder(LighterModifyOrderRequest modifyRequest);

    LighterSendTxResponse modifyOrder(LighterModifyOrderRequest modifyRequest);

    LighterSendTxResponse sendSignedTransaction(int txType, JSONObject txInfo);

    void disconnectAll();
}
