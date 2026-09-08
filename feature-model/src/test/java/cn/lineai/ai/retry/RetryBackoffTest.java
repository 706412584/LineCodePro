package cn.lineai.ai.retry;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

public final class RetryBackoffTest {

    @Test
    public void exponentialGrowthWithCap() {
        Random zero = new Random(0L);
        long d0 = RetryBackoff.delayMs(0, null, zero);
        Assert.assertTrue("attempt0 >= 500ms", d0 >= 500L && d0 < 625L);
        long d3 = RetryBackoff.delayMs(3, null, zero);
        Assert.assertTrue("attempt3 ~4s", d3 >= 4000L && d3 < 5000L);
        long d20 = RetryBackoff.delayMs(20, null, zero);
        Assert.assertTrue("cap 32s", d20 >= RetryBackoff.MAX_DELAY_MS && d20 <= RetryBackoff.MAX_DELAY_MS * 1.25 + 1);
    }

    @Test
    public void jitterStaysWithinQuarter() {
        Random r = new Random(42L);
        for (int i = 0; i < 50; i++) {
            long d = RetryBackoff.delayMs(1, null, r);
            Assert.assertTrue(d >= 1000L && d <= 1250L);
        }
    }

    @Test
    public void retryAfterOverridesBackoff() {
        ModelApiError error = new ModelApiError(ModelApiError.Kind.RATE_LIMIT, 429, 10_000L, "HTTP 429");
        long d = RetryBackoff.delayMs(0, error, new Random(1L));
        Assert.assertEquals(10_000L, d);
        // Retry-After 超大仍 cap 32s
        ModelApiError huge = new ModelApiError(ModelApiError.Kind.RATE_LIMIT, 429, 600_000L, "HTTP 429");
        Assert.assertEquals(RetryBackoff.MAX_DELAY_MS, RetryBackoff.delayMs(0, huge, new Random(1L)));
    }

    @Test
    public void negativeAttemptSafe() {
        long d = RetryBackoff.delayMs(-5, null, new Random(2L));
        Assert.assertTrue(d >= 500L);
    }
}
