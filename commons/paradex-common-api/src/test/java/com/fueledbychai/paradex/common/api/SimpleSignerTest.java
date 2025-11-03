package com.fueledbychai.paradex.common.api;

import java.math.BigInteger;

/**
 * Simple test to verify the signer works.
 */
public class SimpleSignerTest {

    public static void main(String[] args) {
        try {
            System.out.println("Starting signer test...");

            // Test data - using dummy but valid format values
            String testAccountAddress = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
            String testPrivateKey = "0x123456789abcdef123456789abcdef123456789abcdef123456789abcdef1234";
            BigInteger testChainId = new BigInteger("12345");

            ParadexMessageSigner signer = new ParadexMessageSigner(testAccountAddress, testPrivateKey, testChainId);

            System.out.println("Signer created successfully");
            System.out.println("Is initialized: " + signer.isInitialized());
            System.out.println("Account address: " + signer.getAccountAddress());
            System.out.println("Chain ID: " + signer.getChainID());

            // Try to get public key
            String publicKey = signer.getPublicKeyHex();
            System.out.println("Public key: " + (publicKey != null ? publicKey.substring(0, 20) + "..." : "null"));

            System.out.println("Basic signer test passed!");

        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}