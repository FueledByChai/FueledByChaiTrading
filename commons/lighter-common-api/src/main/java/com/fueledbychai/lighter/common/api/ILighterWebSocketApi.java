package com.fueledbychai.lighter.common.api;

import com.fueledbychai.lighter.common.api.ws.listener.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterOrderBookListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterAccountAllTradesListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterAccountOrdersListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterAccountStatsListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterTradeListener;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.signer.LighterSignedTransaction;
import com.fueledbychai.lighter.common.api.ws.model.LighterSendTxResponse;
import com.fueledbychai.lighter.common.api.ws.client.LighterWebSocketClient;
import org.json.JSONObject;

public interface ILighterWebSocketApi {

    LighterWebSocketClient subscribeMarketStats(int marketId, ILighterMarketStatsListener listener);

    LighterWebSocketClient subscribeAllMarketStats(ILighterMarketStatsListener listener);

    LighterWebSocketClient subscribeOrderBook(int marketId, ILighterOrderBookListener listener);

    LighterWebSocketClient subscribeTrades(int marketId, ILighterTradeListener listener);

    LighterWebSocketClient subscribeAccountAllTrades(long accountIndex, String authToken,
            ILighterAccountAllTradesListener listener);

    LighterWebSocketClient subscribeAccountOrders(int marketIndex, long accountIndex, String authToken,
            ILighterAccountOrdersListener listener);

    LighterWebSocketClient subscribeAccountStats(long accountIndex, ILighterAccountStatsListener listener);

    LighterSignedTransaction signOrder(LighterCreateOrderRequest orderRequest);

    LighterSendTxResponse submitOrder(LighterCreateOrderRequest orderRequest);

    LighterSignedTransaction signCancelOrder(LighterCancelOrderRequest cancelRequest);

    LighterSendTxResponse cancelOrder(LighterCancelOrderRequest cancelRequest);

    LighterSignedTransaction signModifyOrder(LighterModifyOrderRequest modifyRequest);

    LighterSendTxResponse modifyOrder(LighterModifyOrderRequest modifyRequest);

    LighterSendTxResponse sendSignedTransaction(int txType, JSONObject txInfo);

    void disconnectAll();
}
