package com.fueledbychai.lighter.common.api.signer;

public class LighterApiKey {

    private final String publicKey;
    private final String privateKey;

    public LighterApiKey(String publicKey, String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }
}
