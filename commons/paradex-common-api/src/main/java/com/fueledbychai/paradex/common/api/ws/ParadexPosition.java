package com.fueledbychai.paradex.common.api.ws;

import java.math.BigDecimal;

public class ParadexPosition {

    protected String market;
    protected BigDecimal avgEntryPrice;
    protected BigDecimal avgExitPrice;
    protected String id;
    protected long closedAt;
    protected long createdAt;
    protected BigDecimal liquidationPrice;
    protected BigDecimal realizedPnl;
    protected BigDecimal realizedFundingPnL;
    protected BigDecimal unrealizedPnl;
    protected BigDecimal unrealizedFundingPnL;
}
