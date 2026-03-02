package com.zhy.http.okhttp.http3;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import okhttp3.CookieJar;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class CronetInterceptorTest {

    private Http3Engine engine;
    private CronetCallBridge bridge;
    private CookieJar cookieJar;
    private CronetInterceptor interceptor;
    private Request sampleRequest;

    @Before
    public void setUp() {
        engine = mock(Http3Engine.class);
        bridge = mock(CronetCallBridge.class);
        cookieJar = mock(CookieJar.class);
        interceptor = new CronetInterceptor(engine, cookieJar, bridge);
        sampleRequest = new Request.Builder()
                .url("https://example.com/test")
                .build();
    }

    @Test
    public void intercept_shouldUseCronet() throws Exception {
        when(engine.shouldUseCronet(any())).thenReturn(true);
        Response fakeResponse = new Response.Builder()
                .request(sampleRequest)
                .protocol(Protocol.HTTP_3)
                .code(200)
                .message("OK")
                .body(ResponseBody.create("hello".getBytes(), null))
                .build();
        when(bridge.execute(any())).thenReturn(fakeResponse);

        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(sampleRequest);

        Response result = interceptor.intercept(chain);
        assertNotNull(result);
        assertEquals(Protocol.HTTP_3, result.protocol());
        verify(chain, never()).proceed(any());
    }

    @Test
    public void intercept_shouldFallback() throws Exception {
        when(engine.shouldUseCronet(any())).thenReturn(false);
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(sampleRequest);
        Response fallback = new Response.Builder()
                .request(sampleRequest)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .body(ResponseBody.create("".getBytes(), null))
                .build();
        when(chain.proceed(sampleRequest)).thenReturn(fallback);

        Response result = interceptor.intercept(chain);
        assertEquals(fallback, result);
        verify(chain).proceed(sampleRequest);
        verify(bridge, never()).execute(any());
    }

    @Test
    public void intercept_exceptionFallsBack() throws Exception {
        when(engine.shouldUseCronet(any())).thenReturn(true);
        when(bridge.execute(any())).thenThrow(new IOException("forced failure"));

        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(sampleRequest);
        Response fallback = new Response.Builder()
                .request(sampleRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create("fallback".getBytes(), null))
                .build();
        when(chain.proceed(sampleRequest)).thenReturn(fallback);

        Response result = interceptor.intercept(chain);
        assertEquals(fallback, result);
        verify(chain).proceed(sampleRequest);
    }
}
