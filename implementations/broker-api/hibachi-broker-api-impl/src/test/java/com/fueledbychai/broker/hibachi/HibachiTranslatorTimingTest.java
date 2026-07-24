package com.fueledbychai.broker.hibachi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.hibachi.common.api.signer.IHibachiSigner;
import com.fueledbychai.hibachi.common.api.signer.SignatureScheme;

class HibachiTranslatorTimingTest {

    @Test
    void cancelTranslationCapturesPayloadAndSigningBoundaries() {
        HibachiTranslator translator = new HibachiTranslator();
        OrderTicket order = new OrderTicket();
        order.setOrderId("123456");
        byte[][] signedPayload = new byte[1][];
        IHibachiSigner signer = new IHibachiSigner() {
            @Override
            public String sign(byte[] packedPayload) {
                signedPayload[0] = packedPayload;
                return "test-signature";
            }

            @Override
            public SignatureScheme scheme() {
                return SignatureScheme.ECDSA;
            }
        };

        HibachiTranslator.SignedRequest request = translator.translateCancel(
                order, 99L, 1_234_567L, signer);

        assertTrue(request.packStartedNs > 0L);
        assertTrue(request.packCompletedNs >= request.packStartedNs);
        assertTrue(request.signCompletedNs >= request.packCompletedNs);
        assertEquals("test-signature", request.signature);
        assertArrayEquals(request.signedBytes, signedPayload[0]);
    }
}
