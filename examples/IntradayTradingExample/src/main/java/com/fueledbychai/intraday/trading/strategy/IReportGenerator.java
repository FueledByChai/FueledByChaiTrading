/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.intraday.trading.strategy;

import com.fueledbychai.broker.order.OrderEventListener;
import java.io.IOException;

/**
 *
 *  
 */
public interface IReportGenerator extends OrderEventListener {

    void deletePartial(String correlationId) throws IOException;

    void loadPartialRoundTrips() throws IOException;

    void savePartial(String correlationId, RoundTrip trip) throws IOException;
    
}
