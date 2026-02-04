/**
 * MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy of this software
and associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.fueledbychai.lighter.common.api.example;

import java.net.InetSocketAddress;
import java.net.Proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.lighter.common.api.LighterRestApi;

import okhttp3.OkHttpClient;

public class LighterRestApiExample {

    private static final Logger logger = LoggerFactory.getLogger(LighterRestApiExample.class);
    private static final int MAX_PRINT = 10;

    public static void main(String[] args) {
        String baseUrl = "https://mainnet.zklighter.elliot.ai/api/v1";
        String accountAddress = "";
        String privateKey = "";
        boolean isTestnet = false;
        String symbol = "BTC";
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080));
        System.setProperty("socksProxyHost", "127.0.0.1");
        System.setProperty("socksProxyPort", "1080");
        OkHttpClient client = new OkHttpClient.Builder().proxy(proxy).build();

        if (baseUrl == null || baseUrl.isBlank()) {
            logger.error("Missing baseUrl. Provide as arg[0] or env LIGHTER_BASE_URL.");
            logger.error("Usage: <baseUrl> [account] [privateKey] [isTestnet] [symbol]");
            return;
        }

        LighterRestApi api = (accountAddress == null || privateKey == null || accountAddress.isBlank()
                || privateKey.isBlank()) ? new LighterRestApi(baseUrl, isTestnet, client)
                        : new LighterRestApi(baseUrl, accountAddress, privateKey, isTestnet, client);

        try {
            InstrumentDescriptor[] perps = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
            logSample("PERPETUAL_FUTURES", perps);

            // InstrumentDescriptor[] spot =
            // api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);
            // logSample("CRYPTO_SPOT", spot);

            // if (symbol != null && !symbol.isBlank()) {
            // InstrumentDescriptor descriptor = api.getInstrumentDescriptor(symbol);
            // logger.info("InstrumentDescriptor for {}: {}", symbol, descriptor);
            // } else {
            // logger.info("No symbol provided. Skipping getInstrumentDescriptor call.");
            // }
        } catch (Exception e) {
            logger.error("Error calling Lighter REST API", e);
        }
    }

    private static void logSample(String label, InstrumentDescriptor[] descriptors) {
        if (descriptors == null || descriptors.length == 0) {
            logger.info("{}: no instruments returned", label);
            return;
        }

        logger.info("{}: {} instruments (showing up to {})", label, descriptors.length, MAX_PRINT);
        for (int i = 0; i < descriptors.length && i < MAX_PRINT; i++) {
            logger.info(descriptors[i].toString());
        }
    }

    private static String getArgOrEnv(String[] args, int index, String envKey) {
        if (args != null && args.length > index && args[index] != null && !args[index].isBlank()) {
            return args[index];
        }
        String env = System.getenv(envKey);
        return (env == null || env.isBlank()) ? null : env;
    }
}
