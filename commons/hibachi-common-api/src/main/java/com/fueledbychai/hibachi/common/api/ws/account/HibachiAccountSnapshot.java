package com.fueledbychai.hibachi.common.api.ws.account;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class HibachiAccountSnapshot {

    private final long accountId;
    private final BigDecimal balance;
    private final List<HibachiPosition> positions;
    private final String listenKey;

    public HibachiAccountSnapshot(long accountId, BigDecimal balance, List<HibachiPosition> positions,
                                  String listenKey) {
        this.accountId = accountId;
        this.balance = balance;
        this.positions = positions == null ? Collections.emptyList() : List.copyOf(positions);
        this.listenKey = listenKey;
    }

    public long getAccountId() { return accountId; }
    public BigDecimal getBalance() { return balance; }
    public List<HibachiPosition> getPositions() { return positions; }
    public String getListenKey() { return listenKey; }
}
