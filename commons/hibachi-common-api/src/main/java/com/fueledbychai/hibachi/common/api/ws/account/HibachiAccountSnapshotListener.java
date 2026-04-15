package com.fueledbychai.hibachi.common.api.ws.account;

@FunctionalInterface
public interface HibachiAccountSnapshotListener {
    void onAccountSnapshot(HibachiAccountSnapshot snapshot);
}
