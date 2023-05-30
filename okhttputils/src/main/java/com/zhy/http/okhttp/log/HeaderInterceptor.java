package com.zhy.http.okhttp.log;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class HeaderInterceptor implements Interceptor {
    private final String _key;
    private final String _value;

    /**
     * 全局Header<br/>
     * 如:<code>new HeaderInterceptor("Authorization", "Bearer " + token)</code>
     */
    public HeaderInterceptor(String key, String value) {
        this._key = key;
        this._value = value;
    }


    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        Request newRequest = request.newBuilder().addHeader(_key, _value).build();
        return chain.proceed(newRequest);
    }

}
