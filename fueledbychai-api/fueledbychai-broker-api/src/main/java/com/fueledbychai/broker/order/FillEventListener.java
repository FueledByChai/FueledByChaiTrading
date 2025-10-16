package com.fueledbychai.broker.order;

public interface FillEventListener {

    void fillReceived(Fill fill);
}
