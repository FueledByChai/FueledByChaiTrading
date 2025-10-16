/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.reporting.csv;

import com.fueledbychai.reporting.IRoundTrip;

/**
 *
 *  
 */
public class TradeRoundTripBuilder implements IRoundTripBuilder {

    @Override
    public IRoundTrip buildRoundTrip() {
        return new TradeRoundTrip();
    }
    
    
}
