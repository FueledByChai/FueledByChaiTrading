package com.fueledbychai.okx.common.api.ws.listener;

import java.util.List;

import com.fueledbychai.okx.common.api.ws.model.OkxTrade;

public interface IOkxTradeListener {

    void onTrades(String instrumentId, List<OkxTrade> trades);
}
