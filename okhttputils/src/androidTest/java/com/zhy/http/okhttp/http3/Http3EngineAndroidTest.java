package com.zhy.http.okhttp.http3;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Http3EngineAndroidTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void hostMatching_defaultAllowsHttps() {
        Http3Engine engine = Http3Engine.newBuilder(context).build();
        assertTrue(engine.shouldUseCronet(HttpUrl.parse("https://example.com/")));
        assertFalse(engine.shouldUseCronet(HttpUrl.parse("http://example.com/")));
    }

    @Test
    public void hostMatching_whitelistOnly() {
        Http3Engine engine = Http3Engine.newBuilder(context)
                .addHost("sub.example.com")
                .build();
        assertTrue(engine.shouldUseCronet(HttpUrl.parse("https://sub.example.com/api")));
        assertFalse(engine.shouldUseCronet(HttpUrl.parse("https://example.com/")));
    }

    @Test
    public void hostMatching_patternWildcard() {
        Http3Engine engine = Http3Engine.newBuilder(context)
                .addHost("*.example.org")
                .build();
        assertTrue(engine.shouldUseCronet(HttpUrl.parse("https://api.example.org/")));
        assertTrue(engine.shouldUseCronet(HttpUrl.parse("https://example.org/")));
        assertFalse(engine.shouldUseCronet(HttpUrl.parse("https://evil.com/")));
    }
}
