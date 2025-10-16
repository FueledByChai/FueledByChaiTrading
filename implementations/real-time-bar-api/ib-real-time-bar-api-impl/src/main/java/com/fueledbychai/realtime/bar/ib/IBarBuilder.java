/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.realtime.bar.ib;

import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.realtime.bar.RealtimeBarListener;

/**
 *
 * @author FueledByChai Contributors
 */
public interface IBarBuilder extends Level1QuoteListener {

    public void addBarListener(RealtimeBarListener listener);

    public void removeBarListener(RealtimeBarListener listener);
    
    public int getListenerCount();
    
    public void stop();
    
    public void buildBarAndFireEvents();
    
}
