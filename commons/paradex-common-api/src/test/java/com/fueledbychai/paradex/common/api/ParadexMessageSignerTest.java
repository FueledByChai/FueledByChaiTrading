package com.fueledbychai.paradex.common.api;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ParadexMessageSigner to verify the optimized signing
 * functionality.
 */
public class ParadexMessageSignerTest {

    // Test data - using valid StarkNet values for unit testing
    private static final String TEST_ACCOUNT_ADDRESS = "0x049d36570d4e46f48e99674bd3fcc84644ddd6b96f7c741b1562b82f9e004dc7";
    private static final String TEST_PRIVATE_KEY = "0x0000000000000000000000000000000000000000000000000000000000000001";
    private static final BigInteger TEST_CHAIN_ID = new BigInteger("12345");

    private static final String TEST_MESSAGE = """
            {
                "domain": {"name": "Paradex", "chainId": "0x3039", "version": "1"},
                "primaryType": "Order",
                "types": {
                    "StarkNetDomain": [
                        {"name": "name", "type": "felt"},
                        {"name": "chainId", "type": "felt"},
                        {"name": "version", "type": "felt"}
                    ],
                    "Order": [
                        {"name": "timestamp", "type": "felt"},
                        {"name": "market", "type": "felt"},
                        {"name": "side", "type": "felt"},
                        {"name": "orderType", "type": "felt"},
                        {"name": "size", "type": "felt"},
                        {"name": "price", "type": "felt"}
                    ]
                },
                "message": {
                    "timestamp": 1699000000,
                    "market": "BTC-USD-PERP",
                    "side": "1",
                    "orderType": "LIMIT",
                    "size": "150000000",
                    "price": "5000000000000"
                }
            }
            """;

    @Test
    public void testSignerInitialization() {
        ParadexMessageSigner signer = new ParadexMessageSigner(TEST_ACCOUNT_ADDRESS, TEST_PRIVATE_KEY, TEST_CHAIN_ID);

        assertTrue(signer.isInitialized());
        assertEquals(TEST_ACCOUNT_ADDRESS, signer.getAccountAddress());
        assertEquals(TEST_CHAIN_ID, signer.getChainID());
        assertNotNull(signer.getPublicKeyHex());
    }

    @Test
    public void testSigningPerformance() {
        ParadexMessageSigner signer = new ParadexMessageSigner(TEST_ACCOUNT_ADDRESS, TEST_PRIVATE_KEY, TEST_CHAIN_ID);

        // Warm up the signer
        signer.getPublicKeyHex();

        // Measure signing time (should be much faster than original 150-300ms)
        long startTime = System.nanoTime();
        String signature1 = signer.signMessage(TEST_MESSAGE);
        long firstSignTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds

        startTime = System.nanoTime();
        String signature2 = signer.signMessage(TEST_MESSAGE);
        long secondSignTime = (System.nanoTime() - startTime) / 1_000_000;

        // Verify signatures are consistent
        assertEquals(signature1, signature2);
        assertNotNull(signature1);
        assertTrue(signature1.startsWith("[\""));
        assertTrue(signature1.endsWith("\"]"));

        System.out.println("First sign time: " + firstSignTime + "ms");
        System.out.println("Second sign time: " + secondSignTime + "ms");

        // Second signing should be faster due to caching (though both should be fast)
        assertTrue(secondSignTime <= firstSignTime + 5); // Allow 5ms tolerance
    }

    @Test
    public void testCacheClear() {
        ParadexMessageSigner signer = new ParadexMessageSigner(TEST_ACCOUNT_ADDRESS, TEST_PRIVATE_KEY, TEST_CHAIN_ID);

        String publicKey1 = signer.getPublicKeyHex();
        assertNotNull(publicKey1);

        signer.clearCache();

        // Should still work after cache clear
        String publicKey2 = signer.getPublicKeyHex();
        assertEquals(publicKey1, publicKey2); // Same key should be generated
    }

    @Test
    public void testDirectSigningMethod() {
        ParadexMessageSigner signer = new ParadexMessageSigner(TEST_ACCOUNT_ADDRESS, TEST_PRIVATE_KEY, TEST_CHAIN_ID);

        // Test direct signing method
        long timestamp = 1699000000;
        String market = "BTC-USD-PERP";
        String side = "1";
        String orderType = "LIMIT";
        String size = "150000000";
        String price = "5000000000000";

        String directSignature = signer.signOrderMessageDirect(timestamp, market, side, orderType, size, price);

        assertNotNull(directSignature);
        assertTrue(directSignature.startsWith("[\""));
        assertTrue(directSignature.endsWith("\"]"));

        // The direct signing should produce the same result as the JSON-based signing
        String jsonSignature = signer.signMessage(TEST_MESSAGE);
        assertEquals(jsonSignature, directSignature);
    }

    @Test
    public void testPreConstructedMessageSigning() {
        ParadexMessageSigner signer = new ParadexMessageSigner(TEST_ACCOUNT_ADDRESS, TEST_PRIVATE_KEY, TEST_CHAIN_ID);

        // Test the new pre-constructed message signing method
        String signature1 = signer.signPreConstructedMessage(TEST_MESSAGE);
        String signature2 = signer.signMessage(TEST_MESSAGE);

        assertNotNull(signature1);
        assertEquals(signature1, signature2); // Should produce same result
    }

    @Test
    public void testInvalidCredentials() {
        // Test with null credentials
        assertThrows(Exception.class, () -> {
            ParadexMessageSigner signer = new ParadexMessageSigner(null, null, TEST_CHAIN_ID);
            signer.signMessage(TEST_MESSAGE);
        });
    }
}