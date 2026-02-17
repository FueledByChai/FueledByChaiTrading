package com.fueledbychai.lighter.common.api;

import java.util.List;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.lighter.common.api.account.LighterPosition;
import com.fueledbychai.lighter.common.api.auth.LighterApiTokenResponse;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierRequest;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierResponse;
import com.fueledbychai.lighter.common.api.auth.LighterCreateApiTokenRequest;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;

public interface ILighterRestApi {

    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    public InstrumentDescriptor getInstrumentDescriptor(String symbol);

    public LighterApiTokenResponse createApiToken(String authToken, LighterCreateApiTokenRequest request);

    public LighterApiTokenResponse createApiToken(LighterCreateApiTokenRequest request);

    public LighterChangeAccountTierResponse changeAccountTier(String authToken, LighterChangeAccountTierRequest request);

    public LighterChangeAccountTierResponse changeAccountTier(LighterChangeAccountTierRequest request);

    public String getApiToken();

    public long getNextNonce(long accountIndex, int apiKeyIndex);

    public List<LighterPosition> getPositions(long accountIndex);

    public List<LighterOrder> getAccountActiveOrders(String authToken, long accountIndex, int marketId);

    public List<LighterOrder> getAccountActiveOrders(long accountIndex, int marketId);
}
