package com.fueledbychai.lighter.common.api.auth;

public enum LighterAccountTier {
    STANDARD("standard"),
    PREMIUM("premium");

    private final String apiValue;

    LighterAccountTier(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }
}
