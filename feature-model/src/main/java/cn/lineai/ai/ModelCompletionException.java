package cn.lineai.ai;

import cn.lineai.ai.retry.ModelApiError;

public final class ModelCompletionException extends Exception {
    private int httpStatus = -1;
    private ModelApiError.Kind kind = ModelApiError.Kind.UNKNOWN;
    private long retryAfterMs = 0;
    private String partialText;
    private String partialReasoning;
    private boolean crossedToolBoundary;
    private boolean watchdogFired;

    public ModelCompletionException(String message) {
        super(message);
    }

    public ModelCompletionException(String message, Throwable cause) {
        super(message, cause);
    }

    public int httpStatus() {
        return httpStatus;
    }

    public ModelCompletionException withHttpStatus(int status) {
        this.httpStatus = status;
        return this;
    }

    public ModelApiError.Kind kind() {
        return kind;
    }

    public ModelCompletionException withKind(ModelApiError.Kind kind) {
        this.kind = kind;
        return this;
    }

    public long retryAfterMs() {
        return retryAfterMs;
    }

    public ModelCompletionException withRetryAfterMs(long retryAfterMs) {
        this.retryAfterMs = retryAfterMs;
        return this;
    }

    public String partialText() {
        return partialText;
    }

    public String partialReasoning() {
        return partialReasoning;
    }

    public ModelCompletionException withPartial(String text, String reasoning, boolean crossedToolBoundary) {
        this.partialText = text == null ? "" : text;
        this.partialReasoning = reasoning == null ? "" : reasoning;
        this.crossedToolBoundary = crossedToolBoundary;
        return this;
    }

    public boolean hasPartial() {
        return (partialText != null && partialText.length() > 0)
                || (partialReasoning != null && partialReasoning.length() > 0);
    }

    public boolean crossedToolBoundary() {
        return crossedToolBoundary;
    }

    public boolean watchdogFired() {
        return watchdogFired;
    }

    public ModelCompletionException withWatchdogFired(boolean fired) {
        this.watchdogFired = fired;
        return this;
    }
}
