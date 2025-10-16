/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.ib.symbol;

import com.fueledbychai.util.FuturesUtil;

/**
 *
 *  
 */
public class HKFELocalSymbolBuilder implements ILocalSymbolBuilder {

    @Override
    public String buildLocalSymbol(String symbol, int expirationMonth, int expirationYear) {
        return FuturesUtil.getFullFuturesSymbolWithOneDigitYear(symbol, expirationMonth, expirationYear);
    }
    
}
