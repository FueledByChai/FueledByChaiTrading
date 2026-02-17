package com.fueledbychai.binance;

import com.fueledbychai.binance.ws.BinanceWebSocketClient;
import com.fueledbychai.binance.ws.BinanceWebSocketClientBuilder;
import com.fueledbychai.binance.ws.aggtrade.AggTradeRecordProcessor;
import com.fueledbychai.binance.ws.partialbook.OrderBookSnapshot;
import com.fueledbychai.binance.ws.partialbook.PartialOrderBookProcessor;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;
import com.fueledbychai.websocket.IWebSocketProcessor;
import com.fueledbychai.websocket.ProxyConfig;

public class BinanceClientTest {

    public static void main(String[] args) throws Exception {

        // Configure SOCKS proxy (uncomment and modify as needed)

        System.setProperty("socksProxyHost", "127.0.0.1");
        System.setProperty("socksProxyPort", "1080");
        System.setProperty("binance.run.proxy", "true");
        ProxyConfig.getInstance().setRunningLocally(true);

        // IBinanceRestApi api =
        // BinanceRestApi.getPublicOnlyApi("https://api.binance.com/api/v3");
        // BinanceInstrumentDescriptorResult result =
        // api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);
        // System.out.println("Retrieved " + result.getSymbols().size() +
        // "instruments.");
        // System.out.println("Sample instrument: " + result.getSymbols().get(0));

        ITickerRegistry tickerRegistry = TickerRegistryFactory.getInstance(Exchange.BINANCE_SPOT);
        Ticker ticker = tickerRegistry.lookupByCommonSymbol(InstrumentType.CRYPTO_SPOT, "ZEC/USDT");
        System.out.println("Lookup ZEC/USDT: " + ticker);

        BinanceWebSocketClient client = BinanceWebSocketClientBuilder
                .buildPartialBookDepth("wss://fstream.binance.com/stream", ticker, new PartialOrderBookProcessor(null));

        AggTradeRecordProcessor processor = new AggTradeRecordProcessor(() -> {

        });
        processor.addEventListener(event -> {
            System.out.println("AggTradeRecord: " + event);
        });

        BinanceWebSocketClient tradesClient = BinanceWebSocketClientBuilder
                .buildTradesClient("wss://fstream.binance.com/stream", ticker, processor);
        // client.connect();
        tradesClient.connect();

    }
}
