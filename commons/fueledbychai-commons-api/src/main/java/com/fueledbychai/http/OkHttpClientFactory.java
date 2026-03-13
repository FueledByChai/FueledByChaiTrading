package com.fueledbychai.http;

import java.net.Proxy;
import java.time.Duration;

import com.fueledbychai.websocket.ProxyConfig;

import okhttp3.OkHttpClient;

public final class OkHttpClientFactory {

    private OkHttpClientFactory() {
    }

    public static OkHttpClient create(Duration timeout) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (timeout != null) {
            builder.connectTimeout(timeout)
                    .readTimeout(timeout)
                    .writeTimeout(timeout)
                    .callTimeout(timeout);
        }

        Proxy proxy = ProxyConfig.getInstance().getProxy();
        if (proxy != null && proxy != Proxy.NO_PROXY) {
            builder.proxy(proxy);
        }
        return builder.build();
    }

    public static OkHttpClient create() {
        return create(null);
    }
}
