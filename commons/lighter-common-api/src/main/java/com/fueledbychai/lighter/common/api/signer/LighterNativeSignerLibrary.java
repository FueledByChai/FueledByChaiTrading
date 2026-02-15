package com.fueledbychai.lighter.common.api.signer;

import com.sun.jna.Library;
import com.sun.jna.Structure;

public interface LighterNativeSignerLibrary extends Library {

    @Structure.FieldOrder({ "result", "err" })
    class StrOrErr extends Structure implements Structure.ByValue {
        public String result;
        public String err;
    }

    @Structure.FieldOrder({ "publicKey", "privateKey", "err" })
    class ApiKeyResponse extends Structure implements Structure.ByValue {
        public String publicKey;
        public String privateKey;
        public String err;
    }

    @Structure.FieldOrder({ "txType", "txInfo", "txHash", "messageToSign", "err" })
    class SignedTxResponse extends Structure implements Structure.ByValue {
        public int txType;
        public String txInfo;
        public String txHash;
        public String messageToSign;
        public String err;
    }

    @Structure.FieldOrder({ "marketIndex", "clientOrderIndex", "baseAmount", "price", "isAsk", "orderType",
            "timeInForce", "reduceOnly", "triggerPrice", "orderExpiry", "nonce" })
    class CreateOrderTxReq extends Structure {
        public int marketIndex;
        public long clientOrderIndex;
        public long baseAmount;
        public int price;
        public boolean isAsk;
        public int orderType;
        public int timeInForce;
        public boolean reduceOnly;
        public int triggerPrice;
        public long orderExpiry;
        public long nonce;

        public static class ByReference extends CreateOrderTxReq implements Structure.ByReference {
        }
    }

    ApiKeyResponse GenerateAPIKey();

    String CreateClient(String url, String privateKey, int chainID, int apiKeyIndex, long accountIndex);

    String CheckClient(int apiKeyIndex, long accountIndex);

    String CreateAuthTokenWithExpiry(long timestamp, long expiry, int apiKeyIndex, long accountIndex);

    StrOrErr SignL1Action(String msgpackData, int apiKeyIndex, long accountIndex);

    StrOrErr SignOpenOrder(String msgpackData, int apiKeyIndex, long accountIndex);

    StrOrErr SignL1ActionV2(String msgpackData, long nonce, int apiKeyIndex, long accountIndex);

    StrOrErr SignOpenOrderV2(String msgpackData, long nonce, int apiKeyIndex, long accountIndex);

    StrOrErr SignCloseOrder(String msgpackData, String closeType, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignCreateOrder(int marketIndex, long clientOrderIndex, long baseAmount, int price, int isAsk,
            int orderType, int timeInForce, int reduceOnly, int triggerPrice, long orderExpiry, long nonce,
            int apiKeyIndex, long accountIndex);

    SignedTxResponse SignCancelOrder(int marketIndex, long orderIndex, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignCancelAllOrders(int marketIndex, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignModifyOrder(int marketIndex, long orderIndex, long baseAmount, int price, int isAsk,
            int orderType, int timeInForce, int reduceOnly, int triggerPrice, long orderExpiry, long nonce,
            int apiKeyIndex, long accountIndex);

    SignedTxResponse SignCreateOrderGroup(CreateOrderTxReq.ByReference orders, long ordersLen, long nonce,
            int apiKeyIndex, long accountIndex);

    SignedTxResponse SignSetReferrer(int referrerCode, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignUpdateAccountMargin(long amount, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignUpdateLeverage(int marketIndex, long leverage, int leverageType, long nonce, int apiKeyIndex,
            long accountIndex);

    SignedTxResponse SignDelegateSigner(long delegateAddress, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignRevokeSigner(int apiKeyIndex, long accountIndex);

    SignedTxResponse SignAddApiKey(int addApiKeyIndex, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignAddApiKeyWithPubKey(String pubKey, int addApiKeyIndex, long nonce, int apiKeyIndex,
            long accountIndex);

    SignedTxResponse SignRemoveApiKey(int removeApiKeyIndex, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignWithdraw(String usdcAddress, long amount, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignConvertUSDC(boolean fromL1ToL2, long amount, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignAccountTransfer(boolean isDeposit, long amount, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignCreateSubAccount(int l1Address, long nonce, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignAddSubAccountSigner(int subAccountID, long signer, int signerApiKeyIndex, long nonce,
            int apiKeyIndex, long accountIndex);

    SignedTxResponse SignRemoveSubAccountSigner(int subAccountID, int signerApiKeyIndex, int apiKeyIndex,
            long accountIndex);

    SignedTxResponse SignDeleteSubAccount(int subAccountID, int apiKeyIndex, long accountIndex);

    SignedTxResponse SignSetSubAccountMarginType(int subAccountID, int marginType, long nonce, int apiKeyIndex,
            long accountIndex);

    SignedTxResponse SignSetSubAccountValue(int subAccountID, int tokenID, long value, int apiKeyIndex,
            long accountIndex);

    String GetPublicKey(int apiKeyIndex, long accountIndex);

    String GetApiPublicKey(int apiKeyIndex, long accountIndex);

    String GetApiPrivateKey(int apiKeyIndex, long accountIndex);
}
