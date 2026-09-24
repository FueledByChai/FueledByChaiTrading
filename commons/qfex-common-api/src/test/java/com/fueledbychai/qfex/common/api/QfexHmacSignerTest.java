package com.fueledbychai.qfex.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class QfexHmacSignerTest {

    @Test
    void signsNonceColonTimestamp() {
        // python: hmac.new(b'qfex_secret_test', b'c0ffee:1760545414', hashlib.sha256).hexdigest()
        QfexHmacSigner.Credentials c = new QfexHmacSigner("qfex_pub_test", "qfex_secret_test")
                .sign("c0ffee", 1760545414L);
        assertEquals("9a892254ce0416371a78a4352130b7825a2f5fb442b12ddfa7c2f5202ef045dc", c.signature());
        assertEquals("qfex_pub_test", c.publicKey());
    }

    @Test
    void noncesAreFresh() {
        assertNotEquals(QfexHmacSigner.newNonce(), QfexHmacSigner.newNonce());
    }
}
