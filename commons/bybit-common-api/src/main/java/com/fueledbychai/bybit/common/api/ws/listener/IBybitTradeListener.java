package com.fueledbychai.bybit.common.api.ws.listener;

import java.util.List;

import com.fueledbychai.bybit.common.api.ws.model.BybitTrade;

public interface IBybitTradeListener {

    void onTrades(String instrumentId, List<BybitTrade> trades);
}
