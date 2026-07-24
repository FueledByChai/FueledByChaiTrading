package com.fueledbychai.grvt.common.api.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A GRVT order payload. Mirrors the {@code Order} struct in the GRVT Python SDK. Only the fields
 * within the EIP-712 {@code Order} type are trustlessly enforced on-chain; {@code clientOrderId}
 * is backend-only metadata.
 */
public class GrvtOrder {

    private String subAccountId;
    private boolean market;
    private GrvtTimeInForce timeInForce = GrvtTimeInForce.GOOD_TILL_TIME;
    private boolean postOnly;
    private boolean reduceOnly;
    private List<GrvtOrderLeg> legs = new ArrayList<>();
    private GrvtSignature signature = new GrvtSignature();
    private String clientOrderId;

    public String getSubAccountId() {
        return subAccountId;
    }

    public void setSubAccountId(String subAccountId) {
        this.subAccountId = subAccountId;
    }

    public boolean isMarket() {
        return market;
    }

    public void setMarket(boolean market) {
        this.market = market;
    }

    public GrvtTimeInForce getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(GrvtTimeInForce timeInForce) {
        this.timeInForce = timeInForce;
    }

    public boolean isPostOnly() {
        return postOnly;
    }

    public void setPostOnly(boolean postOnly) {
        this.postOnly = postOnly;
    }

    public boolean isReduceOnly() {
        return reduceOnly;
    }

    public void setReduceOnly(boolean reduceOnly) {
        this.reduceOnly = reduceOnly;
    }

    public List<GrvtOrderLeg> getLegs() {
        return legs;
    }

    public void setLegs(List<GrvtOrderLeg> legs) {
        this.legs = legs;
    }

    public GrvtSignature getSignature() {
        return signature;
    }

    public void setSignature(GrvtSignature signature) {
        this.signature = signature;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }
}
