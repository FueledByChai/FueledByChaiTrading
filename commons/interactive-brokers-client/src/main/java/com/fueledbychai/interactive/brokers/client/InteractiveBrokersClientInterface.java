/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.interactive.brokers.client;

import com.fueledbychai.broker.BrokerErrorListener;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderEventListener;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.BarData;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.historicaldata.IHistoricalDataProvider;
import com.fueledbychai.historicaldata.IHistoricalDataProvider.ShowProperty;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.realtime.bar.RealtimeBarListener;
import com.fueledbychai.realtime.bar.RealtimeBarRequest;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 *
 *  
 */
public interface InteractiveBrokersClientInterface {

        void addOrderStatusListener(OrderEventListener listener);

        void connect();

        void disconnect();

        int getClientId();

        String getHost();

        String getNextOrderId();

        int getPort();

        void placeOrder(OrderTicket order);

        List<BarData> requestHistoricalData(Ticker ticker, int duration, BarData.LengthUnit lengthUnit, int barSize,
                        BarData.LengthUnit barSizeUnit, IHistoricalDataProvider.ShowProperty showProperty);

        List<BarData> requestHistoricalData(Ticker ticker, Date endDateTime, int duration,
                        BarData.LengthUnit durationLengthUnit, int barSize, BarData.LengthUnit barSizeUnit,
                        ShowProperty whatToShow, boolean useRTH) throws IOException;

        void subscribeLevel1(Ticker ticker, Level1QuoteListener listener);

        void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener);

        void unsubscribeLevel1(Ticker ticker, Level1QuoteListener listener);

        void unsubscribeMarketDepth(Ticker ticker, Level2QuoteListener listener);

        void addBrokerErrorListener(BrokerErrorListener listener);

        void removeBrokerErrorListener(BrokerErrorListener listener);

        List<OrderTicket> getOpenOrders();

        void useDelayedData(boolean useDelayedData);

        List<Position> getOpenPositions();

        public void subscribeRealtimeBar(RealtimeBarRequest request, RealtimeBarListener listener);

}
