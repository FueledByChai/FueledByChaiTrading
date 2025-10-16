/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.realtime.bar.ib;

import com.fueledbychai.realtime.bar.ib.util.RealtimeBarUtil;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 *
 * @author FueledByChai Contributors
 */
public class BarBuilderJob implements Job{

    public void execute(JobExecutionContext jec) throws JobExecutionException {
        IBarBuilder builder = (IBarBuilder) jec.getJobDetail().getJobDataMap().get(RealtimeBarUtil.BAR_BUILDER_DATA);
        if( builder != null ) {
            builder.buildBarAndFireEvents();
        }
    }
    
    
    
}
