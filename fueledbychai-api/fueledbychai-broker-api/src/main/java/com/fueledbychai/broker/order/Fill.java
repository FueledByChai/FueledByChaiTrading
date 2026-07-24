package com.fueledbychai.broker.order;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.fueledbychai.data.Ticker;

public class Fill {

    protected boolean isSnapshot = false;
    protected Ticker ticker;
    protected BigDecimal price;
    protected BigDecimal size;
    protected TradeDirection side;
    protected ZonedDateTime time;
    protected String orderId;
    protected String clientOrderId;
    protected BigDecimal commission;
    protected String fillId;
    protected boolean isTaker;
    // Exchange-reported realized P&L / funding for THIS execution (closing portion
    // only; opening fills report 0). Authoritative — handles partial fills, flips,
    // and avg-entry natively. Null when the venue doesn't supply it. Consumers
    // should prefer this over recomputing realized from (price - avgEntry) * size.
    protected BigDecimal realizedPnl;
    protected BigDecimal realizedFunding;

    public Ticker getTicker() {
        return ticker;
    }

    public void setTicker(Ticker ticker) {
        this.ticker = ticker;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getSize() {
        return size;
    }

    public void setSize(BigDecimal size) {
        this.size = size;
    }

    public TradeDirection getSide() {
        return side;
    }

    public void setSide(TradeDirection side) {
        this.side = side;
    }

    public ZonedDateTime getTime() {
        return time;
    }

    public void setTime(ZonedDateTime time) {
        this.time = time;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public String getFillId() {
        return fillId;
    }

    public void setFillId(String fillId) {
        this.fillId = fillId;
    }

    public boolean isTaker() {
        return isTaker;
    }

    public void setTaker(boolean isTaker) {
        this.isTaker = isTaker;
    }

    public boolean isSnapshot() {
        return isSnapshot;
    }

    public void setSnapshot(boolean isSnapshot) {
        this.isSnapshot = isSnapshot;
    }

    /** Exchange-reported realized P&L for this execution (null if the venue didn't supply it). */
    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    /** Exchange-reported realized funding for this execution (null if not supplied). */
    public BigDecimal getRealizedFunding() {
        return realizedFunding;
    }

    public void setRealizedFunding(BigDecimal realizedFunding) {
        this.realizedFunding = realizedFunding;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((ticker == null) ? 0 : ticker.hashCode());
        result = prime * result + ((price == null) ? 0 : price.hashCode());
        result = prime * result + ((size == null) ? 0 : size.hashCode());
        result = prime * result + ((side == null) ? 0 : side.hashCode());
        result = prime * result + ((time == null) ? 0 : time.hashCode());
        result = prime * result + ((orderId == null) ? 0 : orderId.hashCode());
        result = prime * result + ((clientOrderId == null) ? 0 : clientOrderId.hashCode());
        result = prime * result + ((commission == null) ? 0 : commission.hashCode());
        result = prime * result + ((fillId == null) ? 0 : fillId.hashCode());
        result = prime * result + (isTaker ? 1231 : 1237);
        result = prime * result + (isSnapshot ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Fill other = (Fill) obj;
        if (ticker == null) {
            if (other.ticker != null)
                return false;
        } else if (!ticker.equals(other.ticker))
            return false;
        if (price == null) {
            if (other.price != null)
                return false;
        } else if (!price.equals(other.price))
            return false;
        if (size == null) {
            if (other.size != null)
                return false;
        } else if (!size.equals(other.size))
            return false;
        if (side != other.side)
            return false;
        if (time == null) {
            if (other.time != null)
                return false;
        } else if (!time.equals(other.time))
            return false;
        if (orderId == null) {
            if (other.orderId != null)
                return false;
        } else if (!orderId.equals(other.orderId))
            return false;
        if (clientOrderId == null) {
            if (other.clientOrderId != null)
                return false;
        } else if (!clientOrderId.equals(other.clientOrderId))
            return false;
        if (commission == null) {
            if (other.commission != null)
                return false;
        } else if (!commission.equals(other.commission))
            return false;
        if (fillId == null) {
            if (other.fillId != null)
                return false;
        } else if (!fillId.equals(other.fillId))
            return false;
        if (isTaker != other.isTaker)
            return false;
        if (isSnapshot != other.isSnapshot)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Fill [ticker=" + ticker + ", price=" + price + ", size=" + size + ", side=" + side + ", time=" + time
                + ", orderId=" + orderId + ", clientOrderId=" + clientOrderId + ", commission=" + commission
                + ", fillId=" + fillId + ", isTaker=" + isTaker + ", isSnapshot=" + isSnapshot
                + ", realizedPnl=" + realizedPnl + ", realizedFunding=" + realizedFunding + "]";
    }

}
