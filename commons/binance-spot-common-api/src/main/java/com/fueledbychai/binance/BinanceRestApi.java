package com.fueledbychai.binance;

import java.io.IOException;
import java.net.Proxy;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.binance.model.BinanceInstrumentDescriptorResult;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.http.BaseRestApi;
import com.fueledbychai.websocket.ProxyConfig;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BinanceRestApi extends BaseRestApi implements IBinanceRestApi {
    protected static Logger logger = LoggerFactory.getLogger(BinanceRestApi.class);
    private final ObjectMapper objectMapper;

    protected static IBinanceRestApi publicOnlyApi;
    protected static IBinanceRestApi privateApi;

    public static IBinanceRestApi getPublicOnlyApi(String baseUrl) {
        if (publicOnlyApi == null) {
            publicOnlyApi = new BinanceRestApi(baseUrl);
        }
        return publicOnlyApi;
    }

    public static IBinanceRestApi getPrivateApi(String baseUrl, String accountAddress, String privateKey) {
        if (privateApi == null) {
            privateApi = new BinanceRestApi(baseUrl, accountAddress, privateKey);
        }
        return privateApi;
    }

    protected OkHttpClient client;
    protected String baseUrl;
    protected String accountAddressString;
    protected String privateKeyString;
    protected boolean publicApiOnly = true;

    public BinanceRestApi(String baseUrl) {
        this(baseUrl, null, null);
    }

    public BinanceRestApi(String baseUrl, String accountAddressString, String privateKeyString) {
        this.client = createHttpClient();
        this.baseUrl = baseUrl;
        this.accountAddressString = accountAddressString;
        this.privateKeyString = privateKeyString;
        // Initialize ObjectMapper for JSON processing
        this.objectMapper = new ObjectMapper();
        // Register the custom adapter
        // this.gson = new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new
        // ZonedDateTimeAdapter()).create();
        publicApiOnly = accountAddressString == null || privateKeyString == null;
    }

    protected OkHttpClient createHttpClient() {
        Proxy proxy = ProxyConfig.getInstance().getProxy();
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (proxy != null && proxy != Proxy.NO_PROXY) {
            builder.proxy(proxy);
        }
        return builder.build();
    }

    @Override
    public BinanceInstrumentDescriptorResult getAllInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.CRYPTO_SPOT) {
            throw new IllegalArgumentException("Only crypto spot is supported at this time.");
        }

        String path = "/exchangeInfo";
        String url = baseUrl + path;
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        String newUrl = urlBuilder.build().toString();
        Request request = new Request.Builder().url(newUrl).get().build();
        logger.info("Request: " + request);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Error response: " + response.body().string());
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            logger.info("Response: " + responseBody);
            BinanceInstrumentDescriptorResult result = objectMapper.readValue(responseBody,
                    BinanceInstrumentDescriptorResult.class);
            return result;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);

            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonNode getBookTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        String path = "/ticker/bookTicker?symbol=" + symbol.trim();
        String url = baseUrl + path;
        HttpUrl parsedUrl = HttpUrl.parse(url);
        if (parsedUrl == null) {
            throw new IllegalArgumentException("Invalid URL for path " + path);
        }
        Request request = new Request.Builder().url(parsedUrl).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new IOException("Unexpected Binance response " + response.code() + ": " + body);
            }
            if (response.body() == null) {
                throw new IOException("Empty Binance response");
            }
            return objectMapper.readTree(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException("Unable to load book ticker for " + symbol, e);
        }
    }

    private class ZonedDateTimeAdapter extends TypeAdapter<ZonedDateTime> {

        private final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

        @Override
        public void write(JsonWriter out, ZonedDateTime value) throws IOException {
            out.value(value.format(FORMATTER));
        }

        @Override
        public ZonedDateTime read(JsonReader in) throws IOException {
            return ZonedDateTime.parse(in.nextString(), FORMATTER);
        }
    }

}
