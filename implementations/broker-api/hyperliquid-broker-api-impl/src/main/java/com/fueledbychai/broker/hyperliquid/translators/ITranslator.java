package com.fueledbychai.broker.hyperliquid.translators;

import java.math.BigDecimal;
import java.util.List;

import com.fueledbychai.BestBidOffer;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.hyperliquid.HyperliquidOrderTicket;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.hyperliquid.ws.json.OrderAction;
import com.fueledbychai.hyperliquid.ws.json.OrderJson;
import com.fueledbychai.hyperliquid.ws.listeners.accountinfo.HyperliquidPositionUpdate;
import com.fueledbychai.hyperliquid.ws.listeners.userfills.WsUserFill;

public interface ITranslator {

    OrderAction translateOrderTicket(OrderTicket ticket, BestBidOffer bestBidOffer);

    OrderAction translateOrderTickets(HyperliquidOrderTicket ticket);

    OrderAction translateOrderTickets(List<HyperliquidOrderTicket> hyperliquidOrderTickets);

    OrderJson translateOrderTicketToOrderJson(OrderTicket ticket, BestBidOffer bestBidOffer);

    List<Position> translatePositions(List<HyperliquidPositionUpdate> positionUpdates);

    Position translatePosition(HyperliquidPositionUpdate positionUpdate);

    String getBuySlippage(Ticker ticker, BigDecimal currentAsk);

    String getSellSlippage(Ticker ticker, BigDecimal currentBid);

    List<Fill> translateFill(WsUserFill wsUserFill);

}