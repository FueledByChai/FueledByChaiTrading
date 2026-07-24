package com.fueledbychai.extended.common.api;

import java.math.BigInteger;
import java.util.List;

import com.swmansion.starknet.crypto.Poseidon;
import com.swmansion.starknet.crypto.StarknetCurveSignature;
import com.swmansion.starknet.data.types.Felt;

/**
 * Computes the StarkEx perpetual order message hash used by Extended
 * (extended.exchange) and signs it with the Stark curve.
 *
 * <p>
 * This is a faithful Java port of Extended's {@code fast_stark_crypto}
 * {@code get_order_msg_hash} (see the Rust {@code rust-crypto-lib-base}
 * {@code Order::message_hash}). The order is hashed as a SNIP-12 (revision 1)
 * off-chain message using the Poseidon hash:
 * </p>
 *
 * <pre>
 * message_hash = Poseidon([ shortString("StarkNet Message"),
 *                           domainHash,
 *                           publicKey,
 *                           orderHash ])
 *
 * domainHash   = Poseidon([ DOMAIN_SELECTOR,
 *                           shortString(name), shortString(version),
 *                           shortString(chainId), revision ])
 *
 * orderHash    = Poseidon([ ORDER_SELECTOR,
 *                           positionId, baseAssetId, baseAmount(i64),
 *                           quoteAssetId, quoteAmount(i64), feeAssetId,
 *                           feeAmount(u64), expirationSeconds(u64), salt ])
 * </pre>
 *
 * <p>
 * The two type-hash selectors are {@code starknet_keccak} of the SNIP-12 type
 * strings and are taken verbatim from {@code rust-crypto-lib-base} (verified by
 * its unit tests), so they are hard-coded here rather than recomputed.
 * </p>
 */
public final class ExtendedStarkOrderSigner {

    /** {@code selector!("\"StarknetDomain\"(\"name\":\"shortstring\",...)")} */
    static final Felt DOMAIN_SELECTOR = Felt
            .fromHex("0x1ff2f602e42168014d405a94f75e8a93d640751d71d16311266e140d8b0a210");

    /** {@code selector!("\"Order\"(\"position_id\":\"felt\",...)")} */
    static final Felt ORDER_SELECTOR = Felt
            .fromHex("0x36da8d51815527cabfaa9c982f564c80fa7429616739306036f1f9b608dd112");

    /** {@code cairo_short_string_to_felt("StarkNet Message")} */
    static final Felt STARKNET_MESSAGE = Felt.fromShortString("StarkNet Message");

    private final BcStarknetCurveSigner bcSigner;
    private final Felt publicKey;
    private final Felt domainHash;

    /**
     * @param privateKeyHex Stark private key (0x...)
     * @param publicKeyHex  Stark public key (0x...) — the signer/user key
     * @param domain        SNIP-12 signing domain ({@link StarknetDomain#MAINNET}
     *                      or {@link StarknetDomain#TESTNET})
     */
    public ExtendedStarkOrderSigner(String privateKeyHex, String publicKeyHex, StarknetDomain domain) {
        this.bcSigner = new BcStarknetCurveSigner(Felt.fromHex(privateKeyHex));
        this.publicKey = Felt.fromHex(publicKeyHex);
        this.domainHash = computeDomainHash(domain);
    }

    public Felt getPublicKey() {
        return publicKey;
    }

    // ---------------------------------------------------------------------
    // Hashing (static so they can be unit-tested directly against vectors)
    // ---------------------------------------------------------------------

    public static Felt computeDomainHash(StarknetDomain domain) {
        return Poseidon.poseidonHash(List.of(DOMAIN_SELECTOR, Felt.fromShortString(domain.name()),
                Felt.fromShortString(domain.version()), Felt.fromShortString(domain.chainId()),
                new Felt(domain.revision())));
    }

    /**
     * Computes the {@code Order} struct hash. {@code baseAmount} and
     * {@code quoteAmount} are signed (i64) — one of them is negative depending on
     * order direction; {@code feeAmount} is unsigned (u64).
     */
    public static Felt computeOrderHash(long positionId, Felt baseAssetId, BigInteger baseAmount, Felt quoteAssetId,
            BigInteger quoteAmount, Felt feeAssetId, BigInteger feeAmount, long expirationSeconds, BigInteger salt) {
        return Poseidon.poseidonHash(List.of(ORDER_SELECTOR, new Felt(positionId), baseAssetId,
                Felt.fromSigned(baseAmount), quoteAssetId, Felt.fromSigned(quoteAmount), feeAssetId,
                Felt.fromSigned(feeAmount), new Felt(expirationSeconds), new Felt(salt)));
    }

    /** Wraps the order hash into the final SNIP-12 off-chain message hash. */
    public Felt computeMessageHash(Felt orderHash) {
        return Poseidon.poseidonHash(List.of(STARKNET_MESSAGE, domainHash, publicKey, orderHash));
    }

    public Felt computeMessageHash(long positionId, Felt baseAssetId, BigInteger baseAmount, Felt quoteAssetId,
            BigInteger quoteAmount, Felt feeAssetId, BigInteger feeAmount, long expirationSeconds, BigInteger salt) {
        Felt orderHash = computeOrderHash(positionId, baseAssetId, baseAmount, quoteAssetId, quoteAmount, feeAssetId,
                feeAmount, expirationSeconds, salt);
        return computeMessageHash(orderHash);
    }

    // ---------------------------------------------------------------------
    // Signing
    // ---------------------------------------------------------------------

    /** Signs a previously-computed SNIP-12 message hash. */
    public StarknetCurveSignature sign(Felt messageHash) {
        return bcSigner.sign(messageHash);
    }

    /**
     * Computes the order message hash and signs it, returning the {@code (r, s)}
     * Stark signature.
     */
    public StarknetCurveSignature signOrder(long positionId, Felt baseAssetId, BigInteger baseAmount, Felt quoteAssetId,
            BigInteger quoteAmount, Felt feeAssetId, BigInteger feeAmount, long expirationSeconds, BigInteger salt) {
        return sign(computeMessageHash(positionId, baseAssetId, baseAmount, quoteAssetId, quoteAmount, feeAssetId,
                feeAmount, expirationSeconds, salt));
    }
}
