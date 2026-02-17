package com.fueledbychai.lighter.common.api.ws.model;

import java.math.BigDecimal;

public class LighterTrade {

    private Long id;
    private String txHash;
    private String type;
    private Integer marketId;
    private BigDecimal size;
    private BigDecimal price;
    private BigDecimal usdAmount;
    private Long askId;
    private Long bidId;
    private Long askAccountId;
    private Long bidAccountId;
    private Boolean makerAsk;
    private Long blockHeight;
    private Long timestamp;
    private BigDecimal takerFee;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getMarketId() {
        return marketId;
    }

    public void setMarketId(Integer marketId) {
        this.marketId = marketId;
    }

    public BigDecimal getSize() {
        return size;
    }

    public void setSize(BigDecimal size) {
        this.size = size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getUsdAmount() {
        return usdAmount;
    }

    public void setUsdAmount(BigDecimal usdAmount) {
        this.usdAmount = usdAmount;
    }

    public Long getAskId() {
        return askId;
    }

    public void setAskId(Long askId) {
        this.askId = askId;
    }

    public Long getBidId() {
        return bidId;
    }

    public void setBidId(Long bidId) {
        this.bidId = bidId;
    }

    public Long getAskAccountId() {
        return askAccountId;
    }

    public void setAskAccountId(Long askAccountId) {
        this.askAccountId = askAccountId;
    }

    public Long getBidAccountId() {
        return bidAccountId;
    }

    public void setBidAccountId(Long bidAccountId) {
        this.bidAccountId = bidAccountId;
    }

    public Boolean getMakerAsk() {
        return makerAsk;
    }

    public void setMakerAsk(Boolean makerAsk) {
        this.makerAsk = makerAsk;
    }

    public Long getBlockHeight() {
        return blockHeight;
    }

    public void setBlockHeight(Long blockHeight) {
        this.blockHeight = blockHeight;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getTakerFee() {
        return takerFee;
    }

    public void setTakerFee(BigDecimal takerFee) {
        this.takerFee = takerFee;
    }
}
