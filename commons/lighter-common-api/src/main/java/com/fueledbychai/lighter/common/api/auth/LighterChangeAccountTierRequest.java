package com.fueledbychai.lighter.common.api.auth;

public class LighterChangeAccountTierRequest {

    private long accountIndex;
    private String newTier;
    private String auth;

    public long getAccountIndex() {
        return accountIndex;
    }

    public void setAccountIndex(long accountIndex) {
        this.accountIndex = accountIndex;
    }

    public String getNewTier() {
        return newTier;
    }

    public void setNewTier(String newTier) {
        this.newTier = newTier;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public void validate() {
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (newTier == null || newTier.isBlank()) {
            throw new IllegalArgumentException("newTier is required");
        }
    }
}
