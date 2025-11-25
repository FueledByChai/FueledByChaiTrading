package com.fueledbychai.paradex.common.api;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import com.fueledbychai.data.BarData;
import com.fueledbychai.paradex.common.api.historical.OHLCBar;

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

    public static BigDecimal formatSize(BigDecimal size, BigDecimal minOrderSize) {
        if (size == null || minOrderSize == null || minOrderSize.compareTo(BigDecimal.ZERO) <= 0) {
            return size;
        }
        BigDecimal divided = size.divide(minOrderSize, 0, java.math.RoundingMode.DOWN);
        return divided.multiply(minOrderSize);
    }

}
