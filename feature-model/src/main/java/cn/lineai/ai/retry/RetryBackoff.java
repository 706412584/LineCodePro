package cn.lineai.ai.retry;

import java.util.Random;

/**
 * 重试退避计算（借鉴 cc-haha getRetryDelay）：500ms 基数指数退避、上限 32s、
 * +[0,25%) jitter；服务器下发 Retry-After 时优先服从（同样 cap 32s）。
 */
public final class RetryBackoff {

    public static final int MAX_REQUEST_RETRIES = 10;
    public static final int MAX_OVERLOAD_RETRIES = 3;
    public static final int MAX_STREAM_RETRIES = 4;
    public static final long STREAM_RETRY_BUDGET_MS = 60_000L;

    static final long BASE_DELAY_MS = 500L;
    static final long MAX_DELAY_MS = 32_000L;

    private RetryBackoff() {
    }

    /** attempt 从 0 计：第 1 次重试约 500-625ms，之后翻倍，cap 32s。 */
    public static long delayMs(int attempt, ModelApiError error, Random random) {
        long retryAfter = error == null ? 0 : error.retryAfterMs();
        if (retryAfter > 0) {
            return Math.min(retryAfter, MAX_DELAY_MS);
        }
        int safeAttempt = Math.max(0, attempt);
        double base = Math.min(BASE_DELAY_MS * Math.pow(2, safeAttempt), MAX_DELAY_MS);
        double jitterRatio = random == null ? 0 : random.nextDouble() * 0.25;
        return (long) (base * (1.0 + jitterRatio));
    }
}
