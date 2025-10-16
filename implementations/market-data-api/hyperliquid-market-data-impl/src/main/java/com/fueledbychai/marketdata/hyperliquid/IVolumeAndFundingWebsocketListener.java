package com.fueledbychai.marketdata.hyperliquid;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.fueledbychai.data.Ticker;

public interface IVolumeAndFundingWebsocketListener {

    void volumeAndFundingUpdate(Ticker ticker, BigDecimal volume, BigDecimal volumeNotional, BigDecimal fundingRate,
            BigDecimal markPrice, BigDecimal openInterest, ZonedDateTime timestamp);
}
