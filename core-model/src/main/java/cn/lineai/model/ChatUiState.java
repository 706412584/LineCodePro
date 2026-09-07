package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatUiState {
    private final ToolApproval toolApproval;
    private final String projectLabel;
    private final String projectPath;
    private final String modelLabel;
    private final String selectedModelId;
    private final String contextLabel;
    private final int contextPercent;
    private final boolean streaming;
    private final boolean hasConfiguredModel;
    private final boolean thinkingScrollEnabled;
    private final boolean thinkingAutoExpandEnabled;
    private final boolean processAutoExpandEnabled;
    private final boolean codeWrapEnabled;
    private final String browserMode;
    private final String enterKeyBehavior;
    private final String chatMode;
    private final String conversationId;
    private final List<ChatMessage> messages;
    private final List<ModelConfig> availableModels;

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, true, false, false, OutputSettings.BROWSER_BUILTIN,
                InputSettings.ENTER_SEND, ChatMode.DEFAULT, "", messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, false,
                OutputSettings.BROWSER_BUILTIN, InputSettings.ENTER_SEND, ChatMode.DEFAULT, "", messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, codeWrapEnabled,
                browserMode, InputSettings.ENTER_SEND, ChatMode.DEFAULT, "", messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, String chatMode, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, codeWrapEnabled,
                browserMode, InputSettings.ENTER_SEND, chatMode, "", messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, String chatMode,
            String conversationId, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, codeWrapEnabled,
                browserMode, InputSettings.ENTER_SEND, chatMode, conversationId, messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, String enterKeyBehavior,
            String chatMode, String conversationId, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, codeWrapEnabled,
                browserMode, enterKeyBehavior, chatMode, conversationId, messages, "", Collections.emptyList());
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, String enterKeyBehavior,
            String chatMode, String conversationId, List<ChatMessage> messages,
            String selectedModelId, List<ModelConfig> availableModels
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming, hasConfiguredModel,
                thinkingScrollEnabled, thinkingAutoExpandEnabled, codeWrapEnabled, browserMode, enterKeyBehavior,
                chatMode, conversationId, messages, selectedModelId, availableModels, null);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, String enterKeyBehavior,
            String chatMode, String conversationId, List<ChatMessage> messages,
            String selectedModelId, List<ModelConfig> availableModels, ToolApproval toolApproval
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming, hasConfiguredModel,
                thinkingScrollEnabled, thinkingAutoExpandEnabled, false, codeWrapEnabled, browserMode,
                enterKeyBehavior, chatMode, conversationId, messages, selectedModelId, availableModels, toolApproval);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean processAutoExpandEnabled, boolean codeWrapEnabled, String browserMode, String enterKeyBehavior,
            String chatMode, String conversationId, List<ChatMessage> messages,
            String selectedModelId, List<ModelConfig> availableModels, ToolApproval toolApproval
    ) {
        this.toolApproval = toolApproval;
        this.projectLabel = projectLabel;
        this.projectPath = projectPath == null ? "" : projectPath;
        this.modelLabel = modelLabel;
        this.selectedModelId = selectedModelId == null ? "" : selectedModelId;
        this.contextLabel = contextLabel;
        this.contextPercent = contextPercent;
        this.streaming = streaming;
        this.hasConfiguredModel = hasConfiguredModel;
        this.thinkingScrollEnabled = thinkingScrollEnabled;
        this.thinkingAutoExpandEnabled = thinkingAutoExpandEnabled;
        this.processAutoExpandEnabled = processAutoExpandEnabled;
        this.codeWrapEnabled = codeWrapEnabled;
        this.browserMode = OutputSettings.normalizeBrowserMode(browserMode);
        this.enterKeyBehavior = InputSettings.normalizeEnterKeyBehavior(enterKeyBehavior);
        this.chatMode = ChatMode.normalize(chatMode);
        this.conversationId = conversationId == null ? "" : conversationId;
        this.messages = messages == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(messages));
        this.availableModels = availableModels == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(availableModels));
    }

    public ChatUiState withToolApproval(ToolApproval approval) {
        ChatUiState next = new ChatUiState(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, processAutoExpandEnabled,
                codeWrapEnabled, browserMode,
                enterKeyBehavior, chatMode, conversationId, messages, selectedModelId, availableModels, approval);
        return copyToolbarState(next);
    }
    public ChatUiState withDisplayMessages(List<ChatMessage> displayMessages) {
        ChatUiState next = new ChatUiState(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, processAutoExpandEnabled,
                codeWrapEnabled, browserMode, enterKeyBehavior, chatMode, conversationId, displayMessages,
                selectedModelId, availableModels, toolApproval);
        return copyToolbarState(next);
    }

    /** with* 派生状态时保留编辑框工具栏字段（权限/git 分支/推理 effort），否则工具栏芯片会回退到默认值。 */
    private ChatUiState copyToolbarState(ChatUiState next) {
        next.permissionMode = permissionMode;
        next.gitBranch = gitBranch;
        next.reasoningEffort = reasoningEffort;
        return next;
    }
    public ToolApproval getToolApproval() { return toolApproval; }
    public String getProjectLabel() { return projectLabel; }
    public String getProjectPath() { return projectPath; }
    public String getModelLabel() { return modelLabel; }
    public String getSelectedModelId() { return selectedModelId; }
    public List<ModelConfig> getAvailableModels() { return availableModels; }
    public String getContextLabel() { return contextLabel; }
    public int getContextPercent() { return contextPercent; }
    public boolean isStreaming() { return streaming; }
    public boolean hasConfiguredModel() { return hasConfiguredModel; }
    public boolean isThinkingScrollEnabled() { return thinkingScrollEnabled; }
    public boolean isThinkingAutoExpandEnabled() { return thinkingAutoExpandEnabled; }
    public boolean isProcessAutoExpandEnabled() { return processAutoExpandEnabled; }
    public boolean isCodeWrapEnabled() { return codeWrapEnabled; }
    public String getBrowserMode() { return browserMode; }
    public String getEnterKeyBehavior() { return enterKeyBehavior; }
    public String getChatMode() { return chatMode; }
    public String getConversationId() { return conversationId; }
    public List<ChatMessage> getMessages() { return messages; }

    // ===== 编辑框工具栏状态（cc-haha 复刻；默认空，经 withToolbarState 注入） =====
    private String permissionMode = "";
    private String gitBranch = "";
    private String reasoningEffort = "";

    public ChatUiState withToolbarState(String permissionMode, String gitBranch, String reasoningEffort) {
        ChatUiState next = new ChatUiState(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, processAutoExpandEnabled,
                codeWrapEnabled, browserMode, enterKeyBehavior, chatMode, conversationId, messages,
                selectedModelId, availableModels, toolApproval);
        next.permissionMode = permissionMode == null ? "" : permissionMode;
        next.gitBranch = gitBranch == null ? "" : gitBranch;
        next.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort;
        return next;
    }

    public String getPermissionMode() { return permissionMode == null ? "" : permissionMode; }
    public String getGitBranch() { return gitBranch == null ? "" : gitBranch; }
    public String getReasoningEffort() { return reasoningEffort == null ? "" : reasoningEffort; }
}
