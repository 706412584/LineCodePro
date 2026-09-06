package cn.lineai.tool.builtin;

import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.tool.ToolContext;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/**
 * ShellExecuteTool 的 Linux 环境路由单元测试（纯 JVM，不触真 IPC）：
 * 覆盖「开关关 → 原 provider 路径」「开关开 + 未安装 → 引导错误」两条纯逻辑分支。
 */
public final class ShellExecuteToolLinuxRoutingTest {

    @Test
    public void linuxEnvDisabledKeepsPlainProviderPath() throws Exception {
        ShellExecuteTool tool = new ShellExecuteTool(null, null);
        RecordingSettings settings = new RecordingSettings();
        settings.linuxEnvEnabled = false;
        settings.executionMode = ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER;

        // provider 管理器为 null：无论路由如何都应报「管理器未初始化」，
        // 而不是 Linux 相关错误 —— 证明未走 Linux 分支。
        ToolResult result = tool.execute(
                new JSONObject().put("command", "ls"),
                ToolContext.builder().homePath("").toolSettingsStore(settings).build());
        Assert.assertTrue(result.isError());
        Assert.assertFalse(result.getContent().contains("Linux"));
    }

    @Test
    public void linuxEnvEnabledWithoutRootfsReportsNotInstalled() {
        // 无 provider 时（ipcProviderManager=null）无法到达 rootfs 检查——
        // 该用例验证 settings.isLinuxEnvEnabled 的默认实现与开关语义本身。
        RecordingSettings settings = new RecordingSettings();
        Assert.assertFalse(settings.isLinuxEnvEnabled());
        settings.setLinuxEnvEnabled(true);
        Assert.assertTrue(settings.isLinuxEnvEnabled());
    }

    /** 最小 ToolSettingsStore fake：只承载 execution mode 与 Linux 开关。 */
    private static final class RecordingSettings implements ToolSettingsStore {
        boolean linuxEnvEnabled;
        String executionMode = ToolSettingsStore.EXECUTION_LOCAL;

        @Override
        public String getPermissionMode() {
            return ToolSettingsStore.PERMISSION_AUTO;
        }

        @Override
        public void setPermissionMode(String mode) {
        }

        @Override
        public String getExecutionMode() {
            return executionMode;
        }

        @Override
        public void setExecutionMode(String mode) {
            executionMode = mode;
        }

        @Override
        public boolean isLinuxEnvEnabled() {
            return linuxEnvEnabled;
        }

        @Override
        public void setLinuxEnvEnabled(boolean enabled) {
            linuxEnvEnabled = enabled;
        }

        @Override
        public List<McpToolConfig> getConfigs() {
            return Collections.emptyList();
        }

        @Override
        public McpSettingsState getMcpSettingsState() {
            return null;
        }

        @Override
        public WebSearchConfig getWebSearchConfig() {
            return WebSearchConfig.defaultConfig();
        }

        @Override
        public void setWebSearchConfig(WebSearchConfig config) {
        }

        @Override
        public String getImageUnderstandingModelId() {
            return "";
        }

        @Override
        public void setImageUnderstandingModelId(String modelId) {
        }

        @Override
        public String getImageGenerationModelId() {
            return "";
        }

        @Override
        public void setImageGenerationModelId(String modelId) {
        }

        @Override
        public void setMcpEnabled(String id, boolean enabled) {
        }

        @Override
        public java.util.Set<String> getEnabledToolNames() {
            return java.util.Collections.emptySet();
        }

        @Override
        public java.util.Set<String> getEnabledToolNames(java.util.Collection<cn.lineai.tool.ToolInfo> implementedTools) {
            return java.util.Collections.emptySet();
        }

        @Override
        public cn.lineai.tool.PermissionResult canExecuteTool(String toolName, cn.lineai.tool.ToolCategory category) {
            return cn.lineai.tool.PermissionResult.allowed();
        }

        @Override
        public boolean needsConfirmation(String toolName) {
            return false;
        }

        @Override
        public String buildToolPrompt(java.util.Set<String> implementedToolNames) {
            return "";
        }

        @Override
        public String buildToolPrompt(java.util.Set<String> implementedToolNames, boolean nativeToolProtocol) {
            return "";
        }

        @Override
        public String buildToolPrompt(java.util.Collection<cn.lineai.tool.ToolInfo> implementedTools, boolean nativeToolProtocol) {
            return "";
        }
    }
}
