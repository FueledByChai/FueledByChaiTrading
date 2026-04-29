package com.fueledbychai.hibachi.common.api.signer;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;

/**
 * ECDSA secp256k1 Hibachi signer for trustless accounts.
 *
 * <p>Signs {@code SHA-256(packedPayload)} with the account private key and emits the
 * 65-byte signature as 130 lowercase-hex chars: {@code r (32B) || s (32B) || v (1B)}.
 * The recovery byte {@code v} is 0 or 1 (matching the JS reference SDK's
 * {@code recoveryParam}, not the 27/28 Ethereum convention).
 */
public class EcdsaHibachiSigner implements IHibachiSigner {

    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();

    private final ECKeyPair keyPair;

    public EcdsaHibachiSigner(String privateKeyHex) {
        if (privateKeyHex == null || privateKeyHex.isBlank()) {
            throw new IllegalArgumentException("privateKey is required");
        }
        String hex = privateKeyHex.trim();
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        this.keyPair = ECKeyPair.create(new BigInteger(hex, 16));
    }

    @Override
    public String sign(byte[] packedPayload) {
        if (packedPayload == null) {
            throw new IllegalArgumentException("packedPayload is required");
        }
        byte[] digest = sha256(packedPayload);
        ECDSASignature sig = keyPair.sign(digest);
        int recId = recoveryId(sig, digest, keyPair.getPublicKey());
        if (recId < 0) {
            throw new IllegalStateException("Could not derive ECDSA recovery id");
        }

        byte[] out = new byte[65];
        copyTo32(sig.r, out, 0);
        copyTo32(sig.s, out, 32);
        out[64] = (byte) recId;
        return toHexLower(out);
    }

    @Override
    public SignatureScheme scheme() {
        return SignatureScheme.ECDSA;
    }

    private static int recoveryId(ECDSASignature sig, byte[] digest, BigInteger publicKey) {
        for (int i = 0; i < 4; i++) {
            BigInteger recovered = Sign.recoverFromSignature(i, sig, digest);
            if (recovered != null && recovered.equals(publicKey)) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void copyTo32(BigInteger value, byte[] dest, int offset) {
        byte[] raw = value.toByteArray();
        if (raw.length == 32) {
            System.arraycopy(raw, 0, dest, offset, 32);
            return;
        }
        if (raw.length == 33 && raw[0] == 0) {
            System.arraycopy(raw, 1, dest, offset, 32);
            return;
        }
        if (raw.length < 32) {
            System.arraycopy(raw, 0, dest, offset + (32 - raw.length), raw.length);
            return;
        }
        throw new IllegalStateException("Unexpected ECDSA component length: " + raw.length);
    }

    private static String toHexLower(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX_LOWER[v >>> 4];
            out[i * 2 + 1] = HEX_LOWER[v & 0x0F];
        }
        return new String(out);
    }
}
