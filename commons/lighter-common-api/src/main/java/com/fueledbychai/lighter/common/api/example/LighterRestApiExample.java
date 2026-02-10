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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.lighter.common.api.LighterConfiguration;
import com.fueledbychai.lighter.common.api.LighterRestApi;

public class LighterRestApiExample {

    private static final Logger logger = LoggerFactory.getLogger(LighterRestApiExample.class);
    private static final int MAX_PRINT = 3000;

    public static void main(String[] args) {
        String baseUrlArg = getArgOrSystemOrEnv(args, 0, "lighter.base.url", "LIGHTER_BASE_URL");
        String accountAddress = getArgOrSystemOrEnv(args, 1, LighterConfiguration.LIGHTER_ACCOUNT_ADDRESS,
                "LIGHTER_ACCOUNT_ADDRESS");
        String privateKey = getArgOrSystemOrEnv(args, 2, LighterConfiguration.LIGHTER_PRIVATE_KEY,
                "LIGHTER_PRIVATE_KEY");
        String isTestnetArg = getArgOrSystemOrEnv(args, 3, "lighter.is.testnet", "LIGHTER_IS_TESTNET");
        String symbol = getArgOrSystemOrEnv(args, 4, "lighter.symbol", "LIGHTER_SYMBOL");
        String runProxyArg = getArgOrSystemOrEnv(args, 5, LighterConfiguration.LIGHTER_RUN_PROXY, "LIGHTER_RUN_PROXY");
        String proxyHostArg = getArgOrSystemOrEnv(args, 6, LighterConfiguration.LIGHTER_PROXY_HOST,
                "LIGHTER_PROXY_HOST");
        String proxyPortArg = getArgOrSystemOrEnv(args, 7, LighterConfiguration.LIGHTER_PROXY_PORT,
                "LIGHTER_PROXY_PORT");

        if (runProxyArg != null && !runProxyArg.isBlank()) {
            System.setProperty(LighterConfiguration.LIGHTER_RUN_PROXY, runProxyArg);
        }
        if (proxyHostArg != null && !proxyHostArg.isBlank()) {
            System.setProperty(LighterConfiguration.LIGHTER_PROXY_HOST, proxyHostArg);
        }
        if (proxyPortArg != null && !proxyPortArg.isBlank()) {
            System.setProperty(LighterConfiguration.LIGHTER_PROXY_PORT, proxyPortArg);
        }

        LighterConfiguration.reset();
        LighterConfiguration configuration = LighterConfiguration.getInstance();

        String baseUrl = (baseUrlArg == null || baseUrlArg.isBlank()) ? configuration.getRestUrl() : baseUrlArg;
        boolean isTestnet = isTestnetArg == null || isTestnetArg.isBlank() ? !configuration.isProductionEnvironment()
                : Boolean.parseBoolean(isTestnetArg);

        LighterRestApi api = (accountAddress == null || privateKey == null || accountAddress.isBlank()
                || privateKey.isBlank()) ? new LighterRestApi(baseUrl, isTestnet)
                        : new LighterRestApi(baseUrl, accountAddress, privateKey, isTestnet);

        try {
            logger.info("Running Lighter REST example with proxyEnabled={} proxy={}:{}", configuration.isProxyEnabled(),
                    configuration.getProxyHost(), configuration.getProxyPort());
            InstrumentDescriptor[] perps = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
            logSample("PERPETUAL_FUTURES", perps);

            InstrumentDescriptor[] spot = api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);
            logSample("CRYPTO_SPOT", spot);

            if (symbol != null && !symbol.isBlank()) {
                InstrumentDescriptor descriptor = api.getInstrumentDescriptor(symbol);
                logger.info("InstrumentDescriptor for {}: {}", symbol, descriptor);
            } else {
                logger.info("No symbol provided. Skipping getInstrumentDescriptor call.");
            }
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
            logger.info(descriptors[i].toString() + "\n ");
        }
    }

    private static String getArgOrSystemOrEnv(String[] args, int index, String systemPropertyKey, String envKey) {
        if (args != null && args.length > index && args[index] != null && !args[index].isBlank()) {
            return args[index];
        }
        String systemProperty = System.getProperty(systemPropertyKey);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }
        String envValue = System.getenv(envKey);
        return (envValue == null || envValue.isBlank()) ? null : envValue;
    }
}
