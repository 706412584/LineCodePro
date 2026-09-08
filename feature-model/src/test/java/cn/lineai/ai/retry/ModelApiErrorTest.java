package cn.lineai.ai.retry;

import org.junit.Assert;
import org.junit.Test;

import java.net.SocketTimeoutException;

public final class ModelApiErrorTest {

    @Test
    public void httpStatusClassification() {
        Assert.assertEquals(ModelApiError.Kind.RATE_LIMIT,
                ModelApiError.fromThrowable(new Exception("HTTP 429: too many")).kind());
        Assert.assertEquals(ModelApiError.Kind.SERVER_OVERLOAD,
                ModelApiError.fromThrowable(new Exception("HTTP 529: overloaded")).kind());
        Assert.assertEquals(ModelApiError.Kind.AUTH,
                ModelApiError.fromThrowable(new Exception("HTTP 401: bad key")).kind());
        Assert.assertEquals(ModelApiError.Kind.AUTH,
                ModelApiError.fromThrowable(new Exception("HTTP 403: forbidden")).kind());
        Assert.assertEquals(ModelApiError.Kind.TIMEOUT,
                ModelApiError.fromThrowable(new Exception("HTTP 408: timeout")).kind());
        Assert.assertEquals(ModelApiError.Kind.CLIENT_ERROR,
                ModelApiError.fromThrowable(new Exception("HTTP 400: bad request")).kind());
        Assert.assertEquals(ModelApiError.Kind.SERVER_ERROR,
                ModelApiError.fromThrowable(new Exception("HTTP 503: upstream")).kind());
        Assert.assertEquals(ModelApiError.Kind.SERVER_ERROR,
                ModelApiError.fromThrowable(new Exception("HTTP 500: boom")).kind());
    }

    @Test
    public void causeChainClassification() {
        Assert.assertEquals(ModelApiError.Kind.CONNECTION_ERROR,
                ModelApiError.fromThrowable(new Exception("wrap", new java.net.SocketException("Connection reset"))).kind());
        Assert.assertEquals(ModelApiError.Kind.CONNECTION_ERROR,
                ModelApiError.fromThrowable(new Exception("wrap", new java.io.IOException("Broken pipe"))).kind());
        Assert.assertEquals(ModelApiError.Kind.TIMEOUT,
                ModelApiError.fromThrowable(new Exception("wrap", new SocketTimeoutException("Read timed out"))).kind());
    }

    @Test
    public void inStreamOverloadedEvent() {
        // 200 SSE body 内 error 事件：无 HTTP 前缀，message 带 overloaded_error
        ModelApiError error = ModelApiError.fromThrowable(new Exception("Model stream communication failed: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}"));
        Assert.assertEquals(ModelApiError.Kind.SERVER_OVERLOAD, error.kind());
        Assert.assertTrue(error.retryable());
    }

    @Test
    public void watchdogOverridesConnectionError() {
        ModelApiError error = ModelApiError.fromThrowable(
                new java.net.SocketException("Socket closed"), true);
        Assert.assertEquals(ModelApiError.Kind.WATCHDOG_IDLE, error.kind());
        Assert.assertTrue(ModelApiError.retryable(ModelApiError.Kind.WATCHDOG_IDLE));
    }

    @Test
    public void retryableMatrix() {
        Assert.assertTrue(ModelApiError.retryable(ModelApiError.Kind.RATE_LIMIT));
        Assert.assertTrue(ModelApiError.retryable(ModelApiError.Kind.SERVER_OVERLOAD));
        Assert.assertTrue(ModelApiError.retryable(ModelApiError.Kind.SERVER_ERROR));
        Assert.assertTrue(ModelApiError.retryable(ModelApiError.Kind.TIMEOUT));
        Assert.assertTrue(ModelApiError.retryable(ModelApiError.Kind.CONNECTION_ERROR));
        Assert.assertTrue(ModelApiError.retryable(ModelApiError.Kind.WATCHDOG_IDLE));
        Assert.assertFalse(ModelApiError.retryable(ModelApiError.Kind.AUTH));
        Assert.assertFalse(ModelApiError.retryable(ModelApiError.Kind.CLIENT_ERROR));
        Assert.assertFalse(ModelApiError.retryable(ModelApiError.Kind.CANCELLED));
        Assert.assertFalse(ModelApiError.retryable(ModelApiError.Kind.UNKNOWN));
    }

    @Test
    public void extractHttpStatusPrefix() {
        Assert.assertEquals(429, ModelApiError.extractHttpStatus("HTTP 429: x"));
        Assert.assertEquals(-1, ModelApiError.extractHttpStatus("no prefix"));
        Assert.assertEquals(-1, ModelApiError.extractHttpStatus("HTTP abc"));
    }
}
