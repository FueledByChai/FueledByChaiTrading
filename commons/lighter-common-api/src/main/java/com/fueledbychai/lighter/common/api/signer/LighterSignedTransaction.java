package com.fueledbychai.lighter.common.api.signer;

import org.json.JSONObject;

public class LighterSignedTransaction {

    private final int txType;
    private final JSONObject txInfo;
    private final String txHash;
    private final String messageToSign;

    public LighterSignedTransaction(int txType, JSONObject txInfo, String txHash, String messageToSign) {
        this.txType = txType;
        this.txInfo = txInfo;
        this.txHash = txHash;
        this.messageToSign = messageToSign;
    }

    public int getTxType() {
        return txType;
    }

    public JSONObject getTxInfo() {
        return txInfo;
    }

    public String getTxHash() {
        return txHash;
    }

    public String getMessageToSign() {
        return messageToSign;
    }
}
