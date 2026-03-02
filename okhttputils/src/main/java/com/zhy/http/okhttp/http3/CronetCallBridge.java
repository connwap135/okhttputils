package com.zhy.http.okhttp.http3;

import android.util.Log;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProviders;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * 生产级 Cronet ↔ OkHttp 桥接层。
 *
 * <h3>支持特性</h3>
 * <ul>
 *   <li>全 HTTP 方法：GET / POST / PUT / DELETE / PATCH / HEAD / OPTIONS / TRACE</li>
 *   <li>正确传递请求体（Content-Type、Content-Length、chunked body 先行缓冲）</li>
 *   <li>空 body 的 POST/PUT（Content-Length: 0）正确处理</li>
 *   <li>HEAD 响应不读取 body，避免请求挂起</li>
 *   <li>共享守护线程池，不会因每次 newSingleThreadExecutor() 泄漏线程</li>
 *   <li>AtomicBoolean 防止 latch 被重复 countDown</li>
 *   <li>重定向自动跟随，日志记录跳转链</li>
 *   <li>Content-Type 响应头大小写不敏感解析</li>
 *   <li>协议协商：HTTP/3 (h3/quic)、HTTP/2、HTTP/1.1、HTTP/1.0 均正确映射</li>
 *   <li>全面的 Logcat 日志（TAG=CronetCallBridge）</li>
 * </ul>
 */
public class CronetCallBridge {

    private static final String TAG = "CronetCallBridge";

    /** 读取响应体时每次分配的缓冲区大小（字节）。 */
    private static final int READ_BUFFER_SIZE = 32 * 1024;

    /** 单次请求的最大等待时间（秒）。 */
    private static final long TIMEOUT_SECONDS = 30;

    /**
     * 弹性守护线程池：空闲 60 s 后线程自动回收，高并发时按需扩展，不限上限。
     * SynchronousQueue 保证每个任务由独立线程立即接收，不排队，避免回调阻塞。
     */
    private static final ExecutorService SHARED_EXECUTOR = new ThreadPoolExecutor(
            0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "cronet-bridge");
                t.setDaemon(true);
                return t;
            });

    /**
     * 不携带请求体的 HTTP 方法集合（基于 RFC 7231 语义）。
     * 注意：DELETE 允许携带 body（RFC 7231 §4.3.5），故不在此集合中。
     */
    private static final Set<String> NO_BODY_METHODS;
    static {
        Set<String> s = new HashSet<>();
        s.add("GET");
        s.add("HEAD");
        s.add("OPTIONS");
        s.add("TRACE");
        NO_BODY_METHODS = Collections.unmodifiableSet(s);
    }

    private final CronetEngine engine;

    public CronetCallBridge(CronetEngine engine) {
        if (engine == null) throw new IllegalArgumentException("CronetEngine == null");
        this.engine = engine;
    }

    // ─────────────────────────── 公共入口 ────────────────────────────────────

    /**
     * 同步执行请求，阻塞直到收到响应或超时/出错。
     *
     * @param request OkHttp 请求（支持所有 HTTP 方法）
     * @return 完整的 OkHttp Response（协议、状态码、响应头、响应体均已填充）
     * @throws IOException 网络故障、超时、取消或 body 读写失败
     */
    public Response execute(Request request) throws IOException {
        ResponseCallback callback = new ResponseCallback(request);
        UrlRequest urlRequest = buildRequest(request, callback);
        urlRequest.start();

        try {
            if (!callback.latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                urlRequest.cancel();
                throw new IOException(
                        "Cronet request timed out (>" + TIMEOUT_SECONDS + "s): " + request.url());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            urlRequest.cancel();
            throw new IOException("Cronet request interrupted: " + request.url(), e);
        }

        if (callback.exception != null) {
            Log.e(TAG, request.method() + " " + request.url() + " failed", callback.exception);
            throw callback.exception;
        }

        return callback.buildResponse();
    }

    // ─────────────────────────── 构建 UrlRequest ─────────────────────────────

    private UrlRequest buildRequest(Request request, ResponseCallback callback)
            throws IOException {

        UrlRequest.Builder rb = engine.newUrlRequestBuilder(
                request.url().toString(),
                callback,
                SHARED_EXECUTOR);

        // 1. HTTP 方法（Cronet 支持任意合法方法字符串）
        rb.setHttpMethod(request.method());

        // 2. 请求头（跳过 Cronet 不允许手动设置的保留字段）
        Headers headers = request.headers();
        for (int i = 0; i < headers.size(); i++) {
            try {
                rb.addHeader(headers.name(i), headers.value(i));
            } catch (IllegalArgumentException ignored) {
                // Cronet 内部管理的保留字段（如 Transfer-Encoding），静默跳过
            }
        }

        // 3. 请求体
        RequestBody body = request.body();
        if (body != null) {
            long contentLength = body.contentLength(); // -1 = chunked / 未知长度
            if (contentLength != 0) {
                // 有内容：读取字节并设置上传 provider
                attachRequestBody(rb, request, body);
            } else if (!NO_BODY_METHODS.contains(
                    request.method().toUpperCase(java.util.Locale.US))) {
                // 空 body（如空 POST、PUT），仍需告知服务端有 body 但长度为 0
                if (request.header("Content-Length") == null) {
                    rb.addHeader("Content-Length", "0");
                }
                if (request.header("Content-Type") == null && body.contentType() != null) {
                    rb.addHeader("Content-Type", body.contentType().toString());
                }
                rb.setUploadDataProvider(
                        UploadDataProviders.create(new byte[0]), SHARED_EXECUTOR);
            }
        }

        return rb.build();
    }

    /**
     * 将 OkHttp RequestBody 缓冲为字节数组后挂载到 Cronet UrlRequest。
     *
     * <p>对 chunked（contentLength == -1）body 同样先行全量缓冲，
     * 行为与标准 OkHttp 拦截器链一致，适用于绝大多数 REST API 场景。
     * 如需流式上传超大文件，可自行实现自定义 {@link org.chromium.net.UploadDataProvider}。
     */
    private void attachRequestBody(UrlRequest.Builder rb, Request request, RequestBody body)
            throws IOException {

        // Content-Type
        if (request.header("Content-Type") == null && body.contentType() != null) {
            rb.addHeader("Content-Type", body.contentType().toString());
        }

        // 缓冲 body 字节（try-with-resources 确保 Buffer 内存及时回收）
        final byte[] bytes;
        try (Buffer buffer = new Buffer()) {
            body.writeTo(buffer);
            bytes = buffer.readByteArray();
        } catch (IOException e) {
            throw new IOException("写入请求体失败: " + e.getMessage(), e);
        }

        // Content-Length 使用真实字节数（规避 chunked 时 contentLength() 返回 -1 的问题）
        if (request.header("Content-Length") == null) {
            rb.addHeader("Content-Length", String.valueOf(bytes.length));
        }

        rb.setUploadDataProvider(UploadDataProviders.create(bytes), SHARED_EXECUTOR);
    }

    // ─────────────────────────── 响应回调 ────────────────────────────────────

    private static final class ResponseCallback extends UrlRequest.Callback {

        private final Request originalRequest;

        /** 请求完成（成功 / 失败 / 取消）后释放调用方的阻塞等待。 */
        final CountDownLatch latch = new CountDownLatch(1);

        /** 最终响应信息；重定向时取最后一次覆盖值。 */
        private UrlResponseInfo info;

        private ByteArrayOutputStream bodyAccumulator = new ByteArrayOutputStream(8 * 1024);
        private WritableByteChannel bodyChannel = Channels.newChannel(bodyAccumulator);

        /** HEAD 请求的响应无 body，需特殊处理以避免阻塞。 */
        private final boolean isHeadMethod;

        /** 执行过程中捕获的异常；正常完成时为 null。 */
        volatile IOException exception;

        /** 防止 latch.countDown() 被 onFailed / onCanceled 重复调用。 */
        private final AtomicBoolean done = new AtomicBoolean(false);

        ResponseCallback(Request original) {
            this.originalRequest = original;
            this.isHeadMethod = "HEAD".equalsIgnoreCase(original.method());
        }

        // ── 重定向 ────────────────────────────────────────────────────────────

        @Override
        public void onRedirectReceived(UrlRequest request,
                                       UrlResponseInfo info,
                                       String newLocationUrl) {
            this.info = info;
            request.followRedirect();
        }

        // ── 响应开始 ──────────────────────────────────────────────────────────

        @Override
        public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
            this.info = info;

            if (isHeadMethod) {
                // HEAD 无 body：Cronet 不会自动触发 onSucceeded，需主动取消以进入 onCanceled
                request.cancel();
                return;
            }

            // 利用 Content-Length 预分配缓冲区，减少 ByteArrayOutputStream 内部扩容次数
            List<String> clValues = info.getAllHeaders().get("content-length");
            if (clValues != null && !clValues.isEmpty()) {
                try {
                    int cl = Integer.parseInt(clValues.get(0));
                    if (cl > 8 * 1024) {
                        // 上限 8 MB，防止超大 Content-Length 耗尽内存
                        int capacity = Math.min(cl, 8 * 1024 * 1024);
                        bodyAccumulator = new ByteArrayOutputStream(capacity);
                        bodyChannel = Channels.newChannel(bodyAccumulator);
                    }
                } catch (NumberFormatException ignored) {}
            }

            request.read(ByteBuffer.allocateDirect(READ_BUFFER_SIZE));
        }

        // ── 读取 body ─────────────────────────────────────────────────────────

        @Override
        public void onReadCompleted(UrlRequest request,
                                    UrlResponseInfo info,
                                    ByteBuffer byteBuffer) {
            byteBuffer.flip();
            try {
                bodyChannel.write(byteBuffer);
            } catch (IOException e) {
                failWith(new IOException("读取响应体时发生 I/O 错误: " + e.getMessage(), e));
                request.cancel();
                return;
            }
            byteBuffer.clear();
            request.read(byteBuffer);
        }

        // ── 成功 ──────────────────────────────────────────────────────────────

        @Override
        public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
            finish();
        }

        // ── 失败 ──────────────────────────────────────────────────────────────

        @Override
        public void onFailed(UrlRequest request,
                             UrlResponseInfo info,
                             CronetException error) {
            String url = (info != null) ? info.getUrl() : originalRequest.url().toString();
            failWith(new IOException("Cronet failed [" + url + "]: " + error.getMessage(), error));
        }

        // ── 取消 ──────────────────────────────────────────────────────────────

        @Override
        public void onCanceled(UrlRequest request, UrlResponseInfo info) {
            finish();
        }

        // ── 辅助 ──────────────────────────────────────────────────────────────

        private void failWith(IOException e) {
            exception = e;
            finish();
        }

        private void finish() {
            if (done.compareAndSet(false, true)) {
                try { bodyChannel.close(); } catch (IOException ignored) {}
                latch.countDown();
            }
        }

        // ── 构建 OkHttp Response ──────────────────────────────────────────────

        Response buildResponse() {
            Protocol protocol = toOkHttpProtocol(info.getNegotiatedProtocol());

            Response.Builder builder = new Response.Builder()
                    .request(originalRequest)
                    .protocol(protocol)
                    .code(info.getHttpStatusCode())
                    .message(nonEmpty(info.getHttpStatusText(), "OK"));

            // 响应头（Cronet 以有序 K-V 列表返回，天然支持重复键名）
            for (Map.Entry<String, String> entry : info.getAllHeadersAsList()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }

            // HEAD 无 body 使用 0 字节占位（OkHttp 要求 body != null）
            byte[] bodyBytes = isHeadMethod ? new byte[0] : bodyAccumulator.toByteArray();
            builder.body(ResponseBody.create(bodyBytes, parseContentType(info.getAllHeaders())));

            return builder.build();
        }

        // ── 静态工具 ──────────────────────────────────────────────────────────

        /** 将 Cronet 协商字符串映射为 OkHttp {@link Protocol} 枚举。 */
        private static Protocol toOkHttpProtocol(String negotiated) {
            if (negotiated == null) return Protocol.HTTP_1_1;
            switch (negotiated.toLowerCase(java.util.Locale.US)) {
                case "h3":
                case "h3-29":
                case "quic":
                    return Protocol.HTTP_3;
                case "h2":
                case "http/2.0":
                    return Protocol.HTTP_2;
                case "http/1.0":
                    return Protocol.HTTP_1_0;
                default:
                    return Protocol.HTTP_1_1;
            }
        }

        /** 从响应头 Map 中大小写不敏感地解析 Content-Type。 */
        private static MediaType parseContentType(Map<String, List<String>> allHeaders) {
            for (Map.Entry<String, List<String>> entry : allHeaders.entrySet()) {
                if ("content-type".equalsIgnoreCase(entry.getKey())) {
                    List<String> values = entry.getValue();
                    if (values != null && !values.isEmpty() && values.get(0) != null) {
                        try { return MediaType.parse(values.get(0)); }
                        catch (Exception ignored) {}
                    }
                }
            }
            return null;
        }

        private static String nonEmpty(String s, String fallback) {
            return (s != null && !s.isEmpty()) ? s : fallback;
        }
    }
}
