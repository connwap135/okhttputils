package com.zhy.http.okhttp.http3;

import android.util.Log;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Replaces the buggy {@code cronet-okhttp:0.1.1} bridge.
 *
 * <p>Uses Cronet's native {@link UrlRequest} API directly to execute an HTTP
 * request and converts the result to an {@link okhttp3.Response} while
 * correctly handling null/empty bodies (a crash case in the old bridge).
 */
public class CronetCallBridge {

    private static final String TAG = "CronetCallBridge";
    private static final int BUFFER_SIZE = 32 * 1024;
    private static final long TIMEOUT_SECONDS = 30;

    private final CronetEngine engine;

    public CronetCallBridge(CronetEngine engine) {
        this.engine = engine;
    }

    public Response execute(Request request) throws IOException {
        ResponseCallback callback = new ResponseCallback(request);
        UrlRequest urlRequest = buildRequest(request, callback);
        urlRequest.start();
        try {
            if (!callback.latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                urlRequest.cancel();
                throw new IOException("Cronet request timed out after " + TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            urlRequest.cancel();
            throw new IOException("Cronet request interrupted", e);
        }
        if (callback.exception != null) {
            throw callback.exception;
        }
        return callback.buildResponse();
    }

    private UrlRequest buildRequest(Request request, ResponseCallback callback) {
        UrlRequest.Builder rb = engine.newUrlRequestBuilder(
                request.url().toString(),
                callback,
                Executors.newSingleThreadExecutor());

        rb.setHttpMethod(request.method());

        Headers headers = request.headers();
        for (int i = 0; i < headers.size(); i++) {
            rb.addHeader(headers.name(i), headers.value(i));
        }

        return rb.build();
    }

    // ────────────────────────────────────────────────────────────────────────

    private static final class ResponseCallback extends UrlRequest.Callback {

        private final Request originalRequest;
        final CountDownLatch latch = new CountDownLatch(1);

        private UrlResponseInfo info;
        private final ByteArrayOutputStream bodyAccumulator = new ByteArrayOutputStream();
        private final WritableByteChannel bodyChannel =
                Channels.newChannel(bodyAccumulator);
        IOException exception;

        ResponseCallback(Request original) {
            this.originalRequest = original;
        }

        @Override
        public void onRedirectReceived(UrlRequest request,
                                       UrlResponseInfo info, String newLocationUrl) {
            // follow redirects via Cronet automatically
            this.info = info;
            request.followRedirect();
        }

        @Override
        public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
            this.info = info;
            request.read(ByteBuffer.allocateDirect(BUFFER_SIZE));
        }

        @Override
        public void onReadCompleted(UrlRequest request, UrlResponseInfo info,
                                    ByteBuffer byteBuffer) {
            byteBuffer.flip();
            try {
                bodyChannel.write(byteBuffer);
            } catch (IOException e) {
                exception = e;
                request.cancel();
                latch.countDown();
                return;
            }
            byteBuffer.clear();
            request.read(byteBuffer);
        }

        @Override
        public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
            latch.countDown();
        }

        @Override
        public void onFailed(UrlRequest request, UrlResponseInfo info,
                             CronetException error) {
            exception = new IOException("Cronet request failed: " + error.getMessage(), error);
            latch.countDown();
        }

        @Override
        public void onCanceled(UrlRequest request, UrlResponseInfo info) {
            if (exception == null) {
                exception = new IOException("Cronet request was cancelled");
            }
            latch.countDown();
        }

        Response buildResponse() {
            Protocol protocol = toOkHttpProtocol(info.getNegotiatedProtocol());

            Response.Builder builder = new Response.Builder()
                    .request(originalRequest)
                    .protocol(protocol)
                    .code(info.getHttpStatusCode())
                    .message(info.getHttpStatusText());

            // Add response headers
            for (Map.Entry<String, String> entry : info.getAllHeadersAsList()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }

            // Build body – never null (fixes the OkHttp 5 crash)
            byte[] bodyBytes = bodyAccumulator.toByteArray();
            MediaType mediaType = null;
            List<String> ctList = info.getAllHeaders().get("content-type");
            if (ctList != null && !ctList.isEmpty()) {
                mediaType = MediaType.parse(ctList.get(0));
            }
            builder.body(ResponseBody.create(bodyBytes, mediaType));

            return builder.build();
        }

        private Protocol toOkHttpProtocol(String negotiated) {
            if (negotiated == null) return Protocol.HTTP_1_1;
            String lower = negotiated.toLowerCase();
            if (lower.startsWith("h3") || lower.equals("quic")) return Protocol.HTTP_3;
            if (lower.equals("h2"))                                return Protocol.HTTP_2;
            return Protocol.HTTP_1_1;
        }
    }
}
