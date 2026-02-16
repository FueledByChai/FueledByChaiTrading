package com.fueledbychai.lighter.common.api;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.lighter.common.api.auth.LighterApiTokenResponse;
import com.fueledbychai.lighter.common.api.auth.LighterCreateApiTokenRequest;

public interface ILighterRestApi {

    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    public InstrumentDescriptor getInstrumentDescriptor(String symbol);

    public LighterApiTokenResponse createApiToken(String authToken, LighterCreateApiTokenRequest request);

    public LighterApiTokenResponse createApiToken(LighterCreateApiTokenRequest request);

    public String getApiToken();
}
