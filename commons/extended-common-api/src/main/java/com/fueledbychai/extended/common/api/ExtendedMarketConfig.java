package com.fueledbychai.extended.common.api;

import com.swmansion.starknet.data.types.Felt;

/**
 * Per-market StarkEx settlement configuration (from a market's {@code l2Config}),
 * needed to scale amounts and build the order hash when signing.
 */
public class ExtendedMarketConfig {

    private final String market;
    private final Felt syntheticAssetId;
    private final long syntheticResolution;
    private final Felt collateralAssetId;
    private final long collateralResolution;

    public ExtendedMarketConfig(String market, String syntheticAssetIdHex, long syntheticResolution,
            String collateralAssetIdHex, long collateralResolution) {
        this.market = market;
        this.syntheticAssetId = Felt.fromHex(syntheticAssetIdHex);
        this.syntheticResolution = syntheticResolution;
        this.collateralAssetId = Felt.fromHex(collateralAssetIdHex);
        this.collateralResolution = collateralResolution;
    }

    public String getMarket() {
        return market;
    }

    public Felt getSyntheticAssetId() {
        return syntheticAssetId;
    }

    public long getSyntheticResolution() {
        return syntheticResolution;
    }

    public Felt getCollateralAssetId() {
        return collateralAssetId;
    }

    public long getCollateralResolution() {
        return collateralResolution;
    }
}
