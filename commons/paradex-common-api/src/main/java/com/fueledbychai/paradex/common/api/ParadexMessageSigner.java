package com.fueledbychai.paradex.common.api;

import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.swmansion.starknet.crypto.StarknetCurve;
import com.swmansion.starknet.crypto.StarknetCurveSignature;
import com.swmansion.starknet.data.TypedData;
import com.swmansion.starknet.data.types.Felt;
import com.swmansion.starknet.signer.StarkCurveSigner;

/**
 * Optimized message signer for Paradex operations. Caches cryptographic objects
 * to improve signing performance from 150-300ms to much faster.
 * 
 * Key optimizations: - Caches Felt objects and StarkCurveSigner to avoid
 * recreation - Pre-computes public key hex string - Uses optimized
 * StringBuilder for signature conversion - Thread-safe initialization with
 * double-check locking
 */
public class ParadexMessageSigner {
    private static final Logger logger = LoggerFactory.getLogger(ParadexMessageSigner.class);

    private final String accountAddressString;
    private final String privateKeyString;
    private final BigInteger chainID;

    // Cached objects to avoid recreation on every signature request
    private volatile Felt cachedAccountAddress = null;
    private volatile Felt cachedPrivateKey = null;
    private volatile StarkCurveSigner cachedSigner = null;
    private volatile String starkPublicKeyHex = null;

    // Cache for frequently used values
    private volatile String cachedChainIdHex = null;

    // Reusable StringBuilder for signature conversion
    private final ThreadLocal<StringBuilder> stringBuilderLocal = ThreadLocal.withInitial(() -> new StringBuilder(256));

    /**
     * Creates a new message signer for the given account and chain.
     * 
     * @param accountAddressString The Starknet account address in hex format
     * @param privateKeyString     The private key in hex format
     * @param chainID              The chain ID for the network
     */
    public ParadexMessageSigner(String accountAddressString, String privateKeyString, BigInteger chainID) {
        this.accountAddressString = accountAddressString;
        this.privateKeyString = privateKeyString;
        this.chainID = chainID;

        // Pre-initialize cache to avoid first-call latency
        try {
            initializeSigningCache();
        } catch (Exception e) {
            logger.warn("Failed to pre-initialize signing cache, will initialize on first use: " + e.getMessage());
        }
    }

    /**
     * Signs a message using the cached cryptographic objects. This is the optimized
     * version that should perform much faster than the original.
     * 
     * @param orderMessage The JSON message to sign
     * @return The signature in string format
     */
    public String signMessage(String orderMessage) {
        long totalStart = System.nanoTime();

        // Ensure cache is initialized
        if (cachedSigner == null) {
            initializeSigningCache();
            logger.debug("Using account: {} (Chain ID: {})", accountAddressString, chainID);
        }

        // Convert the message to typed data (unavoidable but could be cached if message
        // patterns are similar)
        long jsonStart = System.nanoTime();
        TypedData typedData = TypedData.fromJsonString(orderMessage);
        long jsonTime = (System.nanoTime() - jsonStart) / 1_000_000;

        // Sign using cached objects
        long signStart = System.nanoTime();
        List<Felt> signature = cachedSigner.signTypedData(typedData, cachedAccountAddress);
        long signTime = (System.nanoTime() - signStart) / 1_000_000;

        // Convert signature to string using optimized method
        long convertStart = System.nanoTime();
        String result = convertSignatureToString(signature);
        long convertTime = (System.nanoTime() - convertStart) / 1_000_000;

        long totalTime = (System.nanoTime() - totalStart) / 1_000_000;

        logger.debug("Signing breakdown - Total: {}ms, JSON Parse: {}ms, Crypto Sign: {}ms, String Convert: {}ms",
                totalTime, jsonTime, signTime, convertTime);

        return result;
    }

    /**
     * Alternative optimized signing method using direct parameter values to avoid
     * JSON parsing. This method constructs the TypedData object directly from
     * parameters rather than parsing JSON.
     */
    public String signOrderMessageDirect(long timestamp, String market, String side, String orderType, String size,
            String price) {
        long totalStart = System.nanoTime();

        // Ensure cache is initialized
        if (cachedSigner == null) {
            initializeSigningCache();
        }

        // Cache chainIdHex for reuse
        if (cachedChainIdHex == null) {
            cachedChainIdHex = "0x" + chainID.toString(16).toUpperCase();
        }

        long constructStart = System.nanoTime();
        TypedData typedData = createOrderTypedDataDirect(timestamp, market, side, orderType, size, price);
        long constructTime = (System.nanoTime() - constructStart) / 1_000_000;

        // Sign using cached objects
        long signStart = System.nanoTime();
        Felt msgHash = typedData.getMessageHash(cachedAccountAddress); // faster than full signTypedData
        StarknetCurveSignature sig = StarknetCurve.sign(cachedPrivateKey, msgHash);
        // adapt to your existing return type:
        List<Felt> signature = List.of(sig.getR(), sig.getS());

        // List<Felt> signature = cachedSigner.signTypedData(typedData,
        // cachedAccountAddress);
        long signTime = (System.nanoTime() - signStart) / 1_000_000;

        // Convert signature to string using optimized method
        long convertStart = System.nanoTime();
        String result = convertSignatureToString(signature);
        long convertTime = (System.nanoTime() - convertStart) / 1_000_000;

        long totalTime = (System.nanoTime() - totalStart) / 1_000_000;

        logger.debug(
                "Direct signing breakdown - Total: {}ms, TypedData Build: {}ms, Crypto Sign: {}ms, String Convert: {}ms",
                totalTime, constructTime, signTime, convertTime);

        return result;
    }

    /**
     * Creates TypedData object directly without JSON parsing for better
     * performance.
     */
    private TypedData createOrderTypedDataDirect(long timestamp, String market, String side, String orderType,
            String size, String price) {
        // This method would construct the TypedData object programmatically
        // Unfortunately, TypedData doesn't have a public constructor that allows direct
        // creation
        // So we still need to use JSON, but we can optimize the JSON creation

        String optimizedJson = String.format(
                "{\"domain\":{\"name\":\"Paradex\",\"chainId\":\"%s\",\"version\":\"1\"},"
                        + "\"primaryType\":\"Order\"," + "\"types\":{" + "\"StarkNetDomain\":["
                        + "{\"name\":\"name\",\"type\":\"felt\"}," + "{\"name\":\"chainId\",\"type\":\"felt\"},"
                        + "{\"name\":\"version\",\"type\":\"felt\"}" + "]," + "\"Order\":["
                        + "{\"name\":\"timestamp\",\"type\":\"felt\"}," + "{\"name\":\"market\",\"type\":\"felt\"},"
                        + "{\"name\":\"side\",\"type\":\"felt\"}," + "{\"name\":\"orderType\",\"type\":\"felt\"},"
                        + "{\"name\":\"size\",\"type\":\"felt\"}," + "{\"name\":\"price\",\"type\":\"felt\"}" + "]"
                        + "}," + "\"message\":{" + "\"timestamp\":%d," + "\"market\":\"%s\"," + "\"side\":\"%s\","
                        + "\"orderType\":\"%s\"," + "\"size\":\"%s\"," + "\"price\":\"%s\"" + "}}",
                cachedChainIdHex, timestamp, market, side, orderType, size, price);

        return TypedData.fromJsonString(optimizedJson);
    }

    /**
     * Alternative optimized signing method for modify orders using direct parameter
     * values.
     */
    public String signModifyOrderMessageDirect(long timestamp, String market, String side, String orderType,
            String size, String price, String orderId) {
        long totalStart = System.nanoTime();

        // Ensure cache is initialized
        if (cachedSigner == null) {
            initializeSigningCache();
        }

        // Cache chainIdHex for reuse
        if (cachedChainIdHex == null) {
            cachedChainIdHex = "0x" + chainID.toString(16).toUpperCase();
        }

        long constructStart = System.nanoTime();
        TypedData typedData = createModifyOrderTypedDataDirect(timestamp, market, side, orderType, size, price,
                orderId);
        long constructTime = (System.nanoTime() - constructStart) / 1_000_000;

        // Sign using cached objects
        long signStart = System.nanoTime();
        List<Felt> signature = cachedSigner.signTypedData(typedData, cachedAccountAddress);
        long signTime = (System.nanoTime() - signStart) / 1_000_000;

        // Convert signature to string using optimized method
        long convertStart = System.nanoTime();
        String result = convertSignatureToString(signature);
        long convertTime = (System.nanoTime() - convertStart) / 1_000_000;

        long totalTime = (System.nanoTime() - totalStart) / 1_000_000;

        logger.debug("Signing info market: {} orderId: {}", market, orderId);
        logger.debug(
                "Direct modify signing breakdown - Total: {}ms, TypedData Build: {}ms, Crypto Sign: {}ms, String Convert: {}ms",
                totalTime, constructTime, signTime, convertTime);

        return result;
    }

    /**
     * Creates TypedData object for modify orders directly without JSON parsing.
     */
    private TypedData createModifyOrderTypedDataDirect(long timestamp, String market, String side, String orderType,
            String size, String price, String orderId) {
        String optimizedJson = String.format(
                "{\"domain\":{\"name\":\"Paradex\",\"chainId\":\"%s\",\"version\":\"1\"},"
                        + "\"primaryType\":\"ModifyOrder\"," + "\"types\":{" + "\"StarkNetDomain\":["
                        + "{\"name\":\"name\",\"type\":\"felt\"}," + "{\"name\":\"chainId\",\"type\":\"felt\"},"
                        + "{\"name\":\"version\",\"type\":\"felt\"}" + "]," + "\"ModifyOrder\":["
                        + "{\"name\":\"timestamp\",\"type\":\"felt\"}," + "{\"name\":\"market\",\"type\":\"felt\"},"
                        + "{\"name\":\"side\",\"type\":\"felt\"}," + "{\"name\":\"orderType\",\"type\":\"felt\"},"
                        + "{\"name\":\"size\",\"type\":\"felt\"}," + "{\"name\":\"price\",\"type\":\"felt\"},"
                        + "{\"name\":\"id\",\"type\":\"felt\"}" + "]" + "}," + "\"message\":{" + "\"timestamp\":%d,"
                        + "\"market\":\"%s\"," + "\"side\":\"%s\"," + "\"orderType\":\"%s\"," + "\"size\":\"%s\","
                        + "\"price\":\"%s\"," + "\"id\":\"%s\"" + "}}",
                cachedChainIdHex, timestamp, market, side, orderType, size, price, orderId);

        return TypedData.fromJsonString(optimizedJson);
    }

    /**
     * Gets the public key hex string. This is cached after first computation.
     * 
     * @return The public key in hex format
     */
    public String getPublicKeyHex() {
        if (starkPublicKeyHex == null) {
            initializeSigningCache();
        }
        return starkPublicKeyHex;
    }

    /**
     * Clears the signing cache. Call this if credentials change or you want to free
     * memory.
     */
    public void clearCache() {
        cachedAccountAddress = null;
        cachedPrivateKey = null;
        cachedSigner = null;
        starkPublicKeyHex = null;
        cachedChainIdHex = null;
        logger.debug("Signing cache cleared for account: {}", accountAddressString);
    }

    /**
     * Initializes the signing cache with cryptographic objects. This is expensive
     * but doing it once upfront improves signing performance significantly.
     * Thread-safe via double-check locking pattern.
     */
    private void initializeSigningCache() {
        if (cachedSigner != null) {
            return; // Already initialized
        }

        synchronized (this) {
            // Double-check pattern to avoid unnecessary synchronization after
            // initialization
            if (cachedSigner != null) {
                return;
            }

            if (accountAddressString != null && privateKeyString != null) {
                long startTime = System.nanoTime();

                cachedAccountAddress = Felt.fromHex(accountAddressString);
                cachedPrivateKey = Felt.fromHex(privateKeyString);
                cachedSigner = new StarkCurveSigner(cachedPrivateKey);

                // Cache the public key hex string since it never changes
                Felt publicKey = cachedSigner.getPublicKey();
                starkPublicKeyHex = publicKey.hexString();

                long initTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
                logger.info("Signing cache initialized for account: {} in {}ms", accountAddressString, initTime);
            }
        }
    }

    /**
     * Optimized method to convert signature to string format. Uses ThreadLocal
     * StringBuilder for better performance and thread safety.
     */
    private String convertSignatureToString(List<Felt> signature) {
        StringBuilder sb = stringBuilderLocal.get();
        sb.setLength(0); // Clear previous content
        sb.append('[');

        for (int i = 0; i < signature.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(signature.get(i).getValue().toString()).append('"');
        }

        sb.append(']');
        return sb.toString();
    }

    /**
     * Gets the account address string.
     * 
     * @return The account address in hex format
     */
    public String getAccountAddress() {
        return accountAddressString;
    }

    /**
     * Optimized method to sign a pre-constructed JSON message. Use this when you
     * can reuse the same message structure with different values.
     * 
     * @param preConstructedJson A pre-built JSON string (avoids string formatting
     *                           overhead)
     * @return The signature string
     */
    public String signPreConstructedMessage(String preConstructedJson) {
        long totalStart = System.nanoTime();

        // Ensure cache is initialized
        if (cachedSigner == null) {
            initializeSigningCache();
        }

        // Parse JSON (still required for StarkNet library)
        long jsonStart = System.nanoTime();
        TypedData typedData = TypedData.fromJsonString(preConstructedJson);
        long jsonTime = (System.nanoTime() - jsonStart) / 1_000_000;

        // Sign using cached objects (this is the 133ms bottleneck)
        long signStart = System.nanoTime();
        List<Felt> signature = cachedSigner.signTypedData(typedData, cachedAccountAddress);
        long signTime = (System.nanoTime() - signStart) / 1_000_000;

        // Convert signature to string
        long convertStart = System.nanoTime();
        String result = convertSignatureToString(signature);
        long convertTime = (System.nanoTime() - convertStart) / 1_000_000;

        long totalTime = (System.nanoTime() - totalStart) / 1_000_000;

        logger.debug(
                "Pre-constructed signing breakdown - Total: {}ms, JSON Parse: {}ms, Crypto Sign: {}ms, String Convert: {}ms",
                totalTime, jsonTime, signTime, convertTime);

        return result;
    }

    /**
     * Gets the chain ID.
     * 
     * @return The chain ID for this signer
     */
    public BigInteger getChainID() {
        return chainID;
    }

    /**
     * Checks if the signer is properly initialized with credentials.
     * 
     * @return true if both account address and private key are set
     */
    public boolean isInitialized() {
        return accountAddressString != null && !accountAddressString.isEmpty() && privateKeyString != null
                && !privateKeyString.isEmpty();
    }
}