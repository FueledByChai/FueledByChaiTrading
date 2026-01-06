package com.fueledbychai.paradex.common.api;

import java.math.BigInteger;

import com.swmansion.starknet.data.types.Felt;

/**
 * Simple performance test to compare original vs optimized signing methods.
 */
public class SigningPerformanceTestFast {

    // Test credentials (dummy values for testing)
    private static final String TEST_ACCOUNT_ADDRESS = "0x049d36570d4e46f48e99674bd3fcc84644ddd6b96f7c741b1562b82f9e004dc7";
    private static final String TEST_PRIVATE_KEY = "0x0000000000000000000000000000000000000000000000000000000000000001";
    private static final BigInteger TEST_CHAIN_ID = new BigInteger("12345");

    public static void main(String[] args) {
        try {
            System.out.println("=== Paradex Message Signing Fast Performance Test ===");

            // Create signer
            ParadexTypedDataSigner signer = new ParadexTypedDataSigner(TEST_ACCOUNT_ADDRESS, TEST_PRIVATE_KEY,
                    "0x" + TEST_CHAIN_ID.toString(16).toUpperCase());

            // Warm up
            System.out.println("Warming up...");
            for (int i = 0; i < 100; i++) {
                performDirectSigningTest(signer, false);
            }

            System.out.println("\n=== Performance Comparison ===");

            // Test original JSON-based signing
            System.out.println("Testing original JSON-based signing method...");
            long originalTime = 0;// performSigningTest(signer, true);

            // Test optimized direct signing
            System.out.println("Testing optimized direct signing method...");
            long optimizedTime = performDirectSigningTest(signer, true);

            System.out.println("\n=== Results ===");
            System.out.println("Original JSON-based signing: " + originalTime + "ms");
            System.out.println("Optimized direct signing: " + optimizedTime + "ms");
            System.out.println("Performance improvement: " + (originalTime - optimizedTime) + "ms ("
                    + String.format("%.1f", ((double) (originalTime - optimizedTime) / originalTime) * 100)
                    + "% faster)");

        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static long performDirectSigningTest(ParadexTypedDataSigner signer, boolean printTime) {
        long timestamp = System.currentTimeMillis();
        String market = "BTC-USD-PERP";
        String side = "1"; // BUY
        String orderType = "LIMIT";
        String size = "150000000"; // 1.5 BTC scaled
        String price = "5000000000000"; // $50000 scaled

        long startTime = System.nanoTime();
        String signature = signer.signOrderAsParadexArray(timestamp, market, side, orderType, size, price);
        long endTime = System.nanoTime();

        long timeMs = (endTime - startTime) / 1_000_000;

        if (printTime) {
            System.out.println("  Time: " + timeMs + "ms");
            System.out.println("  Signature length: " + signature.length());
        }

        return timeMs;
    }

    private static String createTestOrderMessage() {
        long timestamp = System.currentTimeMillis();
        String chainIdHex = "0x" + TEST_CHAIN_ID.toString(16).toUpperCase();

        return String.format("""
                {
                    "domain": {"name": "Paradex", "chainId": "%s", "version": "1"},
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
                        "timestamp": %d,
                        "market": "BTC-USD-PERP",
                        "side": "1",
                        "orderType": "LIMIT",
                        "size": "150000000",
                        "price": "5000000000000"
                    }
                }
                """, chainIdHex, timestamp);
    }

}