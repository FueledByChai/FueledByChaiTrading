package com.fueledbychai.binance;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.fueledbychai.binance.model.BinanceInstrumentDescriptorResult;
import com.fueledbychai.broker.Position;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.Ticker;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BinanceRestApi {
    protected static Logger logger = LoggerFactory.getLogger(BinanceRestApi.class);
    private final Gson gson;
    private final ObjectMapper objectMapper;

    protected static BinanceRestApi publicOnlyApi;
    protected static BinanceRestApi privateApi;

    // https://api.binance.com/api/v3/

    public static BinanceRestApi getPublicOnlyApi(String baseUrl) {
        if (publicOnlyApi == null) {
            publicOnlyApi = new BinanceRestApi(baseUrl);
        }
        return publicOnlyApi;
    }

    public static BinanceRestApi getPrivateApi(String baseUrl, String accountAddress, String privateKey) {
        if (privateApi == null) {
            privateApi = new BinanceRestApi(baseUrl, accountAddress, privateKey);
        }
        return privateApi;
    }

    @FunctionalInterface
    public interface RetryableAction {
        void run() throws Exception; // Allows throwing checked exceptions
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
        this.client = new OkHttpClient();
        this.baseUrl = baseUrl;
        this.accountAddressString = accountAddressString;
        this.privateKeyString = privateKeyString;
        // Initialize ObjectMapper for JSON processing
        this.objectMapper = new ObjectMapper();
        // Register the custom adapter
        this.gson = new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).create();
        publicApiOnly = accountAddressString == null || privateKeyString == null;
    }

    public BinanceInstrumentDescriptorResult getAllInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES) {
            throw new IllegalArgumentException("Only perpetual futures are supported at this time.");
        }

        String path = "/exchangeInfo";
        String url = baseUrl + path;
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        String newUrl = urlBuilder.build().toString();
        RequestBody body = RequestBody.create("{\"type\":\"meta\"}", MediaType.parse("application/json"));

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
