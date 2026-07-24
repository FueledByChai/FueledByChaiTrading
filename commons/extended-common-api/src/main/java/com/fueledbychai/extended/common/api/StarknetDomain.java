package com.fueledbychai.extended.common.api;

/**
 * SNIP-12 Starknet signing domain used by Extended (Perpetuals appchain).
 *
 * <p>
 * Mainnet: {@code name="Perpetuals", version="v0", chainId="SN_MAIN", revision=1}.<br>
 * Testnet: {@code name="Perpetuals", version="v0", chainId="SN_SEPOLIA", revision=1}.
 * </p>
 */
public record StarknetDomain(String name, String version, String chainId, int revision) {

    public static final StarknetDomain MAINNET = new StarknetDomain("Perpetuals", "v0", "SN_MAIN", 1);
    public static final StarknetDomain TESTNET = new StarknetDomain("Perpetuals", "v0", "SN_SEPOLIA", 1);
}
