package cn.lineai.ai.retry;

/**
 * 助手消息提交缓冲（借鉴 cc-haha streamAssistantCommitBuffer）：
 * 流式过程中累积 text/reasoning，一旦出现完整工具调用（副作用边界）即不可安全重发。
 * 流中断时若未越界，已收到内容随异常带出（partial），供编排层提交为部分回复。
 */
public final class AssistantCommitBuffer {

    private final StringBuilder text = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private boolean toolBoundaryCrossed;

    public void appendText(String delta) {
        if (delta != null && delta.length() > 0) {
            text.append(delta);
        }
    }

    public void appendReasoning(String delta) {
        if (delta != null && delta.length() > 0) {
            reasoning.append(delta);
        }
    }

    /** 副作用边界：任一协议出现完整工具调用请求时标记，此后断流不可重发。 */
    public void markToolUseStarted() {
        toolBoundaryCrossed = true;
    }

    public boolean crossedToolBoundary() {
        return toolBoundaryCrossed;
    }

    public boolean hasPartial() {
        return text.length() > 0 || reasoning.length() > 0;
    }

    public String text() {
        return text.toString();
    }

    public String reasoning() {
        return reasoning.toString();
    }
}
