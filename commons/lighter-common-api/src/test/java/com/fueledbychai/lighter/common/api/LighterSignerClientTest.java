package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterOrderType;
import com.fueledbychai.lighter.common.api.order.LighterTimeInForce;
import com.fueledbychai.lighter.common.api.signer.ILighterTransactionSigner;
import com.fueledbychai.lighter.common.api.signer.LighterSignedTransaction;
import com.fueledbychai.lighter.common.api.ws.model.LighterSendTxResponse;

public class LighterSignerClientTest {

    @Test
    void createOrderSignsThenSendsTx() {
        ILighterWebSocketApi wsApi = org.mockito.Mockito.mock(ILighterWebSocketApi.class);
        ILighterTransactionSigner signer = org.mockito.Mockito.mock(ILighterTransactionSigner.class);
        LighterSignerClient client = new LighterSignerClient(wsApi, signer);

        LighterCreateOrderRequest request = LighterCreateOrderRequest.marketOrder(2, 100L, 1000L, 90000, false);
        JSONObject txInfo = new JSONObject();
        txInfo.put("market_index", 2);
        txInfo.put("price", 90000);
        LighterSignedTransaction signed = new LighterSignedTransaction(LighterSignerClient.TX_TYPE_CREATE_ORDER, txInfo,
                "0xhash", "0xmsg");
        LighterSendTxResponse expected = new LighterSendTxResponse("1", 200, "ok", "{\"code\":200}");

        when(signer.signCreateOrder(eq(request))).thenReturn(signed);
        when(wsApi.sendSignedTransaction(eq(LighterSignerClient.TX_TYPE_CREATE_ORDER), any(JSONObject.class)))
                .thenReturn(expected);

        LighterSendTxResponse actual = client.createOrder(request);

        assertNotNull(actual);
        assertEquals(200, actual.getCode());
        assertEquals("ok", actual.getMessage());

        ArgumentCaptor<JSONObject> txInfoCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(wsApi).sendSignedTransaction(eq(LighterSignerClient.TX_TYPE_CREATE_ORDER), txInfoCaptor.capture());
        assertEquals(2, txInfoCaptor.getValue().getInt("market_index"));
    }

    @Test
    void createMarketOrderAppliesMarketDefaults() {
        ILighterWebSocketApi wsApi = org.mockito.Mockito.mock(ILighterWebSocketApi.class);
        ILighterTransactionSigner signer = org.mockito.Mockito.mock(ILighterTransactionSigner.class);
        LighterSignerClient client = new LighterSignerClient(wsApi, signer);

        AtomicReference<LighterCreateOrderRequest> capturedRequest = new AtomicReference<>();
        when(signer.signCreateOrder(any(LighterCreateOrderRequest.class))).thenAnswer(invocation -> {
            LighterCreateOrderRequest req = invocation.getArgument(0);
            capturedRequest.set(req);
            JSONObject txInfo = new JSONObject();
            txInfo.put("market_index", req.getMarketIndex());
            return new LighterSignedTransaction(LighterSignerClient.TX_TYPE_CREATE_ORDER, txInfo, "0xhash", "0xmsg");
        });
        when(wsApi.sendSignedTransaction(eq(LighterSignerClient.TX_TYPE_CREATE_ORDER), any(JSONObject.class)))
                .thenReturn(new LighterSendTxResponse("1", 200, "ok", "{\"code\":200}"));

        LighterSendTxResponse response = client.createMarketOrder(7, 101L, 500L, 12345, true);

        assertNotNull(response);
        assertNotNull(capturedRequest.get());
        assertEquals(7, capturedRequest.get().getMarketIndex());
        assertEquals(101L, capturedRequest.get().getClientOrderIndex());
        assertEquals(500L, capturedRequest.get().getBaseAmount());
        assertEquals(12345, capturedRequest.get().getPrice());
        assertEquals(true, capturedRequest.get().isAsk());
        assertEquals(LighterOrderType.MARKET, capturedRequest.get().getOrderType());
        assertEquals(LighterTimeInForce.IOC, capturedRequest.get().getTimeInForce());
    }

    @Test
    void cancelOrderSignsThenSendsTx() {
        ILighterWebSocketApi wsApi = org.mockito.Mockito.mock(ILighterWebSocketApi.class);
        ILighterTransactionSigner signer = org.mockito.Mockito.mock(ILighterTransactionSigner.class);
        LighterSignerClient client = new LighterSignerClient(wsApi, signer);

        LighterCancelOrderRequest request = new LighterCancelOrderRequest();
        request.setMarketIndex(4);
        request.setOrderIndex(321L);

        JSONObject txInfo = new JSONObject();
        txInfo.put("market_index", 4);
        txInfo.put("order_index", 321L);
        LighterSignedTransaction signed = new LighterSignedTransaction(LighterSignerClient.TX_TYPE_CANCEL_ORDER, txInfo,
                "0xhash", "0xmsg");
        LighterSendTxResponse expected = new LighterSendTxResponse("1", 200, "ok", "{\"code\":200}");

        when(signer.signCancelOrder(eq(request))).thenReturn(signed);
        when(wsApi.sendSignedTransaction(eq(LighterSignerClient.TX_TYPE_CANCEL_ORDER), any(JSONObject.class)))
                .thenReturn(expected);

        LighterSendTxResponse actual = client.cancelOrder(request);

        assertNotNull(actual);
        assertEquals(200, actual.getCode());
        assertEquals("ok", actual.getMessage());

        ArgumentCaptor<JSONObject> txInfoCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(wsApi).sendSignedTransaction(eq(LighterSignerClient.TX_TYPE_CANCEL_ORDER), txInfoCaptor.capture());
        assertEquals(321L, txInfoCaptor.getValue().getLong("order_index"));
    }

    @Test
    void modifyOrderSignsThenSendsTx() {
        ILighterWebSocketApi wsApi = org.mockito.Mockito.mock(ILighterWebSocketApi.class);
        ILighterTransactionSigner signer = org.mockito.Mockito.mock(ILighterTransactionSigner.class);
        LighterSignerClient client = new LighterSignerClient(wsApi, signer);

        LighterModifyOrderRequest request = new LighterModifyOrderRequest();
        request.setMarketIndex(5);
        request.setOrderIndex(1234L);
        request.setBaseAmount(2500L);
        request.setPrice(42000);
        request.setAsk(false);
        request.setOrderType(LighterOrderType.LIMIT);
        request.setTimeInForce(LighterTimeInForce.GTT);

        JSONObject txInfo = new JSONObject();
        txInfo.put("market_index", 5);
        txInfo.put("order_index", 1234L);
        txInfo.put("base_amount", 2500L);
        txInfo.put("price", 42000);
        LighterSignedTransaction signed = new LighterSignedTransaction(LighterSignerClient.TX_TYPE_MODIFY_ORDER, txInfo,
                "0xhash", "0xmsg");
        LighterSendTxResponse expected = new LighterSendTxResponse("1", 200, "ok", "{\"code\":200}");

        when(signer.signModifyOrder(eq(request))).thenReturn(signed);
        when(wsApi.sendSignedTransaction(eq(LighterSignerClient.TX_TYPE_MODIFY_ORDER), any(JSONObject.class)))
                .thenReturn(expected);

        LighterSendTxResponse actual = client.modifyOrder(request);

        assertNotNull(actual);
        assertEquals(200, actual.getCode());
        assertEquals("ok", actual.getMessage());

        ArgumentCaptor<JSONObject> txInfoCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(wsApi).sendSignedTransaction(eq(LighterSignerClient.TX_TYPE_MODIFY_ORDER), txInfoCaptor.capture());
        assertEquals(1234L, txInfoCaptor.getValue().getLong("order_index"));
        assertEquals(42000, txInfoCaptor.getValue().getInt("price"));
    }
}
