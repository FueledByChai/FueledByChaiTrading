package com.fueledbychai.broker.paradex;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderStatus.Status;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.common.ParadexTickerRegistry;
import com.fueledbychai.paradex.common.api.ParadexUtil;
import com.fueledbychai.paradex.common.api.order.OrderType;
import com.fueledbychai.paradex.common.api.order.ParadexOrder;
import com.fueledbychai.paradex.common.api.order.Side;
import com.fueledbychai.paradex.common.api.ws.fills.ParadexFill;
import com.fueledbychai.paradex.common.api.ws.orderstatus.CancelReason;
import com.fueledbychai.paradex.common.api.ws.orderstatus.IParadexOrderStatusUpdate;
import com.fueledbychai.paradex.common.api.ws.orderstatus.ParadexOrderStatus;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.Util;

public class ParadexTranslator implements IParadexTranslator {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ParadexTranslator.class);
    protected static ITickerRegistry tickerRegistry = ParadexTickerRegistry.getInstance();
    protected static IParadexTranslator instance;

    public static IParadexTranslator getInstance() {
        if (instance == null) {
            instance = new ParadexTranslator();
        }
        return instance;
    }

    @Override
    public OrderStatus translateOrderStatus(IParadexOrderStatusUpdate paradexStatus) {

        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(paradexStatus.getTickerString());
        Status status = translateStatusCode(paradexStatus.getStatus(), paradexStatus.getCancelReason(),
                paradexStatus.getOriginalSize(), paradexStatus.getRemainingSize());
        OrderStatus orderStatus = null;
        BigDecimal filledSize = paradexStatus.getOriginalSize().subtract(paradexStatus.getRemainingSize());

        ZonedDateTime timestamp = paradexStatus.getTimestamp() == 0 ? ZonedDateTime.now()
                : ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(paradexStatus.getTimestamp()),
                        ZoneId.of("UTC"));

        orderStatus = new OrderStatus(status, paradexStatus.getOrderId(), filledSize, paradexStatus.getRemainingSize(),
                paradexStatus.getAverageFillPrice(), ticker, timestamp);

        if (status == Status.CANCELED) {
            logger.warn("Order {} was canceled. Cancel reason: {}", paradexStatus.getOrderId(),
                    paradexStatus.getCancelReasonString());
            if (paradexStatus.getCancelReason() == CancelReason.POST_ONLY_WOULD_CROSS) {
                orderStatus.setCancelReason(OrderStatus.CancelReason.POST_ONLY_WOULD_CROSS);
            } else if (paradexStatus.getCancelReason() == CancelReason.USER_CANCELED) {
                orderStatus.setCancelReason(OrderStatus.CancelReason.USER_CANCELED);
            } else {
                orderStatus.setCancelReason(OrderStatus.CancelReason.UNKNOWN);
            }
        }

        return orderStatus;
    }

    @Override
    public Status translateStatusCode(ParadexOrderStatus paradexStatus, CancelReason cancelReason,
            BigDecimal originalSize, BigDecimal remainingSize) {

        if (paradexStatus == ParadexOrderStatus.NEW || paradexStatus == ParadexOrderStatus.UNTRIGGERED) {
            return Status.NEW;
        }

        if (paradexStatus == ParadexOrderStatus.CLOSED) {
            if (cancelReason != CancelReason.NONE) {
                return Status.CANCELED;
            }

            if (remainingSize != null && remainingSize.compareTo(BigDecimal.ZERO) == 0) {
                return Status.FILLED;
            }

        }

        if (paradexStatus == ParadexOrderStatus.OPEN) {
            if (remainingSize != null && originalSize != null && remainingSize.compareTo(originalSize) < 0) {
                return Status.PARTIAL_FILL;
            }

            return Status.NEW;
        }

        throw new FueledByChaiException("Unknown Paradex order status: " + paradexStatus);

    }

    @Override
    public Fill translateFill(ParadexFill paradexFill) {
        Fill fill = new Fill();
        fill.setTicker(tickerRegistry.lookupByBrokerSymbol(paradexFill.getMarket()));
        fill.setPrice(new BigDecimal(paradexFill.getPrice()));
        fill.setFillId(paradexFill.getId());
        fill.setSize(new BigDecimal(paradexFill.getSize()));
        fill.setSide(
                paradexFill.getSide().equals(ParadexFill.Side.BUY) ? com.fueledbychai.broker.order.TradeDirection.BUY
                        : com.fueledbychai.broker.order.TradeDirection.SELL);
        fill.setTime(Util.convertEpochToZonedDateTime(paradexFill.getCreatedAt()));
        fill.setOrderId(String.valueOf(paradexFill.getOrderId()));
        fill.setTaker(paradexFill.getLiquidity() == ParadexFill.LiquidityType.TAKER);
        fill.setCommission(new BigDecimal(paradexFill.getFee()));
        return fill;
    }

    @Override
    public OrderTicket translateOrder(ParadexOrder order) {
        OrderTicket tradeOrder = new OrderTicket();
        tradeOrder.setClientOrderId(order.getClientId());
        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(order.getTicker());
        tradeOrder.setTicker(ticker);
        tradeOrder.setSize(order.getSize());
        tradeOrder.setLimitPrice(order.getLimitPrice());
        if (order.getSide() == Side.BUY) {
            tradeOrder.setDirection(TradeDirection.BUY);
        } else {
            tradeOrder.setDirection(TradeDirection.SELL);
        }
        if (order.getOrderType() == OrderType.MARKET) {
            tradeOrder.setType(OrderTicket.Type.MARKET);
        } else if (order.getOrderType() == OrderType.LIMIT) {
            tradeOrder.setType(OrderTicket.Type.LIMIT);
        } else if (order.getOrderType() == OrderType.STOP) {
            tradeOrder.setType(OrderTicket.Type.STOP);
        } else {
            throw new UnsupportedOperationException("Order type " + order.getOrderType() + " is not supported");
        }

        tradeOrder.setOrderId(order.getOrderId());
        Status currentStatus = null;
        if (order.getOrderStatus() == ParadexOrderStatus.CLOSED) {
            if (order.getCancelReason() != null && order.getCancelReason().length() > 0) {
                currentStatus = Status.CANCELED;
            } else {
                currentStatus = Status.FILLED;
            }
        } else if (order.getOrderStatus() == ParadexOrderStatus.OPEN) {
            if (order.getRemainingSize().compareTo(order.getSize()) < 0) {
                currentStatus = Status.PARTIAL_FILL;
            } else {
                currentStatus = Status.NEW;
            }
        } else if (order.getOrderStatus() == ParadexOrderStatus.NEW
                || order.getOrderStatus() == ParadexOrderStatus.UNTRIGGERED) {
            currentStatus = translateStatusCode(order.getOrderStatus(), CancelReason.NONE, order.getSize(),
                    order.getRemainingSize());
            tradeOrder.setCurrentStatus(currentStatus);
        }

        return tradeOrder;

    }

    @Override
    public ParadexOrder translateOrder(OrderTicket order) {
        ParadexOrder paradoxOrder = new ParadexOrder();
        paradoxOrder.setClientId(order.getClientOrderId());
        paradoxOrder.setTicker(order.getTicker().getSymbol());

        if (order.getTradeDirection() == TradeDirection.BUY) {
            paradoxOrder.setSide(Side.BUY);
        } else {
            paradoxOrder.setSide(Side.SELL);
        }

        paradoxOrder.setSize(order.getSize());

        if (order.getType() == OrderTicket.Type.MARKET) {
            paradoxOrder.setOrderType(OrderType.MARKET);
        } else if (order.getType() == OrderTicket.Type.LIMIT) {
            paradoxOrder.setOrderType(OrderType.LIMIT);
        } else if (order.getType() == OrderTicket.Type.STOP) {
            paradoxOrder.setOrderType(OrderType.STOP);
        } else {
            throw new UnsupportedOperationException("Order type " + order.getType() + " is not supported");
        }

        if (order.getType() == OrderTicket.Type.LIMIT) {
            // need to format the limit price based on the minimum tick size for the symbol.
            BigDecimal limitPrice = ParadexUtil.formatPrice(order.getLimitPrice(),
                    order.getTicker().getMinimumTickSize());
            paradoxOrder.setLimitPrice(limitPrice);
        }

        return paradoxOrder;
    }

    @Override
    public List<OrderTicket> translateOrders(List<ParadexOrder> orders) {
        return orders.stream().map(this::translateOrder).collect(Collectors.toList());
    }
}
