package com.zhy.http.okhttp.http3;

import android.util.Log;

import java.io.IOException;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@SuppressWarnings("unused")
public class CronetInterceptor implements Interceptor {
    // 拦截器日志标签，输出中文信息
    private static final String TAG = "CronetInterceptor";

    private final Http3Engine http3Engine;
    private final CookieJar cookieJar;
    private final CronetCallBridge bridge;

    public CronetInterceptor(Http3Engine http3Engine, CookieJar cookieJar) {
        if (http3Engine == null) throw new IllegalArgumentException("http3Engine == null");
        this.http3Engine = http3Engine;
        this.cookieJar = cookieJar;
        this.bridge = new CronetCallBridge(http3Engine.engine());
    }

    /** Testing constructor – injects a custom bridge. */
    CronetInterceptor(Http3Engine http3Engine, CookieJar cookieJar, CronetCallBridge bridge) {
        if (http3Engine == null) throw new IllegalArgumentException("http3Engine == null");
        this.http3Engine = http3Engine;
        this.cookieJar = cookieJar;
        this.bridge = bridge != null ? bridge : new CronetCallBridge(http3Engine.engine());
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        boolean eligible = http3Engine.shouldUseCronet(request.url());

        if (!eligible) {
            return chain.proceed(request);
        }

        Request cronetRequest = applyCookies(request);
        try {
            Response response = bridge.execute(cronetRequest);
            saveCookies(cronetRequest, response);
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Cronet failed, falling back to OkHttp: "
                    + request.method() + " " + request.url(), e);
            return chain.proceed(request);
        }
    }

    private Request applyCookies(Request request) {
        if (cookieJar == null) return request;
        List<Cookie> cookies = cookieJar.loadForRequest(request.url());
        if (cookies == null || cookies.isEmpty()) return request;
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = cookies.size(); i < n; i++) {
            Cookie c = cookies.get(i);
            if (i > 0) sb.append("; ");
            sb.append(c.name()).append('=').append(c.value());
        }
        return request.newBuilder().header("Cookie", sb.toString()).build();
    }

    private void saveCookies(Request request, Response response) {
        if (cookieJar == null || response == null) return;
        List<Cookie> cookies = Cookie.parseAll(request.url(), response.headers());
        if (!cookies.isEmpty()) cookieJar.saveFromResponse(request.url(), cookies);
    }
}
