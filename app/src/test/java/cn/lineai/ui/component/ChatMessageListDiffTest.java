package cn.lineai.ui.component;

import android.app.Activity;
import android.content.Context;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ChatUiState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 聊天列表精确 diff 的行为测试：同长度仅末行内容变化 → 局部重绑（非结构）；
 * 行数/顺序/类型变化 → 结构变化（全量刷新）；无变化 → 空 diff。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public final class ChatMessageListDiffTest {

    private Context context;
    private ChatMessageListView listView;

    @Before
    public void setup() {
        context = Robolectric.buildActivity(Activity.class).setup().get();
        listView = new ChatMessageListView(context);
    }

    @Test
    public void lastRowContentChangeIsNonStructural() {
        ChatMessage user = message("u1", ChatMessage.Role.USER, "hello");
        ChatMessage assistant = message("a1", ChatMessage.Role.ASSISTANT, "part");
        listView.render(state(false, user, assistant));

        ChatMessage assistantUpdated = message("a1", ChatMessage.Role.ASSISTANT, "partial answer");
        listView.render(state(false, user, assistantUpdated));

        ChatMessageListView.DiffResult diff = listView.lastDiff();
        Assert.assertNotNull(diff);
        Assert.assertFalse("同长度末行变化不应触发全量刷新", diff.structural);
        Assert.assertEquals(Collections.singletonList("a1"), diff.changedIds);
    }

    @Test
    public void streamingFlagChangeIsStructural() {
        ChatMessage user = message("u1", ChatMessage.Role.USER, "hello");
        ChatMessage assistant = message("a1", ChatMessage.Role.ASSISTANT, "part");
        listView.render(state(false, user, assistant));

        // streaming 开关翻转 → flags 变化 → 结构路径（安全全刷）
        listView.render(state(true, user, assistant));
        Assert.assertTrue(listView.lastDiff().structural);

        // streaming 保持不变、仅内容变化 → 局部重绑（流式高频路径）
        ChatMessage assistantUpdated = message("a1", ChatMessage.Role.ASSISTANT, "more");
        listView.render(state(true, user, assistantUpdated));
        ChatMessageListView.DiffResult streamingDiff = listView.lastDiff();
        Assert.assertFalse(streamingDiff.structural);
        Assert.assertEquals(Collections.singletonList("a1"), streamingDiff.changedIds);
    }

    @Test
    public void appendingRowIsStructural() {
        ChatMessage user = message("u1", ChatMessage.Role.USER, "hello");
        listView.render(state(false, user));

        ChatMessage assistant = message("a1", ChatMessage.Role.ASSISTANT, "answer");
        listView.render(state(true, user, assistant));
        Assert.assertTrue("追加行是结构变化", listView.lastDiff().structural);
    }

    @Test
    public void removingRowIsStructural() {
        ChatMessage user = message("u1", ChatMessage.Role.USER, "hello");
        ChatMessage assistant = message("a1", ChatMessage.Role.ASSISTANT, "answer");
        listView.render(state(false, user, assistant));

        listView.render(state(false, user));
        Assert.assertTrue("删行是结构变化", listView.lastDiff().structural);
    }

    @Test
    public void identicalStateYieldsEmptyDiff() {
        ChatMessage user = message("u1", ChatMessage.Role.USER, "hello");
        ChatMessage assistant = message("a1", ChatMessage.Role.ASSISTANT, "answer");
        listView.render(state(false, user, assistant));

        // 全量短路：flags 与消息完全一致 → changedIds 空、非结构（早退分支）
        ChatMessage assistantSame = message("a1", ChatMessage.Role.ASSISTANT, "answer");
        listView.render(state(false, user, assistantSame));

        ChatMessageListView.DiffResult diff = listView.lastDiff();
        Assert.assertFalse(diff.structural);
        Assert.assertTrue(diff.changedIds.isEmpty());
    }

    @Test
    public void conversationChangeIsStructuralAndReported() {
        ChatMessage user = message("u1", ChatMessage.Role.USER, "hello");
        listView.render(state("c1", false, user));

        ChatMessage other = message("x1", ChatMessage.Role.USER, "other");
        listView.render(state("c2", false, other));

        ChatMessageListView.DiffResult diff = listView.lastDiff();
        Assert.assertTrue(diff.conversationChanged);
        Assert.assertTrue(diff.structural);
    }

    @Test
    public void midConversationContentChangeDetectsChangedIds() {
        ChatMessage m1 = message("u1", ChatMessage.Role.USER, "q1");
        ChatMessage m2 = message("a1", ChatMessage.Role.ASSISTANT, "r1");
        ChatMessage m3 = message("u2", ChatMessage.Role.USER, "q2");
        listView.render(state(false, m1, m2, m3));

        // 中间行内容变化：非结构，changedIds 指向该行
        ChatMessage m2Updated = message("a1", ChatMessage.Role.ASSISTANT, "r1 revised");
        listView.render(state(false, m1, m2Updated, m3));
        ChatMessageListView.DiffResult diff = listView.lastDiff();
        Assert.assertFalse(diff.structural);
        Assert.assertEquals(Collections.singletonList("a1"), diff.changedIds);
    }

    private ChatMessage message(String id, ChatMessage.Role role, String content) {
        return new ChatMessage(id, role, content, false);
    }

    private ChatUiState state(boolean streaming, ChatMessage... messages) {
        return state("c1", streaming, messages);
    }

    private ChatUiState state(String conversationId, boolean streaming, ChatMessage... messages) {
        List<ChatMessage> list = new ArrayList<>(Arrays.asList(messages));
        return new ChatUiState(
                "LineCode", "", "", "",
                0, streaming, true,
                true, false, false, false,
                "", "",
                "coding", conversationId, list,
                "", Collections.emptyList(), null
        );
    }
}
