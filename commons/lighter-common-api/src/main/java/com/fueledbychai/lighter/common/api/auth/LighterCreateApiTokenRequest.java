package com.fueledbychai.lighter.common.api.auth;

public class LighterCreateApiTokenRequest {

    public static final String DEFAULT_SCOPES = "read.*";

    private String name;
    private long accountIndex;
    private long expiry;
    private boolean subAccountAccess;
    private String scopes = DEFAULT_SCOPES;

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

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (expiry <= 0) {
            throw new IllegalArgumentException("expiry must be > 0");
        }
        if (scopes == null || scopes.isBlank()) {
            scopes = DEFAULT_SCOPES;
        }
    }
}
