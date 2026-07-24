package com.fueledbychai.marketdata.extended;

import java.time.ZonedDateTime;

import com.google.gson.JsonObject;

import com.fueledbychai.marketdata.IOrderBook;

public interface IExtendedOrderBook extends IOrderBook {

    void handleSnapshot(JsonObject data, ZonedDateTime timestamp);

    void applyDelta(JsonObject data, ZonedDateTime timestamp);

}
