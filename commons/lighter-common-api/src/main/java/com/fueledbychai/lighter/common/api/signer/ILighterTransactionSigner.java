package com.fueledbychai.lighter.common.api.signer;

import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;

public interface ILighterTransactionSigner {

    LighterSignedTransaction signCreateOrder(LighterCreateOrderRequest orderRequest);

    LighterSignedTransaction signCancelOrder(LighterCancelOrderRequest cancelRequest);

    LighterSignedTransaction signModifyOrder(LighterModifyOrderRequest modifyRequest);
}
