package com.fueledbychai.paradex.common.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.BarData;
import com.fueledbychai.paradex.common.api.historical.OHLCBar;
import com.fueledbychai.paradex.common.api.order.OrderType;
import com.fueledbychai.paradex.common.api.order.ParadexOrder;
import com.fueledbychai.paradex.common.api.order.Side;

public class ParadexUtil {

    public static BarData translateBar(OHLCBar bar) {
        BarData barData = new BarData(java.time.Instant.ofEpochSecond(bar.getTime()).atZone(ZoneOffset.UTC),
                new BigDecimal(String.valueOf(bar.getOpen())), new BigDecimal(String.valueOf(bar.getHigh())),
                new BigDecimal(String.valueOf(bar.getLow())), new BigDecimal(String.valueOf(bar.getClose())),
                new BigDecimal(String.valueOf(bar.getVolume())));
        return barData;
    }

    public static List<BarData> translateBars(List<OHLCBar> bars) {
        return bars.stream().map(ParadexUtil::translateBar).collect(Collectors.toList());
    }

    public static String commonSymbolToParadexSymbol(String commonSymbol) {
        String paradexSymbol = commonSymbol.toUpperCase() + "-USD-PERP"; // Placeholder for actual conversion logic
        return paradexSymbol;
    }

    public static BigDecimal formatPrice(BigDecimal price, BigDecimal tickSize) {
        if (price == null || tickSize == null || tickSize.compareTo(BigDecimal.ZERO) <= 0) {
            return price;
        }
        BigDecimal divided = price.divide(tickSize, 0, java.math.RoundingMode.DOWN);
        return divided.multiply(tickSize);
    }

}
