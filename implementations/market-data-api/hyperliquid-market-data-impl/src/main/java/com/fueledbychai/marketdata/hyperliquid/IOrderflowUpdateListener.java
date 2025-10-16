package com.fueledbychai.marketdata.hyperliquid;

import com.fueledbychai.marketdata.OrderFlow;

public interface IOrderflowUpdateListener {

    void onOrderflowUpdate(OrderFlow orderFlow);
}
