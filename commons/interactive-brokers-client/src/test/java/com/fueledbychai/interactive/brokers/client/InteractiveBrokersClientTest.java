/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.interactive.brokers.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.fueledbychai.broker.IBroker;
import com.fueledbychai.broker.order.OrderEventListener;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.BarData;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.historicaldata.IHistoricalDataProvider;
import com.fueledbychai.ib.IBConnectionUtil;
import com.fueledbychai.ib.IBSocket;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.realtime.bar.IRealtimeBarEngine;
import com.fueledbychai.realtime.bar.RealtimeBarListener;
import com.fueledbychai.realtime.bar.RealtimeBarRequest;

/**
 *
 *  
 */
@Ignore("Ignore until IB Issues are fixed")
public class InteractiveBrokersClientTest {

    protected InteractiveBrokersClient client;
    protected IBSocket mockIbSocket;
    protected QuoteEngine mockQuoteEngine;
    protected IBroker mockBroker;
    protected IHistoricalDataProvider mockHistoricalDataProvider;
    protected static IBConnectionUtil mockIbConnectionUtil;
    protected IRealtimeBarEngine mockRealtimeBarEngine;

    protected String host = "myHost";
    protected int port = 123;
    protected int clientId = 99;

    public InteractiveBrokersClientTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        mockIbSocket = mock(IBSocket.class);
        mockQuoteEngine = mock(QuoteEngine.class);
        mockBroker = mock(IBroker.class);
        mockHistoricalDataProvider = mock(IHistoricalDataProvider.class);
        mockIbConnectionUtil = mock(IBConnectionUtil.class);
        mockRealtimeBarEngine = mock(IRealtimeBarEngine.class);
        client = new InteractiveBrokersClient(host, port, clientId);
        client.broker = mockBroker;
        client.historicalDataProvider = mockHistoricalDataProvider;
        client.ibSocket = mockIbSocket;
        client.quoteEngine = mockQuoteEngine;
        client.realtimeBarProvider = mockRealtimeBarEngine;
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testConnect_WithMock() {
        InteractiveBrokersClientInterface mockClient = mock(InteractiveBrokersClientInterface.class);
        String host = "myHost";
        int port = 9999;
        int clientId = 123;
        InteractiveBrokersClient.setMockInteractiveBrokersClient(mockClient, host, port, clientId);

        assertEquals(mockClient, InteractiveBrokersClient.getInstance(host, port, clientId));
    }

    @Test
    public void testConnect_NoMock() {
        InteractiveBrokersClientInterface mockClient = mock(InteractiveBrokersClientInterface.class);
        String host = "myHost";
        int port = 9999;
        int clientId = 123;
        InteractiveBrokersClient.setMockInteractiveBrokersClient(mockClient, host, port, clientId);

        InteractiveBrokersClientInterface actual = InteractiveBrokersClient.getInstance(host, 9998, clientId);

        assertNotEquals(mockClient, actual);
        assertEquals(actual, InteractiveBrokersClient.getInstance(host, 9998, clientId));

    }

    @Test
    public void testConnect() {
        client.connect();
        verify(mockIbSocket).connect();
        verify(mockBroker).connect();
        verify(mockHistoricalDataProvider).connect();
        verify(mockQuoteEngine).startEngine();
    }

    @Test
    public void disconnect() {
        client.disconnect();
        verify(mockBroker).disconnect();
        verify(mockQuoteEngine).stopEngine();
    }

    @Test
    public void testSubscribeUnsubscribeLevel1() {
        Ticker ticker = new Ticker("ABC").setInstrumentType(InstrumentType.STOCK);
        Level1QuoteListener listener = (ILevel1Quote quote) -> {
        };
        client.subscribeLevel1(ticker, listener);
        client.unsubscribeLevel1(ticker, listener);
        verify(mockQuoteEngine).subscribeLevel1(ticker, listener);
        verify(mockQuoteEngine).unsubscribeLevel1(ticker, listener);
    }

    @Test
    public void testSubscribeMarketDepth() {
        Ticker ticker = new Ticker("ABC").setInstrumentType(InstrumentType.STOCK);
        Level2QuoteListener listener = (ILevel2Quote quote) -> {
        };
        client.subscribeMarketDepth(ticker, listener);
        client.unsubscribeMarketDepth(ticker, listener);
        verify(mockQuoteEngine).subscribeMarketDepth(ticker, listener);
        verify(mockQuoteEngine).unsubscribeMarketDepth(ticker, listener);

    }

    @Test
    public void testPlaceOrder() {
        OrderTicket order = new OrderTicket("123", new Ticker("ABC").setInstrumentType(InstrumentType.STOCK),
                BigDecimal.valueOf(10), TradeDirection.SELL);
        client.placeOrder(order);
        verify(mockBroker).placeOrder(order);
    }

    @Test
    public void testAddOrderStatusListener() {
        OrderEventListener listener = mock(OrderEventListener.class);
        client.addOrderStatusListener(listener);
        verify(mockBroker).addOrderEventListener(listener);
    }

    @Test
    public void testGetNextOrderId() {
        when(mockBroker.getNextOrderId()).thenReturn("123");
        assertEquals("123", client.getNextOrderId());
    }

    @Test
    public void testGetOpenOrders() {
        List<OrderTicket> list = new ArrayList<>();
        list.add(new OrderTicket("123", new Ticker("ABC").setInstrumentType(InstrumentType.STOCK),
                BigDecimal.valueOf(123), TradeDirection.SELL));
        when(mockBroker.getOpenOrders()).thenReturn(list);
        assertEquals(list, mockBroker.getOpenOrders());
    }

    @Test
    public void testRequestGetHistoricalData() {
        List<BarData> barData = new ArrayList<>();
        barData.add(new BarData());
        Ticker ticker = new Ticker("ABC").setInstrumentType(InstrumentType.STOCK);
        int duration = 1;
        BarData.LengthUnit unit = BarData.LengthUnit.DAY;
        int barSize = 1;
        BarData.LengthUnit sizeUnit = BarData.LengthUnit.HOUR;
        IHistoricalDataProvider.ShowProperty showProperty = IHistoricalDataProvider.ShowProperty.TRADES;

        when(mockHistoricalDataProvider.requestHistoricalData(ticker, duration, unit, barSize, sizeUnit, showProperty,
                true)).thenReturn(barData);
        assertEquals(barData, client.requestHistoricalData(ticker, duration, unit, barSize, sizeUnit, showProperty));
    }

    @Test
    public void testRequestGetHistoricalData_OverloadedMethod() throws IOException {
        List<BarData> barData = new ArrayList<>();
        barData.add(new BarData());
        Ticker ticker = new Ticker("ABC").setInstrumentType(InstrumentType.STOCK);
        int duration = 1;
        BarData.LengthUnit unit = BarData.LengthUnit.DAY;
        int barSize = 1;
        BarData.LengthUnit sizeUnit = BarData.LengthUnit.HOUR;
        IHistoricalDataProvider.ShowProperty showProperty = IHistoricalDataProvider.ShowProperty.TRADES;
        Date startDate = new Date();

        when(mockHistoricalDataProvider.requestHistoricalData(ticker, startDate, duration, unit, barSize, sizeUnit,
                showProperty, true)).thenReturn(barData);
        assertEquals(barData,
                client.requestHistoricalData(ticker, startDate, duration, unit, barSize, sizeUnit, showProperty, true));
    }

    @Test
    public void testSubscribeRealtimeBar() {
        RealtimeBarRequest request = new RealtimeBarRequest(1,
                new Ticker("ABC").setInstrumentType(InstrumentType.STOCK), clientId, BarData.LengthUnit.MINUTE);
        RealtimeBarListener listener = (int requestId, Ticker ticker, BarData bar) -> {
        };

        client.subscribeRealtimeBar(request, listener);
        verify(mockRealtimeBarEngine).subscribeRealtimeBars(request, listener);
    }

}
