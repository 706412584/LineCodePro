package cn.lineai.ai.retry;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class StreamWatchdogTest {

    @Test
    public void firesOnIdle() throws Exception {
        StreamWatchdog watchdog = new StreamWatchdog(1100L);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StreamWatchdog.Reason> reason = new AtomicReference<>();
        watchdog.start(r -> {
            reason.set(r);
            latch.countDown();
        });
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(StreamWatchdog.Reason.FIRST_TOKEN, reason.get());
        Assert.assertTrue(watchdog.fired());
        watchdog.stop();
    }

    @Test
    public void activityResetsAndSwitchesToIdleReason() throws Exception {
        StreamWatchdog watchdog = new StreamWatchdog(1100L);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StreamWatchdog.Reason> reason = new AtomicReference<>();
        watchdog.start(r -> {
            reason.set(r);
            latch.countDown();
        });
        // 持续活动 2.5s（> 超时阈值），不应触发 FIRST_TOKEN
        for (int i = 0; i < 25; i++) {
            watchdog.onActivity();
            Thread.sleep(100L);
        }
        Assert.assertFalse(watchdog.fired());
        // 停止活动，等待触发，reason 应为 IDLE
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(StreamWatchdog.Reason.IDLE, reason.get());
        watchdog.stop();
    }

    @Test
    public void stopPreventsFire() throws Exception {
        StreamWatchdog watchdog = new StreamWatchdog(1100L);
        watchdog.start(r -> Assert.fail("should not fire after stop"));
        watchdog.stop();
        Thread.sleep(1600L);
        Assert.assertFalse(watchdog.fired());
    }
}
