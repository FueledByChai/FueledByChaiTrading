package com.fueledbychai.paradex.common.api;

import java.math.BigInteger;
import java.util.Arrays;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import com.swmansion.starknet.crypto.StarknetCurveSignature;
import com.swmansion.starknet.data.types.Felt;

/**
 * BouncyCastle-based Stark curve signing primitive (hash -> (r,s)). Ported from
 * Paradex Groovy implementation.
 */
public final class BcStarknetCurveSigner {

    private static final BigInteger ALPHA = BigInteger.ONE; // A
    private static final BigInteger BETA = new BigInteger(
            "3141592653589793238462643383279502884197169399375105820974944592307816406665"); // B
    private static final BigInteger PRIME = new BigInteger(
            "3618502788666131213697322783095070105623107215331596699973092056135872020481"); // P
    private static final BigInteger EC_ORDER = new BigInteger(
            "3618502788666131213697322783095070105526743751716087489154079457884512865583"); // N
    private static final BigInteger GX = new BigInteger(
            "874739451078007766457464989774322083649278607533249481151382481072868806602");
    private static final BigInteger GY = new BigInteger(
            "152666792071518830868575557812948353041420400780739481342941381225525861407");

    private static final ECCurve CURVE;
    private static final ECPoint G;
    private static final ECDomainParameters DOMAIN;

    static {
        ECCurve.Fp dummy = new ECCurve.Fp(PRIME, ALPHA, BETA, EC_ORDER, BigInteger.ONE);
        ECCurve curve = dummy.configure().setCoordinateSystem(ECCurve.COORD_AFFINE).create();
        CURVE = curve;
        G = curve.createPoint(GX, GY);
        DOMAIN = new ECDomainParameters(CURVE, G, EC_ORDER);
    }

    private final ECPrivateKeyParameters privateKeyParams;

    public BcStarknetCurveSigner(Felt privateKeyFelt) {
        BigInteger privateKey = privateKeyFelt.getValue();
        this.privateKeyParams = new ECPrivateKeyParameters(privateKey, DOMAIN);
    }

    public StarknetCurveSignature sign(Felt messageHashFelt) {
        BigInteger messageHash = messageHashFelt.getValue();
        byte[] msg = toUnsignedByteArray(fixMessageLength(messageHash));

        HMacDSAKCalculator kCalc = new HMacDSAKCalculator(new SHA256Digest());
        ECDSASigner signer = new ECDSASigner(kCalc);

        signer.init(true, privateKeyParams);
        BigInteger[] sig = signer.generateSignature(msg);

        return new StarknetCurveSignature(new Felt(sig[0]), new Felt(sig[1]));
    }

    private static BigInteger fixMessageLength(BigInteger message) {
        String hex = message.toString(16);
        if (hex.length() <= 62) {
            return message;
        }
        if (hex.length() != 63) {
            throw new IllegalArgumentException("InvalidHashLength: " + hex.length());
        }
        return message.shiftLeft(4);
    }

    private static byte[] toUnsignedByteArray(BigInteger value) {
        byte[] signed = value.toByteArray();
        if (signed.length > 0 && signed[0] == 0x00) {
            return Arrays.copyOfRange(signed, 1, signed.length);
        }
        return signed;
    }
}
