package com.fueledbychai.extended.common.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Scales human-readable order quantities/prices into the signed integer
 * "stark" amounts that go into the Extended order hash.
 *
 * <p>
 * Mirrors the X10 SDK {@code create_order_settlement_data} logic exactly:
 * </p>
 * <ul>
 * <li>{@code syntheticHuman = qty}</li>
 * <li>{@code collateralHuman = qty * price}</li>
 * <li>{@code feeHuman = feeRate * collateralHuman}</li>
 * <li>synthetic/collateral are rounded with the side context (BUY =&gt; UP, SELL
 * =&gt; DOWN); fee is always rounded UP</li>
 * <li>BUY negates the collateral amount; SELL negates the synthetic amount</li>
 * </ul>
 */
public record ExtendedStarkAmounts(BigInteger syntheticAmount, BigInteger collateralAmount, BigInteger feeAmount) {

    /**
     * @param isBuy                buying the synthetic asset
     * @param qty                  order size in synthetic (base) units
     * @param price                limit price in collateral units
     * @param feeRate              taker (+ builder) fee rate, e.g. 0.0005
     * @param syntheticResolution  synthetic asset stark resolution (e.g. 1_000_000)
     * @param collateralResolution collateral asset stark resolution (e.g. 1_000_000)
     */
    public static ExtendedStarkAmounts compute(boolean isBuy, BigDecimal qty, BigDecimal price, BigDecimal feeRate,
            long syntheticResolution, long collateralResolution) {
        RoundingMode sideRounding = isBuy ? RoundingMode.UP : RoundingMode.DOWN;

        BigDecimal syntheticHuman = qty;
        BigDecimal collateralHuman = qty.multiply(price);
        BigDecimal feeHuman = feeRate.multiply(collateralHuman);

        BigInteger synthetic = scale(syntheticHuman, syntheticResolution, sideRounding);
        BigInteger collateral = scale(collateralHuman, collateralResolution, sideRounding);
        BigInteger fee = scale(feeHuman, collateralResolution, RoundingMode.UP);

        if (isBuy) {
            collateral = collateral.negate();
        } else {
            synthetic = synthetic.negate();
        }
        return new ExtendedStarkAmounts(synthetic, collateral, fee);
    }

    private static BigInteger scale(BigDecimal human, long resolution, RoundingMode rounding) {
        return human.multiply(BigDecimal.valueOf(resolution)).setScale(0, rounding).toBigInteger();
    }
}
