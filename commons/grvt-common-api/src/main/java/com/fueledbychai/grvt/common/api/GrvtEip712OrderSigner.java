package com.fueledbychai.grvt.common.api;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import com.fueledbychai.grvt.common.api.model.GrvtInstrument;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;
import com.fueledbychai.grvt.common.api.model.GrvtOrderLeg;
import com.fueledbychai.grvt.common.api.model.GrvtSignature;

/**
 * Signs GRVT orders with EIP-712 typed-data (Ethereum secp256k1).
 * <p>
 * GRVT's order struct is signed as the typed data below (see {@code grvt_raw_signing.py} in the
 * official Python SDK):
 *
 * <pre>
 * Order(uint64 subAccountID, bool isMarket, uint8 timeInForce, bool postOnly, bool reduceOnly,
 *       OrderLeg[] legs, uint32 nonce, int64 expiration)
 * OrderLeg(uint256 assetID, uint64 contractSize, uint64 limitPrice, bool isBuyingContract)
 * domain = EIP712Domain(string name="GRVT Exchange", string version="0", uint256 chainId)
 * </pre>
 *
 * The EIP-712 struct hashing is implemented manually with Keccak-256 rather than web3j's
 * {@code StructuredDataEncoder} because the order contains an array of structs ({@code OrderLeg[]}),
 * which that encoder does not reliably support. Correctness is pinned by golden test vectors ported
 * from the GRVT SDK ({@code GrvtEip712OrderSignerTest}).
 */
public final class GrvtEip712OrderSigner {

    private static final BigInteger PRICE_MULTIPLIER = BigInteger.TEN.pow(9);

    private static final byte[] DOMAIN_TYPEHASH =
            keccak("EIP712Domain(string name,string version,uint256 chainId)");
    private static final byte[] ORDER_TYPEHASH = keccak(
            "Order(uint64 subAccountID,bool isMarket,uint8 timeInForce,bool postOnly,bool reduceOnly,"
                    + "OrderLeg[] legs,uint32 nonce,int64 expiration)"
                    + "OrderLeg(uint256 assetID,uint64 contractSize,uint64 limitPrice,bool isBuyingContract)");
    private static final byte[] ORDERLEG_TYPEHASH =
            keccak("OrderLeg(uint256 assetID,uint64 contractSize,uint64 limitPrice,bool isBuyingContract)");
    private static final byte[] DOMAIN_NAME_HASH = keccak("GRVT Exchange");
    private static final byte[] DOMAIN_VERSION_HASH = keccak("0");

    private final ECKeyPair keyPair;
    private final String signerAddress;

    public GrvtEip712OrderSigner(String privateKeyHex) {
        this.keyPair = ECKeyPair.create(Numeric.toBigInt(privateKeyHex));
        this.signerAddress = Keys.toChecksumAddress(Keys.getAddress(this.keyPair));
    }

    public String getSignerAddress() {
        return signerAddress;
    }

    /**
     * Signs the given order in place: computes the EIP-712 digest, signs it, and populates the
     * order's {@link GrvtSignature} (r, s, v, signer). The order's existing nonce and expiration are
     * used as-is. Returns the same order for convenience.
     *
     * @param order       the order to sign (nonce + expiration must already be set)
     * @param instruments lookup from instrument name to its descriptor (for assetID + baseDecimals)
     * @param chainId     the EIP-712 chain id for the target environment (prod 325, testnet 326)
     */
    public GrvtOrder signOrder(GrvtOrder order, Map<String, GrvtInstrument> instruments, int chainId) {
        byte[] digest = computeDigest(order, instruments, chainId);
        Sign.SignatureData sd = Sign.signMessage(digest, keyPair, false);

        GrvtSignature signature = order.getSignature();
        signature.setR(Numeric.toHexString(sd.getR()));
        signature.setS(Numeric.toHexString(sd.getS()));
        signature.setV(sd.getV()[0] & 0xff);
        signature.setSigner(signerAddress);
        return order;
    }

    /** Computes the 32-byte EIP-712 digest (keccak256(0x1901 || domainSeparator || hashStruct(order))). */
    byte[] computeDigest(GrvtOrder order, Map<String, GrvtInstrument> instruments, int chainId) {
        byte[] domainSeparator = domainSeparator(chainId);
        byte[] orderHash = hashOrder(order, instruments);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((byte) 0x19);
        out.write((byte) 0x01);
        write(out, domainSeparator);
        write(out, orderHash);
        return Hash.sha3(out.toByteArray());
    }

    private static byte[] domainSeparator(int chainId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, DOMAIN_TYPEHASH);
        write(out, DOMAIN_NAME_HASH);
        write(out, DOMAIN_VERSION_HASH);
        write(out, uint256(BigInteger.valueOf(chainId)));
        return Hash.sha3(out.toByteArray());
    }

    private static byte[] hashOrder(GrvtOrder order, Map<String, GrvtInstrument> instruments) {
        ByteArrayOutputStream legsConcat = new ByteArrayOutputStream();
        for (GrvtOrderLeg leg : order.getLegs()) {
            write(legsConcat, hashLeg(leg, instruments));
        }
        byte[] legsHash = Hash.sha3(legsConcat.toByteArray());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, ORDER_TYPEHASH);
        write(out, uint256(new BigInteger(order.getSubAccountId())));
        write(out, bool(order.isMarket()));
        write(out, uint256(BigInteger.valueOf(order.getTimeInForce().getSignValue())));
        write(out, bool(order.isPostOnly()));
        write(out, bool(order.isReduceOnly()));
        write(out, legsHash);
        write(out, uint256(BigInteger.valueOf(order.getSignature().getNonce())));
        write(out, int256(new BigInteger(order.getSignature().getExpiration())));
        return Hash.sha3(out.toByteArray());
    }

    private static byte[] hashLeg(GrvtOrderLeg leg, Map<String, GrvtInstrument> instruments) {
        GrvtInstrument instrument = instruments.get(leg.getInstrument());
        if (instrument == null) {
            throw new IllegalArgumentException("Unknown instrument for signing: " + leg.getInstrument());
        }
        BigInteger assetId = Numeric.toBigInt(instrument.getInstrumentHash());
        BigInteger sizeMultiplier = BigInteger.TEN.pow(instrument.getBaseDecimals());
        BigInteger contractSize = leg.getSize().multiply(new BigDecimal(sizeMultiplier)).toBigInteger();
        BigInteger limitPrice = leg.getLimitPrice().multiply(new BigDecimal(PRICE_MULTIPLIER)).toBigInteger();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, ORDERLEG_TYPEHASH);
        write(out, uint256(assetId));
        write(out, uint256(contractSize));
        write(out, uint256(limitPrice));
        write(out, bool(leg.isBuyingAsset()));
        return Hash.sha3(out.toByteArray());
    }

    private static byte[] uint256(BigInteger value) {
        return Numeric.toBytesPadded(value, 32);
    }

    /** Two's-complement 32-byte encoding for signed integers (handles negative values). */
    private static byte[] int256(BigInteger value) {
        if (value.signum() >= 0) {
            return Numeric.toBytesPadded(value, 32);
        }
        byte[] result = new byte[32];
        byte[] bytes = value.toByteArray();
        java.util.Arrays.fill(result, (byte) 0xff);
        System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
        return result;
    }

    private static byte[] bool(boolean value) {
        byte[] result = new byte[32];
        result[31] = (byte) (value ? 1 : 0);
        return result;
    }

    private static byte[] keccak(String value) {
        return Hash.sha3(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }
}
