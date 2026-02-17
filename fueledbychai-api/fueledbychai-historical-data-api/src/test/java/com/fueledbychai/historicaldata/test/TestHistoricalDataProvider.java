package com.fueledbychai.historicaldata.test;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import com.fueledbychai.data.BarData;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.historicaldata.IHistoricalDataProvider;

public class TestHistoricalDataProvider implements IHistoricalDataProvider {

    @Override
    public void init(Properties props) {
        // no-op
    }

    @Override
    public List<BarData> requestHistoricalData(Ticker ticker, Date endDateTime, int duration,
            BarData.LengthUnit durationLengthUnit, int barSize, BarData.LengthUnit barSizeUnit, ShowProperty whatToShow,
            boolean useRTH) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<BarData> requestHistoricalData(Ticker ticker, int duration, BarData.LengthUnit durationLengthUnit,
            int barSize, BarData.LengthUnit barSizeUnit, ShowProperty whatToShow, boolean useRTH) {
        return Collections.emptyList();
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void connect() {
        // no-op
    }
}
