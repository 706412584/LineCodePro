package cn.lineai.ai.retry;

import org.junit.Assert;
import org.junit.Test;

public final class ModelRetryDecisionTest {

    @Test
    public void transientErrorsRetry() {
        for (ModelApiError.Kind kind : new ModelApiError.Kind[]{
                ModelApiError.Kind.RATE_LIMIT, ModelApiError.Kind.SERVER_ERROR,
                ModelApiError.Kind.TIMEOUT, ModelApiError.Kind.CONNECTION_ERROR,
                ModelApiError.Kind.WATCHDOG_IDLE}) {
            Assert.assertEquals(ModelRetryDecision.Action.RETRY,
                    ModelRetryDecision.evaluate(kind, 0, 0, false, false).action());
        }
    }

    @Test
    public void deterministicErrorsFail() {
        for (ModelApiError.Kind kind : new ModelApiError.Kind[]{
                ModelApiError.Kind.AUTH, ModelApiError.Kind.CLIENT_ERROR,
                ModelApiError.Kind.CANCELLED, ModelApiError.Kind.UNKNOWN}) {
            Assert.assertEquals(ModelRetryDecision.Action.FAIL,
                    ModelRetryDecision.evaluate(kind, 0, 0, false, false).action());
            // 有 partial 也只提交不重试
            Assert.assertEquals(ModelRetryDecision.Action.COMMIT_PARTIAL_AND_FAIL,
                    ModelRetryDecision.evaluate(kind, 0, 0, false, true).action());
        }
    }

    @Test
    public void toolBoundaryNeverRetries() {
        Assert.assertEquals(ModelRetryDecision.Action.FAIL,
                ModelRetryDecision.evaluate(ModelApiError.Kind.CONNECTION_ERROR, 0, 0, true, false).action());
        Assert.assertEquals(ModelRetryDecision.Action.COMMIT_PARTIAL_AND_FAIL,
                ModelRetryDecision.evaluate(ModelApiError.Kind.CONNECTION_ERROR, 0, 0, true, true).action());
        // 即使第一次尝试、错误可重试，越界即不可重发
        Assert.assertEquals(ModelRetryDecision.Action.FAIL,
                ModelRetryDecision.evaluate(ModelApiError.Kind.RATE_LIMIT, 0, 0, true, false).action());
    }

    @Test
    public void overloadLimitThree() {
        Assert.assertEquals(ModelRetryDecision.Action.RETRY,
                ModelRetryDecision.evaluate(ModelApiError.Kind.SERVER_OVERLOAD, 0, 2, false, false).action());
        Assert.assertEquals(ModelRetryDecision.Action.FAIL,
                ModelRetryDecision.evaluate(ModelApiError.Kind.SERVER_OVERLOAD, 0, 3, false, false).action());
    }

    @Test
    public void requestLimitTen() {
        Assert.assertEquals(ModelRetryDecision.Action.RETRY,
                ModelRetryDecision.evaluate(ModelApiError.Kind.TIMEOUT, 9, 0, false, false).action());
        Assert.assertEquals(ModelRetryDecision.Action.FAIL,
                ModelRetryDecision.evaluate(ModelApiError.Kind.TIMEOUT, 10, 0, false, false).action());
    }

    @Test
    public void partialCommitsThenRetries() {
        ModelRetryDecision decision = ModelRetryDecision.evaluate(
                ModelApiError.Kind.CONNECTION_ERROR, 1, 0, false, true);
        Assert.assertEquals(ModelRetryDecision.Action.COMMIT_PARTIAL_AND_RETRY, decision.action());
        Assert.assertTrue(decision.commitPartial());
    }
}
