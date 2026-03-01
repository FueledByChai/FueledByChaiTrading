package com.fueledbychai.deribit.common.api.ws.listener;

import java.util.List;

import com.fueledbychai.deribit.common.api.ws.model.DeribitTrade;

public interface IDeribitTradeListener {

    void onTrades(String instrumentName, List<DeribitTrade> trades);
}
