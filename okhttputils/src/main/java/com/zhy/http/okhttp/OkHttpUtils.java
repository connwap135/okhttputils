package com.zhy.http.okhttp;

import android.content.Context;
import android.util.Log;

import com.zhy.http.okhttp.builder.GetBuilder;
import com.zhy.http.okhttp.builder.HeadBuilder;
import com.zhy.http.okhttp.builder.OtherRequestBuilder;
import com.zhy.http.okhttp.builder.PostFileBuilder;
import com.zhy.http.okhttp.builder.PostFormBuilder;
import com.zhy.http.okhttp.builder.PostStringBuilder;
import com.zhy.http.okhttp.callback.Callback;
import com.zhy.http.okhttp.request.RequestCall;
import com.zhy.http.okhttp.utils.Platform;
import com.zhy.http.okhttp.http3.CronetCallBridge;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.chromium.net.CronetEngine;

/**
 * Created by zhy on 15/8/17.
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked", "NullableProblems", "FieldMayBeFinal"})
public class OkHttpUtils {
    public static final long DEFAULT_MILLISECONDS = 10_000L;
    private static final String TAG = "OkHttpUtils";
    private volatile static OkHttpUtils mInstance;
    private OkHttpClient mOkHttpClient;
    private Platform mPlatform;

    public OkHttpUtils(OkHttpClient okHttpClient) {
        if (okHttpClient == null) {
            mOkHttpClient = new OkHttpClient();
        } else {
            mOkHttpClient = okHttpClient;
        }

        mPlatform = Platform.get();
    }


    public static OkHttpUtils initClient(OkHttpClient okHttpClient) {
        if (mInstance == null) {
            synchronized (OkHttpUtils.class) {
                if (mInstance == null) {
                    mInstance = new OkHttpUtils(okHttpClient);
                }
            }
        }
        return mInstance;
    }

    public static OkHttpUtils initClient(OkHttpClient.Builder okHttpBuilder) {
        return initClient(okHttpBuilder == null ? null : okHttpBuilder.build());
    }

    public static OkHttpUtils getInstance() {
        return initClient((OkHttpClient) null);
    }

    public static OkHttpClient.Builder enableHttp3(Context context, OkHttpClient.Builder okHttpBuilder) {
        return enableHttp3(context, okHttpBuilder, (Http3Policy) null);
    }

    public static OkHttpClient.Builder enableHttp3(Context context,
                                                    OkHttpClient.Builder okHttpBuilder,
                                                    Http3Policy.Builder policyBuilder) {
        Http3Policy policy = policyBuilder == null ? null : policyBuilder.build();
        return enableHttp3(context, okHttpBuilder, policy);
    }

    public static OkHttpClient.Builder enableHttp3(Context context,
                                                    OkHttpClientBuilderConfigurer builderConfigurer) {
        return enableHttp3(context, builderConfigurer, (Http3PolicyConfigurer) null);
    }

    public static OkHttpClient.Builder enableHttp3(Context context,
                                                    OkHttpClientBuilderConfigurer builderConfigurer,
                                                    Http3PolicyConfigurer policyConfigurer) {
        OkHttpClient.Builder okHttpBuilder = new OkHttpClient.Builder();
        if (builderConfigurer != null) {
            builderConfigurer.configure(okHttpBuilder);
        }
        return enableHttp3(context, okHttpBuilder, policyConfigurer);
    }

    public static OkHttpClient.Builder enableHttp3(Context context,
                                                    OkHttpClient.Builder okHttpBuilder,
                                                    Http3PolicyConfigurer configurer) {
        if (configurer == null) {
            return enableHttp3(context, okHttpBuilder, (Http3Policy) null);
        }
        Http3Policy.Builder policyBuilder = Http3Policy.newBuilder();
        configurer.configure(policyBuilder);
        return enableHttp3(context, okHttpBuilder, policyBuilder);
    }

    public static Http3Policy.Builder http3Policy() {
        return Http3Policy.newBuilder();
    }

    public static OkHttpClient.Builder enableHttp3(Context context,
                                                    OkHttpClient.Builder okHttpBuilder,
                                                    Http3Policy policy) {
        if (context == null || okHttpBuilder == null) {
            return okHttpBuilder;
        }
        okHttpBuilder.addInterceptor(http3Interceptor(context, policy));
        return okHttpBuilder;
    }

    public static Interceptor http3Interceptor(Context context) {
        return http3Interceptor(context, (Http3Policy) null);
    }

    public static Interceptor http3Interceptor(Context context,
                                                Http3PolicyConfigurer configurer) {
        if (configurer == null) {
            return http3Interceptor(context, (Http3Policy) null);
        }
        Http3Policy.Builder policyBuilder = Http3Policy.newBuilder();
        configurer.configure(policyBuilder);
        return http3Interceptor(context, policyBuilder.build());
    }

    public static Interceptor http3Interceptor(Context context,
                                                Http3Policy policy) {
        if (context == null) {
            return chain -> chain.proceed(chain.request());
        }
        Http3Policy resolvedPolicy = policy == null ? Http3Policy.defaultPolicy() : policy;
        try {
            CronetEngine cronetEngine = new CronetEngine.Builder(context.getApplicationContext())
                    .enableQuic(true)
                    .enableHttp2(true)
                    .build();
            CronetCallBridge bridge = new CronetCallBridge(cronetEngine);
            Log.i(TAG, "Cronet transport enabled (HTTP/3/QUIC available when server supports it).");
            return chain -> {
                Request request = chain.request();
                if (isHttp3Eligible(request, resolvedPolicy)) {
                    try {
                        Response resp = bridge.execute(request);
                        Log.d(TAG, "http3Interceptor protocol=" + resp.protocol());
                        return resp;
                    } catch (Exception e) {
                        Log.w(TAG, "Cronet transport failed, falling back: " + e.getMessage());
                    }
                }
                return chain.proceed(request);
            };
        } catch (Throwable throwable) {
            Log.w(TAG, "Cronet initialization failed, fallback to plain OkHttp transport.", throwable);
            return chain -> chain.proceed(chain.request());
        }
    }

    private static boolean isHttp3Eligible(Request request, Http3Policy policy) {
        if (request == null || !request.url().isHttps()) {
            return false;
        }
        String connection = request.header("Connection");
        String upgrade = request.header("Upgrade");
        if (connection != null && "upgrade".equalsIgnoreCase(connection.trim())) {
            return false;
        }
        if (upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim())) {
            return false;
        }
        return policy == null || policy.isAllowedHost(request.url().host());
    }

    public static final class Http3Policy {
        private static final Http3Policy DEFAULT = new Builder().build();
        private final Set<String> hostWhitelist;
        private final Set<String> hostBlacklist;
        private final boolean allowByDefault;

        private Http3Policy(Builder builder) {
            this.hostWhitelist = Collections.unmodifiableSet(new LinkedHashSet<>(builder.hostWhitelist));
            this.hostBlacklist = Collections.unmodifiableSet(new LinkedHashSet<>(builder.hostBlacklist));
            this.allowByDefault = builder.allowByDefault;
        }

        public static Http3Policy defaultPolicy() {
            return DEFAULT;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        boolean isAllowedHost(String host) {
            if (host == null || host.trim().isEmpty()) {
                return false;
            }
            String normalizedHost = normalize(host);
            if (matches(hostBlacklist, normalizedHost)) {
                return false;
            }
            if (!hostWhitelist.isEmpty()) {
                return matches(hostWhitelist, normalizedHost);
            }
            return allowByDefault;
        }

        private static boolean matches(Set<String> rules, String host) {
            for (String rule : rules) {
                if (matchesRule(rule, host)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matchesRule(String rule, String host) {
            if (rule == null || rule.isEmpty()) {
                return false;
            }
            if (rule.startsWith("*.")) {
                String suffix = rule.substring(2);
                return host.equals(suffix) || host.endsWith("." + suffix);
            }
            return host.equals(rule);
        }

        private static String normalize(String value) {
            return value.trim().toLowerCase(Locale.US);
        }

        public static final class Builder {
            private final Set<String> hostWhitelist = new LinkedHashSet<>();
            private final Set<String> hostBlacklist = new LinkedHashSet<>();
            private boolean allowByDefault = true;

            public Builder allowByDefault(boolean allowByDefault) {
                this.allowByDefault = allowByDefault;
                return this;
            }

            public Builder addWhitelistHost(String host) {
                if (host != null && !host.trim().isEmpty()) {
                    hostWhitelist.add(normalize(host));
                }
                return this;
            }

            public Builder addBlacklistHost(String host) {
                if (host != null && !host.trim().isEmpty()) {
                    hostBlacklist.add(normalize(host));
                }
                return this;
            }

            public Builder addWhitelistHosts(String... hosts) {
                if (hosts == null) {
                    return this;
                }
                for (String host : hosts) {
                    addWhitelistHost(host);
                }
                return this;
            }

            public Builder addBlacklistHosts(String... hosts) {
                if (hosts == null) {
                    return this;
                }
                for (String host : hosts) {
                    addBlacklistHost(host);
                }
                return this;
            }

            public Http3Policy build() {
                return new Http3Policy(this);
            }
        }
    }

    public interface Http3PolicyConfigurer {
        void configure(Http3Policy.Builder builder);
    }

    public interface OkHttpClientBuilderConfigurer {
        void configure(OkHttpClient.Builder builder);
    }


    public Executor getDelivery() {
        return mPlatform.defaultCallbackExecutor();
    }

    public OkHttpClient getOkHttpClient() {
        return mOkHttpClient;
    }

    public static GetBuilder get() {
        return new GetBuilder();
    }

    public static PostStringBuilder postString() {
        return new PostStringBuilder();
    }

    public static PostFileBuilder postFile() {
        return new PostFileBuilder();
    }

    public static PostFormBuilder post() {
        return new PostFormBuilder();
    }

    public static OtherRequestBuilder put() {
        return new OtherRequestBuilder(METHOD.PUT);
    }

    public static HeadBuilder head() {
        return new HeadBuilder();
    }

    public static OtherRequestBuilder delete() {
        return new OtherRequestBuilder(METHOD.DELETE);
    }

    public static OtherRequestBuilder patch() {
        return new OtherRequestBuilder(METHOD.PATCH);
    }

    public void execute(final RequestCall requestCall, Callback callback) {
        if (callback == null)
            callback = Callback.CALLBACK_DEFAULT;
        final Callback finalCallback = callback;
        final int id = requestCall.getOkHttpRequest().getId();

        requestCall.getCall().enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                sendFailResultCallback(call, e, finalCallback, id);
            }

            @Override
            public void onResponse(final Call call, final Response response) {
                try {
                    if (call.isCanceled()) {
                        sendFailResultCallback(call, new IOException("Canceled!"), finalCallback, id);
                        return;
                    }

                    if (!finalCallback.validateReponse(response, id)) {
                        if (response.body() != null) {
                            String errorBody = response.body().string();
                            sendFailResultCallback(call, new IOException(errorBody), finalCallback, id);
                        } else {
                            sendFailResultCallback(call, new IOException("request failed , response's code is : " + response.code()), finalCallback, id);
                        }
                        return;
                    }

                    Object o = finalCallback.parseNetworkResponse(response, id);
                    sendSuccessResultCallback(o, finalCallback, id);
                } catch (Exception e) {
                    sendFailResultCallback(call, e, finalCallback, id);
                } finally {
                    if (response.body() != null)
                        response.body().close();
                }

            }
        });
    }


    public void sendFailResultCallback(final Call call, final Exception e, final Callback callback, final int id) {
        if (callback == null) return;

        mPlatform.execute(() -> {
            callback.onError(call, e, id);
            callback.onAfter(id);
        });
    }

    public void sendSuccessResultCallback(final Object object, final Callback callback, final int id) {
        if (callback == null) return;
        mPlatform.execute(() -> {
            callback.onResponse(object, id);
            callback.onAfter(id);
        });
    }

    public void cancelTag(Object tag) {
        for (Call call : mOkHttpClient.dispatcher().queuedCalls()) {
            if (tag.equals(call.request().tag())) {
                call.cancel();
            }
        }
        for (Call call : mOkHttpClient.dispatcher().runningCalls()) {
            if (tag.equals(call.request().tag())) {
                call.cancel();
            }
        }
    }

    public static class METHOD {
        public static final String HEAD = "HEAD";
        public static final String DELETE = "DELETE";
        public static final String PUT = "PUT";
        public static final String PATCH = "PATCH";
    }
}

