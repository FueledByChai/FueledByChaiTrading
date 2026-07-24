package com.fueledbychai.extended.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.swmansion.starknet.crypto.StarknetCurveSignature;
import com.swmansion.starknet.data.types.Felt;

/**
 * Verifies the Java port of Extended's StarkEx perpetual order hashing/signing
 * against golden vectors taken from the authoritative sources:
 *
 * <ul>
 * <li>{@code x10xchange/rust-crypto-lib-base} unit tests (low-level domain /
 * order / message hashes), and</li>
 * <li>{@code x10xchange/python_sdk} signing tests (full end-to-end BTC-USD order
 * hash + Stark signature).</li>
 * </ul>
 *
 * If these pass, the order-signing pipeline is byte-for-byte compatible with
 * Extended.
 */
class ExtendedStarkOrderSignerTest {

    // ---- low-level selector constants (rust-crypto-lib-base) ----

    @Test
    void domainSelectorMatches() {
        assertEquals(new BigInteger("1ff2f602e42168014d405a94f75e8a93d640751d71d16311266e140d8b0a210", 16),
                ExtendedStarkOrderSigner.DOMAIN_SELECTOR.getValue());
    }

    @Test
    void orderSelectorMatches() {
        assertEquals(new BigInteger("36da8d51815527cabfaa9c982f564c80fa7429616739306036f1f9b608dd112", 16),
                ExtendedStarkOrderSigner.ORDER_SELECTOR.getValue());
    }

    // ---- domain hash (rust test_starknet_domain_hashing, SEPOLIA) ----

    @Test
    void domainHashSepolia() {
        Felt hash = ExtendedStarkOrderSigner.computeDomainHash(StarknetDomain.TESTNET);
        assertEquals(new BigInteger(
                "2788850828067604540663615870177667078542240404906059806659101905868929188327"),
                hash.getValue());
    }

    // ---- order struct hash (rust test_order_hashing: fields 1..9) ----

    @Test
    void orderStructHash() {
        Felt hash = ExtendedStarkOrderSigner.computeOrderHash(1L, new Felt(2), BigInteger.valueOf(3), new Felt(4),
                BigInteger.valueOf(5), new Felt(6), BigInteger.valueOf(7), 8L, BigInteger.valueOf(9));
        assertEquals(new BigInteger(
                "1329353150252109345267997901008558234696410103652961347079636617692652241760"),
                hash.getValue());
    }

    // ---- full message hash (rust test_message_hash_order, SEPOLIA) ----

    @Test
    void fullMessageHash() {
        // user_key from the rust test
        String publicKey = "0x"
                + new BigInteger("1528491859474308181214583355362479091084733880193869257167008343298409336538")
                        .toString(16);
        // private key is irrelevant for the hash; use a dummy non-zero key
        ExtendedStarkOrderSigner signer = new ExtendedStarkOrderSigner("0x1", publicKey, StarknetDomain.TESTNET);

        Felt orderHash = ExtendedStarkOrderSigner.computeOrderHash(1L, new Felt(2), BigInteger.valueOf(3), new Felt(4),
                BigInteger.valueOf(5), new Felt(6), BigInteger.valueOf(7), 8L, BigInteger.valueOf(9));
        Felt msgHash = signer.computeMessageHash(orderHash);

        assertEquals(new BigInteger(
                "2788960362996410178586013462192086205585543858281504820767681025777602529597"),
                msgHash.getValue());
    }

    // ---- negative-amount message hash (rust test_rs_get_order_msg) ----

    @Test
    void messageHashWithNegativeAmount() {
        // position=100, base=0x2 amt=100, quote=0x1 amt=-156, fee=0x1 amt=74,
        // expiration=100, salt=123, pubkey below, SEPOLIA domain
        String publicKey = "0x5d05989e9302dcebc74e241001e3e3ac3f4402ccf2f8e6f74b034b07ad6a904";
        ExtendedStarkOrderSigner signer = new ExtendedStarkOrderSigner("0x1", publicKey, StarknetDomain.TESTNET);
        Felt orderHash = ExtendedStarkOrderSigner.computeOrderHash(100L, new Felt(2), BigInteger.valueOf(100),
                new Felt(1), BigInteger.valueOf(-156), new Felt(1), BigInteger.valueOf(74), 100L,
                BigInteger.valueOf(123));
        Felt msgHash = signer.computeMessageHash(orderHash);
        assertEquals(Felt.fromHex("0x4de4c009e0d0c5a70a7da0e2039fb2b99f376d53496f89d9f437e736add6b48").getValue(),
                msgHash.getValue());
    }

    // ---- amount scaling (python_sdk test debuggingAmounts) ----

    @Test
    void amountScalingSell() {
        ExtendedStarkAmounts a = ExtendedStarkAmounts.compute(false, new BigDecimal("0.00100000"),
                new BigDecimal("43445.11680000"), new BigDecimal("0.0005"), 1_000_000L, 1_000_000L);
        assertEquals(BigInteger.valueOf(-1000), a.syntheticAmount());
        assertEquals(BigInteger.valueOf(43445116), a.collateralAmount());
        assertEquals(BigInteger.valueOf(21723), a.feeAmount());
    }

    @Test
    void amountScalingBuy() {
        ExtendedStarkAmounts a = ExtendedStarkAmounts.compute(true, new BigDecimal("0.00100000"),
                new BigDecimal("43445.11680000"), new BigDecimal("0.0005"), 1_000_000L, 1_000_000L);
        assertEquals(BigInteger.valueOf(1000), a.syntheticAmount());
        assertEquals(BigInteger.valueOf(-43445117), a.collateralAmount());
        assertEquals(BigInteger.valueOf(21723), a.feeAmount());
    }

    // ---- end-to-end: BTC-USD SELL order hash + signature (python_sdk) ----

    private static final Felt BTC_SYNTHETIC_ID = Felt.fromHex("0x4254432d3600000000000000000000");
    private static final Felt USD_COLLATERAL_ID = Felt
            .fromHex("0x31857064564ed0ff978e687456963cba09c2c6985d8f9300a1de4962fafa054");
    private static final String STARK_PRIVATE_KEY = "0x7a7ff6fd3cab02ccdcd4a572563f5976f8976899b03a39773795a3c486d4986";
    private static final String STARK_PUBLIC_KEY = "0x61c5e7e8339b7d56f197f54ea91b776776690e3232313de0f2ecbd0ef76f466";

    @Test
    void endToEndSellOrderHashAndSignature() {
        long positionId = 10002L;
        long salt = 1473459052L; // frozen nonce
        // expiration = (expiryEpochMillis / 1000) + 14 days
        long expiration = (1704420537000L / 1000L) + 14L * 86400L; // 1705630137

        ExtendedStarkAmounts amounts = ExtendedStarkAmounts.compute(false, new BigDecimal("0.00100000"),
                new BigDecimal("43445.11680000"), new BigDecimal("0.0005"), 1_000_000L, 1_000_000L);

        ExtendedStarkOrderSigner signer = new ExtendedStarkOrderSigner(STARK_PRIVATE_KEY, STARK_PUBLIC_KEY,
                StarknetDomain.TESTNET);

        // X10's get_order_msg_hash (and the JSON "id") is the full SNIP-12 message
        // hash, not the bare order struct hash.
        Felt messageHash = signer.computeMessageHash(positionId, BTC_SYNTHETIC_ID, amounts.syntheticAmount(),
                USD_COLLATERAL_ID, amounts.collateralAmount(), USD_COLLATERAL_ID, amounts.feeAmount(), expiration,
                BigInteger.valueOf(salt));

        assertEquals(new BigInteger(
                "529621978301228831750156704671293558063128025271079340676658105549022202327"),
                messageHash.getValue(), "order message hash (settlement id) mismatch");

        StarknetCurveSignature sig = signer.sign(messageHash);

        assertEquals(Felt.fromHex("0x3d17d8b9652e5f60d40d079653cfa92b1065ea8cf159609a3c390070dcd44f7").getValue(),
                sig.getR().getValue(), "signature r mismatch");
        assertEquals(Felt.fromHex("0x76a6deccbc84ac324f695cfbde80e0ed62443e95f5dcd8722d12650ccc122e5").getValue(),
                sig.getS().getValue(), "signature s mismatch");
    }
}
