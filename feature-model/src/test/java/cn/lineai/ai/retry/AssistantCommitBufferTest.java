package cn.lineai.ai.retry;

import org.junit.Assert;
import org.junit.Test;

public final class AssistantCommitBufferTest {

    @Test
    public void appendAndRead() {
        AssistantCommitBuffer buffer = new AssistantCommitBuffer();
        buffer.appendReasoning("think");
        buffer.appendText("hello ");
        buffer.appendText("world");
        Assert.assertEquals("think", buffer.reasoning());
        Assert.assertEquals("hello world", buffer.text());
        Assert.assertTrue(buffer.hasPartial());
        Assert.assertFalse(buffer.crossedToolBoundary());
    }

    @Test
    public void markBoundaryIsSticky() {
        AssistantCommitBuffer buffer = new AssistantCommitBuffer();
        buffer.appendText("partial");
        buffer.markToolUseStarted();
        Assert.assertTrue(buffer.crossedToolBoundary());
        // null/空 delta 安全
        buffer.appendText(null);
        buffer.appendReasoning("");
        Assert.assertEquals("partial", buffer.text());
    }

    @Test
    public void emptyBufferHasNoPartial() {
        AssistantCommitBuffer buffer = new AssistantCommitBuffer();
        Assert.assertFalse(buffer.hasPartial());
    }
}
