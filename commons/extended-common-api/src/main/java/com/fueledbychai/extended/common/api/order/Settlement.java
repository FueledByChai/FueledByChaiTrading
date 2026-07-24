package com.fueledbychai.extended.common.api.order;

/**
 * The Stark settlement payload attached to a {@code POST /user/order} request.
 *
 * <p>
 * Serialized as:
 * </p>
 *
 * <pre>
 * "settlement": {
 *   "signature": { "r": "0x..", "s": "0x.." },
 *   "starkKey": "0x..",
 *   "collateralPosition": "10002"
 * }
 * </pre>
 */
public class Settlement {

    public static class Signature {
        private final String r;
        private final String s;

        public Signature(String r, String s) {
            this.r = r;
            this.s = s;
        }

        public String getR() {
            return r;
        }

        public String getS() {
            return s;
        }
    }

    private final Signature signature;
    private final String starkKey;
    private final String collateralPosition;

    public Settlement(String r, String s, String starkKey, String collateralPosition) {
        this.signature = new Signature(r, s);
        this.starkKey = starkKey;
        this.collateralPosition = collateralPosition;
    }

    public Signature getSignature() {
        return signature;
    }

    public String getStarkKey() {
        return starkKey;
    }

    public String getCollateralPosition() {
        return collateralPosition;
    }
}
