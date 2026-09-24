package com.fueledbychai.qfex.common.api;

import com.fueledbychai.qfex.common.api.ws.QfexMarketDataStream;
import com.fueledbychai.qfex.common.api.ws.QfexTradeStream;

public class QfexWebSocketApi implements IQfexWebSocketApi {

    protected final QfexMarketDataStream marketData;
    protected final QfexTradeStream trade;

    public QfexWebSocketApi(QfexConfiguration config) {
        this.marketData = new QfexMarketDataStream(config.getMarketDataWebSocketUrl());
        this.trade = config.hasPrivateApiConfiguration()
                ? new QfexTradeStream(config.getTradeWebSocketUrl(),
                        new QfexHmacSigner(config.getPublicKey(), config.getSecretKey()), config.getAccountId(),
                        config.isCancelOnDisconnect(), config.getOrderTimeoutMillis())
                : null;
    }

    @Override
    public void connect() {
        marketData.start();
    }

    @Override
    public void connectOrderEntryWebSocket() {
        if (trade == null) {
            throw new IllegalStateException("QFEX order entry requires " + QfexConfiguration.QFEX_API_PUBLIC_KEY
                    + " and " + QfexConfiguration.QFEX_API_SECRET_KEY);
        }
        trade.start();
    }

    @Override
    public void disconnectAll() {
        marketData.stop();
        if (trade != null) {
            trade.stop();
        }
    }

    @Override
    public QfexMarketDataStream marketData() {
        return marketData;
    }

    @Override
    public QfexTradeStream trade() {
        return trade;
    }
}
