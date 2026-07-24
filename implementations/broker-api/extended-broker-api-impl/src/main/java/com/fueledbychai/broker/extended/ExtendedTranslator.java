package com.fueledbychai.broker.extended;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderStatus.Status;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.extended.common.api.order.ExtendedOrder;
import com.fueledbychai.extended.common.api.order.ExtendedOrderStatus;
import com.fueledbychai.extended.common.api.order.OrderType;
import com.fueledbychai.extended.common.api.order.Side;
import com.fueledbychai.extended.common.api.order.TimeInForce;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class ExtendedTranslator implements IExtendedTranslator {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ExtendedTranslator.class);
    protected static ITickerRegistry tickerRegistry = TickerRegistryFactory.getInstance(Exchange.EXTENDED);
    protected static IExtendedTranslator instance;

    public static IExtendedTranslator getInstance() {
        if (instance == null) {
            instance = new ExtendedTranslator();
        }
        return instance;
    }

    @Override
    public ExtendedOrder translateOrder(OrderTicket order) {
        ExtendedOrder extendedOrder = new ExtendedOrder();

        if (order.getOrderId() != null && !order.getOrderId().isEmpty()) {
            extendedOrder.setId(order.getOrderId());
        }
        extendedOrder.setExternalId(order.getClientOrderId());

        // The exchange (broker) symbol for the ticker.
        extendedOrder.setMarket(order.getTicker().getSymbol());

        if (order.getTradeDirection() == TradeDirection.BUY) {
            extendedOrder.setSide(Side.BUY);
        } else {
            extendedOrder.setSide(Side.SELL);
        }

        extendedOrder.setQty(order.getSize());
        extendedOrder.setPrice(order.getLimitPrice());

        if (order.getType() == OrderTicket.Type.MARKET) {
            extendedOrder.setType(OrderType.MARKET);
        } else if (order.getType() == OrderTicket.Type.LIMIT) {
            extendedOrder.setType(OrderType.LIMIT);
        } else {
            throw new UnsupportedOperationException("Order type " + order.getType() + " is not supported");
        }

        // Default time in force.
        extendedOrder.setTimeInForce(TimeInForce.GTT);

        if (order.getModifiers().contains(OrderTicket.Modifier.POST_ONLY)
                || order.getModifiers().contains(OrderTicket.Modifier.RPI)) {
            extendedOrder.setPostOnly(true);
        }
        if (order.getModifiers().contains(OrderTicket.Modifier.REDUCE_ONLY)) {
            extendedOrder.setReduceOnly(true);
        }

        return extendedOrder;
    }

    @Override
    public OrderTicket translateOrder(ExtendedOrder order) {
        OrderTicket tradeOrder = new OrderTicket();
        tradeOrder.setClientOrderId(order.getExternalId());
        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, order.getMarket());
        tradeOrder.setTicker(ticker);
        tradeOrder.setSize(order.getQty());
        tradeOrder.setLimitPrice(order.getPrice());

        if (order.getSide() == Side.BUY) {
            tradeOrder.setDirection(TradeDirection.BUY);
        } else {
            tradeOrder.setDirection(TradeDirection.SELL);
        }

        if (order.getType() == OrderType.MARKET) {
            tradeOrder.setType(OrderTicket.Type.MARKET);
        } else if (order.getType() == OrderType.LIMIT) {
            tradeOrder.setType(OrderTicket.Type.LIMIT);
        } else {
            tradeOrder.setType(OrderTicket.Type.LIMIT);
        }

        if (order.getId() != null) {
            tradeOrder.setOrderId(order.getId());
        }
        if (order.getFilledQty() != null) {
            tradeOrder.setFilledSize(order.getFilledQty());
        }
        if (order.getAveragePrice() != null) {
            tradeOrder.setFilledPrice(order.getAveragePrice());
        }

        tradeOrder.setCurrentStatus(translateStatusCode(order.getStatus()));

        return tradeOrder;
    }

    @Override
    public List<OrderTicket> translateOrders(List<ExtendedOrder> orders) {
        return orders.stream().map(this::translateOrder).collect(Collectors.toList());
    }

    @Override
    public Status translateStatusCode(ExtendedOrderStatus status) {
        if (status == null) {
            return Status.UNKNOWN;
        }
        switch (status) {
        case NEW:
        case UNTRIGGERED:
            return Status.NEW;
        case PARTIALLY_FILLED:
            return Status.PARTIAL_FILL;
        case FILLED:
            return Status.FILLED;
        case CANCELLED:
        case EXPIRED:
            return Status.CANCELED;
        case REJECTED:
            return Status.REJECTED;
        default:
            logger.warn("Unknown Extended order status: {}", status);
            return Status.UNKNOWN;
        }
    }
}
