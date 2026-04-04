package com.fueledbychai.aster.common.api;

import java.util.concurrent.atomic.AtomicLong;

import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

/**
 * Signs Aster v3 API requests using EIP-712 typed data.
 * <p>
 * The signing scheme matches Aster's Python reference implementation:
 * the URL-encoded parameter string is placed in an EIP-712 {@code Message.msg}
 * field, hashed with domain separator {@code AsterSignTransaction}, and signed
 * with the API wallet private key.
 */
public final class AsterEip712Signer {

    private static final String TYPED_DATA_TEMPLATE = """
            {
              "types": {
                "EIP712Domain": [
                  {"name": "name", "type": "string"},
                  {"name": "version", "type": "string"},
                  {"name": "chainId", "type": "uint256"},
                  {"name": "verifyingContract", "type": "address"}
                ],
                "Message": [
                  {"name": "msg", "type": "string"}
                ]
              },
              "primaryType": "Message",
              "domain": {
                "name": "AsterSignTransaction",
                "version": "1",
                "chainId": 1666,
                "verifyingContract": "0x0000000000000000000000000000000000000000"
              },
              "message": {
                "msg": "$MSG$"
              }
            }
            """;

    private final ECKeyPair keyPair;
    private final AtomicLong lastSecond = new AtomicLong(0);
    private final AtomicLong counter = new AtomicLong(0);

    public AsterEip712Signer(String privateKeyHex) {
        this.keyPair = ECKeyPair.create(Numeric.toBigInt(privateKeyHex));
    }

    /**
     * Signs the given URL-encoded parameter string and returns the hex signature.
     */
    public String sign(String encodedParams) {
        try {
            String json = TYPED_DATA_TEMPLATE.replace("$MSG$", escapeJsonValue(encodedParams));
            StructuredDataEncoder encoder = new StructuredDataEncoder(json);
            byte[] digest = encoder.hashStructuredData();
            Sign.SignatureData sd = Sign.signMessage(digest, keyPair, false);
            return toHex(sd);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign Aster request", e);
        }
    }

    /**
     * Returns a unique nonce in microseconds, matching Aster's Python reference.
     * Thread-safe: uses atomic counter to guarantee uniqueness even when called
     * concurrently within the same second.
     */
    public long getNonce() {
        long nowSec = System.currentTimeMillis() / 1000;
        long prev = lastSecond.get();
        if (nowSec != prev && lastSecond.compareAndSet(prev, nowSec)) {
            counter.set(0);
        }
        return nowSec * 1_000_000L + counter.getAndIncrement();
    }

    private static String toHex(Sign.SignatureData sd) {
        byte[] r = sd.getR();
        byte[] s = sd.getS();
        byte[] v = sd.getV();
        byte[] sig = new byte[r.length + s.length + v.length];
        System.arraycopy(r, 0, sig, 0, r.length);
        System.arraycopy(s, 0, sig, r.length, s.length);
        System.arraycopy(v, 0, sig, r.length + s.length, v.length);
        return Numeric.toHexStringNoPrefix(sig);
    }

    private static String escapeJsonValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
