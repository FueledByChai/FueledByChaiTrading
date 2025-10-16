/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.ib;

import com.ib.client.Contract;
import com.fueledbychai.data.Ticker;

/**
 *
 *  
 *
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates and open the template
 * in the editor.
 */
/**
 *
 *  
 */
public class CFDContractBuilder implements IContractBuilder<Ticker> {

    @Override
    public Contract buildContract(Ticker ticker) {
        Contract contract = new Contract();
        contract.currency(ticker.getCurrency());
        contract.exchange(ticker.getExchange().getExchangeName());
        contract.secType(IbUtils.getSecurityType(ticker.getInstrumentType()));
        contract.symbol(ticker.getSymbol());
        if (ticker.getPrimaryExchange() != null) {
            contract.primaryExch(ticker.getPrimaryExchange().getExchangeName());
        }
        return contract;
    }

}
