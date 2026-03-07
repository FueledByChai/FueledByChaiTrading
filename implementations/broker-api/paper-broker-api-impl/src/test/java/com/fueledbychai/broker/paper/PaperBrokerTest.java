package com.fueledbychai.broker.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Properties;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.QuoteEngine;

public class PaperBrokerTest {

    private static final PaperBrokerLatency ZERO_LATENCY = new PaperBrokerLatency(0, 0, 0, 0);
    private final QuoteEngine quoteEngine = new TestQuoteEngine();

    @Test
    public void testGetOpenOrdersReturnsAllBidsAndAsks() {
        PaperBroker broker = new PaperBroker(quoteEngine, new Ticker("BTCUSDT"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        OrderTicket bidOrder = new OrderTicket("bid-1", new Ticker("BTCUSDT"), new BigDecimal("1.0"),
                TradeDirection.BUY);
        OrderTicket askOrder = new OrderTicket("ask-1", new Ticker("BTCUSDT"), new BigDecimal("2.0"),
                TradeDirection.SELL);

        broker.openBids.put(bidOrder.getOrderId(), bidOrder);
        broker.openAsks.put(askOrder.getOrderId(), askOrder);

        List<OrderTicket> openOrders = broker.getOpenOrders();

        assertEquals(2, openOrders.size());
        assertTrue(openOrders.stream().anyMatch(order -> order == bidOrder));
        assertTrue(openOrders.stream().anyMatch(order -> order == askOrder));
    }

    @Test
    public void testGetOpenOrdersForTickerFiltersByTicker() {
        PaperBroker broker = new PaperBroker(quoteEngine, new Ticker("BTCUSDT"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        Ticker btcTicker = new Ticker("BTCUSDT");
        Ticker ethTicker = new Ticker("ETHUSDT");

        OrderTicket btcBid = new OrderTicket("bid-btc", btcTicker, new BigDecimal("1.0"), TradeDirection.BUY);
        OrderTicket btcAsk = new OrderTicket("ask-btc", btcTicker, new BigDecimal("2.0"), TradeDirection.SELL);
        OrderTicket ethBid = new OrderTicket("bid-eth", ethTicker, new BigDecimal("3.0"), TradeDirection.BUY);

        broker.openBids.put(btcBid.getOrderId(), btcBid);
        broker.openAsks.put(btcAsk.getOrderId(), btcAsk);
        broker.openBids.put(ethBid.getOrderId(), ethBid);

        List<OrderTicket> openOrders = broker.getOpenOrders(btcTicker);

        assertEquals(2, openOrders.size());
        assertTrue(openOrders.stream().anyMatch(order -> order == btcBid));
        assertTrue(openOrders.stream().anyMatch(order -> order == btcAsk));
        assertTrue(openOrders.stream().noneMatch(order -> order == ethBid));
    }

    @Test
    public void testCancelOrderByClientOrderIdNotFound() {
        TestPaperBroker broker = new TestPaperBroker(quoteEngine, new Ticker("BTCUSDT"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);
        broker.interceptCancelRequests = true;

        BrokerRequestResult result = broker.cancelOrderByClientOrderId("missing-client-id");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("missing-client-id"));
        assertFalse(broker.cancelCalled);
    }

    @Test
    public void testCancelOrderByClientOrderIdFound() {
        TestPaperBroker broker = new TestPaperBroker(quoteEngine, new Ticker("BTCUSDT"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);
        broker.interceptCancelRequests = true;

        OrderTicket order = new OrderTicket("order-1", new Ticker("BTCUSDT"), new BigDecimal("1.0"),
                TradeDirection.BUY);
        order.setClientOrderId("client-1");
        broker.openOrders.put(order.getOrderId(), order);

        BrokerRequestResult result = broker.cancelOrderByClientOrderId("client-1");

        assertTrue(result.isSuccess());
        assertTrue(broker.cancelCalled);
        assertEquals(order.getOrderId(), broker.cancelledOrderId);
        assertTrue(broker.cancelShouldDelay);
    }

    @Test
    public void testGenerateBalanceFilenameSanitizesSymbol() {
        PaperBroker broker = new PaperBroker(quoteEngine, new Ticker("AZTEC/USDC"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        String filename = broker.generateBalanceFilename("AZTEC/USDC", "LIGHTER");

        assertEquals("AZTEC_USDC-LIGHTER-paperbroker-startingbalance.txt", filename);
        assertFalse(filename.contains("/"));
    }

    @Test
    public void testGenerateCsvFilenameSanitizesSymbol() {
        PaperBroker broker = new PaperBroker(quoteEngine, new Ticker("AZTEC/USDC"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        String filename = broker.generateCsvFilename("AZTEC/USDC", "LIGHTER");

        assertTrue(filename.matches("\\d{8}-\\d{4}-AZTEC_USDC-LIGHTER-Trades\\.csv"));
        assertFalse(filename.contains("/"));
    }

    @Test
    public void testPerpetualCommissionStillUsesFlatBpsSchedule() {
        Ticker ticker = new Ticker("BTCUSDT").setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        PaperBroker broker = new PaperBroker(quoteEngine, ticker, PaperBrokerCommission.PARADEX_COMMISSION,
                ZERO_LATENCY, 1000.0);

        double fee = broker.calcFee(100.0, 1.0, false);

        assertEquals(-0.02, fee, 0.000000001);
    }

    @Test
    public void testOptionCommissionCapsAtPremiumPercentage() {
        Ticker optionTicker = new Ticker("BTC-28MAR26-100000-C").setInstrumentType(InstrumentType.OPTION)
                .setContractMultiplier(BigDecimal.ONE);
        PaperBrokerCommission commission = new PaperBrokerCommission(-0.2, -2.0,
                new PaperBrokerOptionCommission(-0.3, -0.3, 12.5, 12.5));
        PaperBroker broker = new PaperBroker(quoteEngine, optionTicker, commission, ZERO_LATENCY, 1000.0);

        broker.underlyingPriceUpdated(optionTicker.getSymbol(), new BigDecimal("1000000"), ZonedDateTime.now());
        double fee = broker.calcFee(10.0, 1.0, false);

        assertEquals(-1.25, fee, 0.000000001);
    }

    @Test
    public void testOptionCommissionFallsBackToTradePriceWhenUnderlyingMissing() {
        Ticker optionTicker = new Ticker("ETH-28MAR26-3000-C").setInstrumentType(InstrumentType.OPTION)
                .setContractMultiplier(new BigDecimal("0.1"));
        PaperBrokerCommission commission = new PaperBrokerCommission(-0.2, -2.0,
                new PaperBrokerOptionCommission(-10.0, -10.0, null, null));
        PaperBroker broker = new PaperBroker(quoteEngine, optionTicker, commission, ZERO_LATENCY, 1000.0);

        double fee = broker.calcFee(50.0, 2.0, false);

        assertEquals(-0.01, fee, 0.000000001);
    }

    @Test
    public void testContractMultiplierFeeBasisUsesContractsNotPremiumNotional() {
        Ticker optionTicker = new Ticker("BTC-USD-260327-120000-C").setInstrumentType(InstrumentType.OPTION)
                .setContractMultiplier(new BigDecimal("0.01")).setCurrency("BTC");
        PaperBrokerCommission commission = new PaperBrokerCommission(0.0, 0.0,
                new PaperBrokerOptionCommission(-3.0, -3.0, 7.0, 7.0,
                        PaperBrokerOptionCommission.FeeBasis.CONTRACT_MULTIPLIER, true));
        PaperBroker broker = new PaperBroker(quoteEngine, optionTicker, commission, ZERO_LATENCY, 1000.0);

        double fee = broker.calcFee(0.05, 2.0, false);

        assertEquals(-0.000006, fee, 0.0000000001);
    }

    @Test
    public void testCommissionDefaultsResolveByTickerExchangeAndOptionStyle() {
        Ticker bybitOption = new Ticker("BTC-7MAR26-60000-C-USDT").setInstrumentType(InstrumentType.OPTION)
                .setExchange(Exchange.BYBIT);
        Ticker okxOption = new Ticker("BTC-USD-260327-120000-C").setInstrumentType(InstrumentType.OPTION)
                .setExchange(Exchange.OKX).setCurrency("BTC");
        Ticker deribitInverse = new Ticker("BTC-28MAR26-100000-C").setInstrumentType(InstrumentType.OPTION)
                .setExchange(Exchange.DERIBIT).setCurrency("BTC");
        Ticker deribitLinear = new Ticker("BTC_USDC-28MAR26-100000-C").setInstrumentType(InstrumentType.OPTION)
                .setExchange(Exchange.DERIBIT).setCurrency("USDC");
        Ticker paradexPerp = new Ticker("BTC-USD-PERP").setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setExchange(Exchange.PARADEX);

        assertEquals(PaperBrokerCommission.BYBIT_COMMISSION, PaperBrokerCommission.forTicker(bybitOption));
        assertEquals(PaperBrokerCommission.OKX_COMMISSION, PaperBrokerCommission.forTicker(okxOption));
        assertEquals(PaperBrokerCommission.DERIBIT_INVERSE_COMMISSION, PaperBrokerCommission.forTicker(deribitInverse));
        assertEquals(PaperBrokerCommission.DERIBIT_LINEAR_COMMISSION, PaperBrokerCommission.forTicker(deribitLinear));
        assertEquals(PaperBrokerCommission.PARADEX_COMMISSION, PaperBrokerCommission.forTicker(paradexPerp));
    }

    @Test
    public void testAccountingDefaultsResolveInverseOnlyForInversePerpetualsAndFutures() {
        Ticker deribitPerp = new Ticker("BTC-PERPETUAL").setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setExchange(Exchange.DERIBIT).setCurrency("USD").setContractMultiplier(new BigDecimal("10"));
        Ticker bybitInverseFuture = new Ticker("BTCUSDZ24").setInstrumentType(InstrumentType.FUTURES)
                .setExchange(Exchange.BYBIT).setCurrency("USD");
        Ticker okxInverseFuture = new Ticker("BTC-USD-260327").setInstrumentType(InstrumentType.FUTURES)
                .setExchange(Exchange.OKX).setCurrency("BTC").setContractMultiplier(new BigDecimal("100"));
        Ticker deribitOption = new Ticker("BTC-28MAR26-100000-C").setInstrumentType(InstrumentType.OPTION)
                .setExchange(Exchange.DERIBIT).setCurrency("BTC");

        assertEquals(PaperBrokerAccounting.PnlModelType.INVERSE,
                PaperBrokerAccounting.forTicker(deribitPerp).getPnlModelType());
        assertEquals(PaperBrokerAccounting.PnlModelType.INVERSE,
                PaperBrokerAccounting.forTicker(bybitInverseFuture).getPnlModelType());
        assertEquals(PaperBrokerAccounting.PnlModelType.INVERSE,
                PaperBrokerAccounting.forTicker(okxInverseFuture).getPnlModelType());
        assertEquals(PaperBrokerAccounting.PnlModelType.LINEAR,
                PaperBrokerAccounting.forTicker(deribitOption).getPnlModelType());
    }

    @Test
    public void testInversePerpetualUnrealizedPnlUsesReciprocalPriceMath() {
        Ticker inversePerp = new Ticker("BTCUSD").setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setExchange(Exchange.BYBIT).setCurrency("USD").setContractMultiplier(BigDecimal.ONE);
        PaperBroker broker = new PaperBroker(quoteEngine, inversePerp, PaperBrokerCommission.DEFAULT_COMMISSION,
                ZERO_LATENCY, 1000.0);
        broker.currentPosition = new BigDecimal("1000");
        broker.averageEntryPrice = 100000.0;

        broker.markPriceUpdated(inversePerp.getSymbol(), new BigDecimal("110000"), ZonedDateTime.now());

        double expectedPnl = 1000.0 * ((1.0 / 100000.0) - (1.0 / 110000.0));
        assertEquals(expectedPnl, broker.getUnrealizedPnL(), 0.000000000001);
    }

    @Test
    public void testInversePerpetualCloseUsesReciprocalRealizedPnl() {
        Ticker inversePerp = new Ticker("BTCUSD").setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setExchange(Exchange.BYBIT).setCurrency("USD").setContractMultiplier(BigDecimal.ONE);
        PaperBroker broker = new PaperBroker(quoteEngine, inversePerp, PaperBrokerCommission.DEFAULT_COMMISSION,
                ZERO_LATENCY, 1000.0);
        broker.currentPosition = new BigDecimal("1000");
        broker.averageEntryPrice = 100000.0;

        broker.updatePosition(TradeDirection.SELL, new BigDecimal("400"), 110000.0, false);

        double expectedPnl = 400.0 * ((1.0 / 100000.0) - (1.0 / 110000.0));
        assertEquals(expectedPnl, broker.realizedPnL, 0.000000000001);
        assertEquals(1000.0 + expectedPnl, broker.currentAccountBalance, 0.000000000001);
        assertEquals(new BigDecimal("600"), broker.currentPosition);
        assertEquals(100000.0, broker.averageEntryPrice, 0.0);
    }

    @Test
    public void testInversePerpetualAverageEntryUsesHarmonicMean() {
        Ticker inversePerp = new Ticker("BTCUSD").setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setExchange(Exchange.BYBIT).setCurrency("USD").setContractMultiplier(BigDecimal.ONE);
        PaperBroker broker = new PaperBroker(quoteEngine, inversePerp, PaperBrokerCommission.DEFAULT_COMMISSION,
                ZERO_LATENCY, 1000.0);
        broker.currentPosition = new BigDecimal("1000");
        broker.averageEntryPrice = 100000.0;

        broker.updatePosition(TradeDirection.BUY, new BigDecimal("1000"), 120000.0, false);

        double expectedAverageEntry = 2000.0 / ((1000.0 / 100000.0) + (1000.0 / 120000.0));
        assertEquals(expectedAverageEntry, broker.averageEntryPrice, 0.000000001);
        assertEquals(new BigDecimal("2000"), broker.currentPosition);
    }

    @Test
    public void testCoinSettledOptionPnlRemainsPremiumBased() {
        Ticker optionTicker = new Ticker("BTC-28MAR26-100000-C").setInstrumentType(InstrumentType.OPTION)
                .setExchange(Exchange.DERIBIT).setCurrency("BTC").setContractMultiplier(BigDecimal.ONE);
        PaperBroker broker = new PaperBroker(quoteEngine, optionTicker, PaperBrokerCommission.DEFAULT_COMMISSION,
                ZERO_LATENCY, 1000.0);
        broker.currentPosition = new BigDecimal("2");
        broker.averageEntryPrice = 0.10;

        broker.markPriceUpdated(optionTicker.getSymbol(), new BigDecimal("0.25"), ZonedDateTime.now());

        assertEquals(0.30, broker.getUnrealizedPnL(), 0.000000000001);
    }

    private static class TestPaperBroker extends PaperBroker {

        private boolean interceptCancelRequests;
        private boolean cancelCalled;
        private String cancelledOrderId;
        private boolean cancelShouldDelay;

        private TestPaperBroker(QuoteEngine quoteEngine, Ticker ticker, PaperBrokerCommission commission,
                PaperBrokerLatency latencyModel, double startingBalance) {
            super(quoteEngine, ticker, commission, latencyModel, startingBalance);
        }

        @Override
        public void cancelOrderSubmitWithDelay(String orderId, boolean shouldDelay) {
            if (!interceptCancelRequests) {
                super.cancelOrderSubmitWithDelay(orderId, shouldDelay);
                return;
            }

            cancelCalled = true;
            cancelledOrderId = orderId;
            cancelShouldDelay = shouldDelay;
        }
    }

    private static class TestQuoteEngine extends QuoteEngine {

        private TestQuoteEngine() {
            super(1);
        }

        @Override
        public String getDataProviderName() {
            return "TEST";
        }

        @Override
        public Date getServerTime() {
            return new Date();
        }

        @Override
        public void startEngine() {
        }

        @Override
        public void startEngine(Properties props) {
        }

        @Override
        public void stopEngine() {
        }

        @Override
        public boolean started() {
            return true;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void useDelayedData(boolean useDelayed) {
        }
    }
}
