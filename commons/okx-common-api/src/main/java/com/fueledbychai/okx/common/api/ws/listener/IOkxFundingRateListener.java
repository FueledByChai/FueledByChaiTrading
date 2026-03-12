package com.fueledbychai.okx.common.api.ws.listener;

import com.fueledbychai.okx.common.api.ws.model.OkxFundingRateUpdate;

public interface IOkxFundingRateListener {

    void onFundingRate(OkxFundingRateUpdate update);
}
