package com.fueledbychai.grvt.common.api.model;

/**
 * EIP-712 signature envelope attached to signed GRVT payloads (orders, transfers, etc.).
 * <p>
 * {@code expiration} is a unix-nanoseconds timestamp ({@code int64}) carried as a string to avoid
 * precision loss; {@code nonce} is a {@code uint32} deconfliction value.
 */
public class GrvtSignature {

    private String signer;
    private String r;
    private String s;
    private int v;
    private String expiration;
    private long nonce;

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public String getR() {
        return r;
    }

    public void setR(String r) {
        this.r = r;
    }

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

    public int getV() {
        return v;
    }

    public void setV(int v) {
        this.v = v;
    }

    public String getExpiration() {
        return expiration;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }
}
