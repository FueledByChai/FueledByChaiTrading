/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.reporting;

import com.fueledbychai.broker.order.OrderEventListener;
import java.io.IOException;

/**
 *
 *  
 */
public interface IReportGenerator extends OrderEventListener {

    public void deletePartial(String correlationId) throws IOException;

    public void loadPartialRoundTrips() throws IOException;

    public void savePartial(String correlationId, IRoundTrip trip) throws IOException;
    
}
