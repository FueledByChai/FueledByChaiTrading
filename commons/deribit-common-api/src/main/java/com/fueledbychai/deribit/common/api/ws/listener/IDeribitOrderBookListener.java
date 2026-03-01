package com.fueledbychai.deribit.common.api.ws.listener;

import com.fueledbychai.deribit.common.api.ws.model.DeribitBookUpdate;

public interface IDeribitOrderBookListener {

    void onOrderBook(DeribitBookUpdate update);
}
