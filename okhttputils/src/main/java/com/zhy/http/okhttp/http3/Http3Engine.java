package com.zhy.http.okhttp.http3;

import android.content.Context;
import android.util.Log;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.HttpUrl;

@SuppressWarnings("unused")
public class Http3Engine {
    // 日志标签，用于输出中文信息
    private static final String TAG = "Http3Engine";

    /** Background executor shared by all warmup requests (daemon thread – won't block app exit). */
    private static final Executor WARMUP_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cronet-warmup");
        t.setDaemon(true);
        return t;
    });
    private final CronetEngine cronetEngine;
    /** Exact-host entries (host + optional port). */
    private final List<HostEntry> exactEntries;
    /** Wildcard suffix rules, e.g. stored as "example.com" for "*.example.com". Port not supported for wildcards. */
    private final Set<String> wildcardSuffixes;
    /** Cache: "host:port" (real URL port) → decision boolean. */
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> decisionCache;

    private Http3Engine(Builder builder) {
        // 在构建引擎之前添加 QUIC hint，这样 Cronet 在第一次请求就尝试使用 QUIC，
        // 不需要等到 Alt-Svc 握手完成。
        CronetEngine.Builder engineBuilder = new CronetEngine.Builder(builder.context)
                .enableQuic(true)
                .enableHttp2(builder.enableHttp2);

        List<HostEntry> exact = new ArrayList<>();
        Set<String> suffixes = new LinkedHashSet<>();
        for (HostEntry entry : builder.hostRules) {
            if (entry.wildcard) {
                suffixes.add(entry.host);
            } else {
                exact.add(entry);
                int p = entry.port == -1 ? 443 : entry.port;
                engineBuilder.addQuicHint(entry.host, p, p);
            }
        }

        this.cronetEngine = engineBuilder.build();
        this.exactEntries = Collections.unmodifiableList(exact);
        this.wildcardSuffixes = Collections.unmodifiableSet(suffixes);
        this.decisionCache = new java.util.concurrent.ConcurrentHashMap<>(64);
    }

    public static Builder newBuilder(Context context) {
        return new Builder(context);
    }

    public CronetEngine engine() {
        return cronetEngine;
    }

    public boolean shouldUseCronet(HttpUrl url) {
        if (url == null || !url.isHttps()) {
            return false;
        }
        if (exactEntries.isEmpty() && wildcardSuffixes.isEmpty()) {
            return true;
        }
        String host = normalize(url.host());
        int port = url.port(); // HttpUrl always returns an explicit port (443 for https default)
        String cacheKey = host + ":" + port;
        Boolean cached = decisionCache.get(cacheKey);
        if (cached != null) return cached;

        boolean allowed = false;
        for (HostEntry entry : exactEntries) {
            if (entry.host.equals(host) && (entry.port == -1 || entry.port == port)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            for (String suffix : wildcardSuffixes) {
                if (host.equals(suffix) || host.endsWith("." + suffix)) {
                    allowed = true;
                    break;
                }
            }
        }
        decisionCache.put(cacheKey, allowed);
        return allowed;
    }

    public void shutdown() {
        cronetEngine.shutdown();
    }

    /**
     * Fires async HEAD requests to every configured exact host so Cronet can receive the
     * {@code Alt-Svc} header and cache QUIC support.  After this, all real requests to those
     * hosts will automatically use HTTP/3 (QUIC) instead of falling back to TCP.
     * <p>
     * Called automatically by {@link Builder#build()} when {@link Builder#autoWarmup(boolean)}
     * is {@code true} (the default).  Safe to call multiple times.
     */
    public void warmup() {
        if (exactEntries.isEmpty()) return;
        for (HostEntry entry : exactEntries) {
            int p = entry.port;
            String url = (p == -1 || p == 443)
                    ? "https://" + entry.host + "/"
                    : "https://" + entry.host + ":" + p + "/";
            fireWarmupRequest(url);
        }
    }

    private void fireWarmupRequest(String url) {
        try {
            UrlRequest request = cronetEngine
                    .newUrlRequestBuilder(url, new WarmupCallback(url), WARMUP_EXECUTOR)
                    .setHttpMethod("HEAD")
                    .build();
            request.start();
        } catch (Exception e) {
            Log.w(TAG, "Http3Engine warmup failed for " + url, e);
        }
    }

    private static final class WarmupCallback extends UrlRequest.Callback {
        private final String url;
        WarmupCallback(String url) { this.url = url; }

        @Override
        public void onRedirectReceived(UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
            request.followRedirect();
        }

        @Override
        public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
            // 收到响应头即可（触发 Alt-Svc 缓存），不需要读取 body
            request.cancel();
        }

        @Override
        public void onReadCompleted(UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) {
            request.cancel();
        }

        @Override
        public void onSucceeded(UrlRequest request, UrlResponseInfo info) {}

        @Override
        public void onFailed(UrlRequest request, UrlResponseInfo info, CronetException error) {
            Log.w(TAG, "Http3Engine warmup failed for " + url + ": " + error.getMessage());
        }

        @Override
        public void onCanceled(UrlRequest request, UrlResponseInfo info) {}
    }

    private static boolean matches(String rule, String host) {
        if (rule == null || host == null) return false;
        if (rule.startsWith("*.")) {
            String suffix = rule.substring(2);
            return host.equals(suffix) || host.endsWith("." + suffix);
        }
        return host.equals(rule);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.US);
    }

    /**
     * Represents a host rule: an exact hostname with an optional port.
     * <p>
     * {@code port == -1} means "any port".  {@code wildcard == true} means this is a
     * {@code *.suffix} rule (port is irrelevant for wildcards).
     */
    public static final class HostEntry {
        final String host;
        final int    port;     // -1 = any port
        final boolean wildcard;

        HostEntry(String host, int port, boolean wildcard) {
            this.host     = host;
            this.port     = port;
            this.wildcard = wildcard;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HostEntry)) return false;
            HostEntry e = (HostEntry) o;
            return port == e.port && wildcard == e.wildcard && Objects.equals(host, e.host);
        }

        @Override public int hashCode() {
            return Objects.hash(host, port, wildcard);
        }
    }

    public static final class Builder {
        private final Context context;
        private final List<HostEntry> hostRules = new ArrayList<>();
        private boolean enableHttp2 = true;
        private boolean autoWarmup = true;

        private Builder(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("context == null");
            }
            this.context = context.getApplicationContext();
        }

        public Builder enableHttp2(boolean enableHttp2) {
            this.enableHttp2 = enableHttp2;
            return this;
        }

        /**
         * Whether to automatically fire warmup HEAD requests to all configured hosts when
         * {@link #build()} is called.  Default: {@code true}.
         * <p>
         * Disable if you want to control the timing manually and call {@link Http3Engine#warmup()}
         * yourself.
         */
        public Builder autoWarmup(boolean autoWarmup) {
            this.autoWarmup = autoWarmup;
            return this;
        }

        /**
         * Add a host that should use HTTP/3 (QUIC), matching any port.
         * Supports wildcard prefix: {@code "*.example.com"}.
         */
        public Builder addHost(String host) {
            if (host == null || host.trim().isEmpty()) return this;
            host = host.trim().toLowerCase(Locale.US);
            if (host.startsWith("*.")) {
                hostRules.add(new HostEntry(host.substring(2), -1, true));
            } else {
                hostRules.add(new HostEntry(host, -1, false));
            }
            return this;
        }

        /**
         * Add a host + specific port that should use HTTP/3 (QUIC).
         * Only requests whose URL port exactly matches {@code port} will be intercepted.
         */
        public Builder addHost(String host, int port) {
            if (host == null || host.trim().isEmpty()) return this;
            if (port <= 0 || port > 65535) throw new IllegalArgumentException("invalid port: " + port);
            hostRules.add(new HostEntry(host.trim().toLowerCase(Locale.US), port, false));
            return this;
        }

        /** Convenience: add multiple hosts (any port). Supports wildcard prefix. */
        public Builder addHosts(String... hosts) {
            if (hosts == null) return this;
            for (String host : hosts) addHost(host);
            return this;
        }

        public Http3Engine build() {
            Http3Engine engine = new Http3Engine(this);
            if (autoWarmup) {
                engine.warmup();
            }
            return engine;
        }
    }
}
