/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.ib.example.historical.data;

import java.util.List;

import com.fueledbychai.data.BarData;
import com.fueledbychai.data.BarData.LengthUnit;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.historicaldata.IHistoricalDataProvider.ShowProperty;
import com.fueledbychai.interactive.brokers.client.InteractiveBrokersClient;
import com.fueledbychai.interactive.brokers.client.InteractiveBrokersClientInterface;

/**
 *
 *  
 */
public class HistoricalDataExample {

    public void requestHistoricalData() {
        InteractiveBrokersClientInterface client = InteractiveBrokersClient.getInstance("localhost", 6468, 1);
        client.connect();

        Ticker ticker = new Ticker("AMZN").setInstrumentType(InstrumentType.STOCK);
        int duration = 1;
        LengthUnit durationUnit = LengthUnit.DAY;
        int barSize = 1;
        LengthUnit barSizeUnit = LengthUnit.MINUTE;
        ShowProperty dataToRequest = ShowProperty.TRADES;

        List<BarData> historicalData = client.requestHistoricalData(ticker, duration, durationUnit, barSize,
                barSizeUnit, dataToRequest);

        System.out.println("Retrieved " + historicalData.size() + " bars");
        historicalData.stream().forEach((bar) -> {
            System.out.println("Retrieved Bar: " + bar);
        });

        client.disconnect();
    }

    public static void main(String[] args) {
        new HistoricalDataExample().requestHistoricalData();
    }
}
