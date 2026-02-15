package com.fueledbychai.lighter.common.api.signer;

import java.io.File;
import java.util.Locale;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.lighter.common.api.LighterConfiguration;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.sun.jna.Native;

/**
 * Wraps the official Lighter signer shared library.
 */
public class LighterNativeTransactionSigner implements ILighterTransactionSigner {

    private static final Logger logger = LoggerFactory.getLogger(LighterNativeTransactionSigner.class);
    private static final int MAINNET_CHAIN_ID = 304;
    private static final int TESTNET_CHAIN_ID = 300;

    private final LighterNativeSignerLibrary nativeSigner;

    public LighterNativeTransactionSigner(LighterConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration is required");
        }
        String restUrl = configuration.getRestUrl();
        String privateKey = normalizePrivateKey(configuration.getPrivateKey());
        int chainId = resolveChainId(restUrl);
        int apiKeyIndex = configuration.getApiKeyIndex();
        long accountIndex = configuration.getAccountIndex();
        this.nativeSigner = loadNativeSigner(configuration.getSignerLibraryPath());

        String createError = trimToNull(
                nativeSigner.CreateClient(restUrl, privateKey, chainId, apiKeyIndex, accountIndex));
        if (createError != null) {
            throw new IllegalStateException("Unable to initialize Lighter signer client: " + createError);
        }

        String checkError = trimToNull(nativeSigner.CheckClient(apiKeyIndex, accountIndex));
        if (checkError != null) {
            throw new IllegalStateException("Lighter signer self-check failed: " + checkError);
        }
    }

    @Override
    public LighterSignedTransaction signCreateOrder(LighterCreateOrderRequest orderRequest) {
        if (orderRequest == null) {
            throw new IllegalArgumentException("orderRequest is required");
        }
        orderRequest.validate();

        LighterNativeSignerLibrary.SignedTxResponse response = nativeSigner.SignCreateOrder(
                orderRequest.getMarketIndex(),
                orderRequest.getClientOrderIndex(),
                orderRequest.getBaseAmount(),
                orderRequest.getPrice(),
                orderRequest.isAsk() ? 1 : 0,
                orderRequest.getOrderType().getCode(),
                orderRequest.getTimeInForce().getCode(),
                orderRequest.isReduceOnly() ? 1 : 0,
                orderRequest.getTriggerPrice(),
                orderRequest.getOrderExpiry(),
                orderRequest.getNonce(),
                orderRequest.getApiKeyIndex(),
                orderRequest.getAccountIndex());

        return mapSignedTxResponse(response, "create-order");
    }

    @Override
    public LighterSignedTransaction signCancelOrder(LighterCancelOrderRequest cancelRequest) {
        if (cancelRequest == null) {
            throw new IllegalArgumentException("cancelRequest is required");
        }
        cancelRequest.validate();

        LighterNativeSignerLibrary.SignedTxResponse response = nativeSigner.SignCancelOrder(
                cancelRequest.getMarketIndex(),
                cancelRequest.getOrderIndex(),
                cancelRequest.getNonce(),
                cancelRequest.getApiKeyIndex(),
                cancelRequest.getAccountIndex());

        return mapSignedTxResponse(response, "cancel-order");
    }

    @Override
    public LighterSignedTransaction signModifyOrder(LighterModifyOrderRequest modifyRequest) {
        if (modifyRequest == null) {
            throw new IllegalArgumentException("modifyRequest is required");
        }
        modifyRequest.validate();

        LighterNativeSignerLibrary.SignedTxResponse response = nativeSigner.SignModifyOrder(
                modifyRequest.getMarketIndex(),
                modifyRequest.getOrderIndex(),
                modifyRequest.getBaseAmount(),
                modifyRequest.getPrice(),
                modifyRequest.isAsk() ? 1 : 0,
                modifyRequest.getOrderType().getCode(),
                modifyRequest.getTimeInForce().getCode(),
                modifyRequest.isReduceOnly() ? 1 : 0,
                modifyRequest.getTriggerPrice(),
                modifyRequest.getOrderExpiry(),
                modifyRequest.getNonce(),
                modifyRequest.getApiKeyIndex(),
                modifyRequest.getAccountIndex());

        return mapSignedTxResponse(response, "modify-order");
    }

    protected LighterSignedTransaction mapSignedTxResponse(LighterNativeSignerLibrary.SignedTxResponse response,
            String actionName) {
        if (response == null) {
            throw new IllegalStateException("Signer returned null response for " + actionName);
        }

        String err = trimToNull(response.err);
        if (err != null) {
            throw new IllegalStateException("Unable to sign Lighter " + actionName + ": " + err);
        }
        if (response.txType <= 0) {
            throw new IllegalStateException("Signer returned invalid tx type: " + response.txType);
        }

        String txInfoText = trimToNull(response.txInfo);
        if (txInfoText == null) {
            throw new IllegalStateException("Signer returned empty tx_info");
        }
        JSONObject txInfo = new JSONObject(txInfoText);
        return new LighterSignedTransaction(response.txType, txInfo, trimToNull(response.txHash),
                trimToNull(response.messageToSign));
    }

    public LighterApiKey generateApiKey() {
        LighterNativeSignerLibrary.ApiKeyResponse response = nativeSigner.GenerateAPIKey();
        String err = trimToNull(response.err);
        if (err != null) {
            throw new IllegalStateException("Unable to generate Lighter API key: " + err);
        }
        String publicKey = trimToNull(response.publicKey);
        String privateKey = trimToNull(response.privateKey);
        if (publicKey == null || privateKey == null) {
            throw new IllegalStateException("Signer returned invalid API key response");
        }
        return new LighterApiKey(publicKey, privateKey);
    }

    public String createAuthTokenWithExpiry(long timestamp, long expiry, int apiKeyIndex, long accountIndex) {
        String response = trimToNull(nativeSigner.CreateAuthTokenWithExpiry(timestamp, expiry, apiKeyIndex,
                accountIndex));
        if (response == null) {
            throw new IllegalStateException("Signer returned empty auth token");
        }
        return response;
    }

    public String getPublicKey(int apiKeyIndex, long accountIndex) {
        return trimToNull(nativeSigner.GetPublicKey(apiKeyIndex, accountIndex));
    }

    public String getApiPublicKey(int apiKeyIndex, long accountIndex) {
        return trimToNull(nativeSigner.GetApiPublicKey(apiKeyIndex, accountIndex));
    }

    public String getApiPrivateKey(int apiKeyIndex, long accountIndex) {
        return trimToNull(nativeSigner.GetApiPrivateKey(apiKeyIndex, accountIndex));
    }

    protected int resolveChainId(String restUrl) {
        String url = restUrl == null ? "" : restUrl.toLowerCase(Locale.ROOT);
        return url.contains("mainnet") ? MAINNET_CHAIN_ID : TESTNET_CHAIN_ID;
    }

    protected LighterNativeSignerLibrary loadNativeSigner(String configuredLibraryPath) {
        String target = resolveLibraryTarget(configuredLibraryPath);
        logger.info("Loading Lighter signer library from '{}'", target);
        return Native.load(target, LighterNativeSignerLibrary.class);
    }

    protected String resolveLibraryTarget(String configuredLibraryPath) {
        String path = trimToNull(configuredLibraryPath);
        if (path == null) {
            throw new IllegalStateException("Missing signer library path. Set "
                    + LighterConfiguration.LIGHTER_SIGNER_LIBRARY_PATH + " to a signer file or directory.");
        }

        File file = new File(path);
        if (file.isDirectory()) {
            return new File(file, getPlatformLibraryFilename()).getAbsolutePath();
        }
        return file.getAbsolutePath();
    }

    protected String getPlatformLibraryFilename() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        if (osName.contains("mac")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                return "lighter-signer-darwin-arm64.dylib";
            }
            return "lighter-signer-darwin-amd64.dylib";
        }
        if (osName.contains("win")) {
            return "lighter-signer-windows-amd64.dll";
        }
        if (osName.contains("linux")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                return "lighter-signer-linux-arm64.so";
            }
            return "lighter-signer-linux-amd64.so";
        }
        throw new IllegalStateException("Unsupported platform for Lighter signer library: os=" + osName
                + ", arch=" + arch);
    }

    protected String normalizePrivateKey(String privateKey) {
        String value = trimToNull(privateKey);
        if (value == null) {
            throw new IllegalStateException("Missing private key configuration for Lighter signing.");
        }
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return value.substring(2);
        }
        return value;
    }

    protected String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
