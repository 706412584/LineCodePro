package cn.lineai.security;

import java.net.Proxy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public final class AppProxyTest {

    @After
    public void tearDown() {
        AppProxy.apply("", 0);
    }

    @Test
    public void disabledByDefaultYieldsNoProxy() {
        AppProxy.apply("", 0);
        Assert.assertFalse(AppProxy.isEnabled());
        Assert.assertEquals("", AppProxy.proxyUrl());
        Assert.assertSame(Proxy.NO_PROXY, AppProxy.proxyFor("api.example.com"));
    }

    @Test
    public void applyEnablesHttpProxyForRemoteHosts() {
        AppProxy.apply("127.0.0.1", 7890);
        Assert.assertTrue(AppProxy.isEnabled());
        Assert.assertEquals("http://127.0.0.1:7890", AppProxy.proxyUrl());
        Proxy proxy = AppProxy.proxyFor("api.openai.com");
        Assert.assertEquals(Proxy.Type.HTTP, proxy.type());
    }

    @Test
    public void localhostAlwaysBypassesProxy() {
        AppProxy.apply("10.0.0.1", 7890);
        Assert.assertSame(Proxy.NO_PROXY, AppProxy.proxyFor("localhost"));
        Assert.assertSame(Proxy.NO_PROXY, AppProxy.proxyFor("127.0.0.1"));
        Assert.assertSame(Proxy.NO_PROXY, AppProxy.proxyFor("::1"));
        Assert.assertSame(Proxy.NO_PROXY, AppProxy.proxyFor("10.0.2.2"));
        Assert.assertNotSame(Proxy.NO_PROXY, AppProxy.proxyFor("example.com"));
    }

    @Test
    public void invalidPortDisablesProxy() {
        AppProxy.apply("127.0.0.1", 0);
        Assert.assertFalse(AppProxy.isEnabled());
        AppProxy.apply("127.0.0.1", 70000);
        Assert.assertFalse(AppProxy.isEnabled());
        AppProxy.apply("   ", 7890);
        Assert.assertFalse(AppProxy.isEnabled());
    }
}
