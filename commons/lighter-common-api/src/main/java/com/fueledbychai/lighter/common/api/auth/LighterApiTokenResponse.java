package com.fueledbychai.lighter.common.api.auth;

public class LighterApiTokenResponse {

    private int code;
    private String message;
    private long tokenId;
    private String apiToken;
    private String name;
    private long accountIndex;
    private long expiry;
    private boolean subAccountAccess;
    private boolean revoked;
    private String scopes;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTokenId() {
        return tokenId;
    }

    public void setTokenId(long tokenId) {
        this.tokenId = tokenId;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getAccountIndex() {
        return accountIndex;
    }

    public void setAccountIndex(long accountIndex) {
        this.accountIndex = accountIndex;
    }

    public long getExpiry() {
        return expiry;
    }

    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }

    public boolean isSubAccountAccess() {
        return subAccountAccess;
    }

    public void setSubAccountAccess(boolean subAccountAccess) {
        this.subAccountAccess = subAccountAccess;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }
}
