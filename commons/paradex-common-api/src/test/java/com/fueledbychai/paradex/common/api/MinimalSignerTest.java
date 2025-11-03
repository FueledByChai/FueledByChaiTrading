package com.fueledbychai.paradex.common.api;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal test to check basic signer functionality.
 */
public class MinimalSignerTest {

    @Test
    public void testBasicConstruction() {
        // Use more realistic test values
        String testAccountAddress = "0x049d36570d4e46f48e99674bd3fcc84644ddd6b96f7c741b1562b82f9e004dc7";
        String testPrivateKey = "0x0000000000000000000000000000000000000000000000000000000000000001";
        BigInteger testChainId = new BigInteger("393402133025997798000961");

        try {
            ParadexMessageSigner signer = new ParadexMessageSigner(testAccountAddress, testPrivateKey, testChainId);

            // Basic assertions
            assertNotNull(signer);
            assertTrue(signer.isInitialized());
            assertEquals(testAccountAddress, signer.getAccountAddress());
            assertEquals(testChainId, signer.getChainID());

        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            fail("Constructor should not throw exception: " + e.getMessage());
        }
    }
}