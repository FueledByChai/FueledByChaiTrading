package com.fueledbychai.broker.lighter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.BrokerFactory;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

/**
 * Simple example that places a BTC market order on Lighter using the service
 * registries.
 */
public class LighterMarketOrderExample {

    private static final Logger logger = LoggerFactory.getLogger(LighterMarketOrderExample.class);
    private static final String ORDER_SYMBOL = "BTC";
    private static final InstrumentType ORDER_INSTRUMENT_TYPE = InstrumentType.PERPETUAL_FUTURES;
    private static final TradeDirection ORDER_SIDE = TradeDirection.BUY;
    private static final BigDecimal ORDER_SIZE = new BigDecimal("0.001");

    public static void main(String[] args) {
        ITickerRegistry tickerRegistry = TickerRegistryFactory.getInstance(Exchange.LIGHTER);
        IBroker broker = BrokerFactory.getInstance(Exchange.LIGHTER);

        Ticker ticker = resolveTicker(tickerRegistry, ORDER_INSTRUMENT_TYPE, ORDER_SYMBOL);
        if (ticker == null) {
            throw new IllegalStateException("Unable to resolve ticker for symbol " + ORDER_SYMBOL + " on Lighter.");
        }

        logger.info("Preparing hardcoded market order symbol={} side={} size={}", ticker.getSymbol(), ORDER_SIDE,
                ORDER_SIZE);

        try {
            broker.connect();

            OrderTicket order = new OrderTicket();
            order.setTicker(ticker);
            order.setDirection(ORDER_SIDE);
            order.setType(OrderTicket.Type.MARKET);
            order.setSize(ORDER_SIZE);
            order.setClientOrderId(broker.getNextOrderId());
            order.setOrderEntryTime(ZonedDateTime.now());

            BrokerRequestResult result = broker.placeOrder(order);
            if (!result.isSuccess()) {
                logger.error("Order placement failed: {}", result);
                return;
            }

            logger.info("Order submitted. clientOrderId={} provisionalOrderId={}", order.getClientOrderId(),
                    order.getOrderId());

            OrderTicket updated = broker.requestOrderStatusByClientOrderId(order.getClientOrderId());
            if (updated != null) {
                logger.info("Order status: orderId={} status={} filledSize={} filledPrice={}", updated.getOrderId(),
                        updated.getCurrentStatus(), updated.getFilledSize(), updated.getFilledPrice());
            } else {
                logger.info("No immediate status available for clientOrderId={}", order.getClientOrderId());
            }
        } catch (Exception ex) {
            logger.error("Error while placing Lighter market order", ex);
        } finally {
            broker.disconnect();
        }
    }

    private static Ticker resolveTicker(ITickerRegistry tickerRegistry, InstrumentType instrumentType, String symbol) {
        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(instrumentType, symbol);
        if (ticker != null) {
            return ticker;
        }

        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            ticker = tickerRegistry.lookupByCommonSymbol(instrumentType, symbol + "/USDC");
            if (ticker != null) {
                return ticker;
            }
            ticker = tickerRegistry.lookupByCommonSymbol(instrumentType, symbol + "/USD");
            if (ticker != null) {
                return ticker;
            }
        }

        ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.CRYPTO_SPOT, symbol);
        if (ticker != null) {
            return ticker;
        }
        ticker = tickerRegistry.lookupByCommonSymbol(InstrumentType.CRYPTO_SPOT, symbol + "/USDC");
        if (ticker != null) {
            return ticker;
        }
        return tickerRegistry.lookupByCommonSymbol(InstrumentType.CRYPTO_SPOT, symbol + "/USD");
    }
}
