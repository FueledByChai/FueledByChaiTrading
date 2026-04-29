package com.fueledbychai.hibachi.common.api.signer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.MessageDigest;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;

class EcdsaHibachiSignerTest {

    private static final String PRIVATE_KEY_HEX =
            "0x4646464646464646464646464646464646464646464646464646464646464646";

    @Test
    void emits65BytesAsLowercaseHex() {
        EcdsaHibachiSigner signer = new EcdsaHibachiSigner(PRIVATE_KEY_HEX);
        byte[] payload = new byte[] { 0x01, 0x02, 0x03, 0x04 };

        String hex = signer.sign(payload);

        assertEquals(130, hex.length(), "65 bytes => 130 hex chars");
        assertEquals(hex, hex.toLowerCase(), "must be lowercase hex");
        int v = Integer.parseInt(hex.substring(128, 130), 16);
        assertTrue(v == 0 || v == 1, "recovery byte must be 0 or 1; got " + v);
    }

    @Test
    void signatureRecoversToOriginalPublicKey() throws Exception {
        EcdsaHibachiSigner signer = new EcdsaHibachiSigner(PRIVATE_KEY_HEX);
        byte[] payload = new byte[] { 0x10, 0x20, 0x30, 0x40, 0x50 };

        String hex = signer.sign(payload);
        byte[] sigBytes = hexToBytes(hex);
        BigInteger r = new BigInteger(1, java.util.Arrays.copyOfRange(sigBytes, 0, 32));
        BigInteger s = new BigInteger(1, java.util.Arrays.copyOfRange(sigBytes, 32, 64));
        int recId = sigBytes[64] & 0xFF;

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
        BigInteger recovered = Sign.recoverFromSignature(recId, new ECDSASignature(r, s), digest);
        ECKeyPair expected = ECKeyPair.create(new BigInteger(PRIVATE_KEY_HEX.substring(2), 16));
        assertEquals(expected.getPublicKey(), recovered);
    }

    @Test
    void scheme_isEcdsa() {
        assertEquals(SignatureScheme.ECDSA, new EcdsaHibachiSigner(PRIVATE_KEY_HEX).scheme());
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
