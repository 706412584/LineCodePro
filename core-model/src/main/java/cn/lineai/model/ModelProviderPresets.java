package cn.lineai.model;

/**
 * 服务商预设表（含 4 槽位默认模型，参考 cc-haha providerPresets.json）。
 *
 * <p>defaultModels 顺序固定 main/haiku/sonnet/opus；{@code [1m]} 后缀表示 1M 上下文，
 * 表单侧解析为 contextSize=1000000。</p>
 */
public final class ModelProviderPresets {
    public static final ModelProviderPreset CUSTOM = new ModelProviderPreset(
            "custom",
            ModelProtocolType.OPENAI_COMPATIBLE,
            "",
            "https://api.example.com/v1"
    );

    private static final ModelProviderPreset[] PRESETS = new ModelProviderPreset[] {
            new ModelProviderPreset("deepseek", ModelProtocolType.OPENAI_COMPATIBLE,
                    "https://api.deepseek.com/v1", "https://api.deepseek.com/v1",
                    true, new String[] {"deepseek-v4-pro[1m]", "deepseek-v4-flash", "deepseek-v4-pro[1m]", "deepseek-v4-pro[1m]"}, null),
            new ModelProviderPreset("glm", ModelProtocolType.OPENAI_COMPATIBLE,
                    "https://open.bigmodel.cn/api/paas/v4", "https://open.bigmodel.cn/api/paas/v4",
                    true, new String[] {"glm-5.2[1m]", "glm-4.7", "glm-5.2[1m]", "glm-5.2[1m]"},
                    new String[][] {
                            {"cn", "https://open.bigmodel.cn/api/paas/v4"},
                            {"global", "https://api.z.ai/api/paas/v4"},
                    }),
            new ModelProviderPreset("mimo", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.xiaomimimo.com/v1", "https://api.xiaomimimo.com/v1",
                    true, new String[] {"mimo-pro", "mimo-flash", "mimo-pro", "mimo-pro"}, null),
            new ModelProviderPreset("mimo-token-plan", ModelProtocolType.OPENAI_COMPATIBLE, "https://token-plan-cn.xiaomimimo.com/v1", "https://token-plan-cn.xiaomimimo.com/v1",
                    true, new String[] {"mimo-pro", "mimo-flash", "mimo-pro", "mimo-pro"}, null),
            new ModelProviderPreset("kimi", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.moonshot.cn/v1", "https://api.moonshot.cn/v1",
                    true, new String[] {"k3", "k3", "k3", "k3"}, null),
            new ModelProviderPreset("qwen", ModelProtocolType.OPENAI_COMPATIBLE, "https://dashscope.aliyuncs.com/compatible-mode/v1", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    true, new String[] {"qwen4-max", "qwen4-flash", "qwen4-max", "qwen4-max"}, null),
            new ModelProviderPreset("openai", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "https://api.openai.com/v1",
                    true, new String[] {"gpt-5.3", "gpt-5.3-mini", "gpt-5.3", "gpt-5.3"}, null),
            new ModelProviderPreset("claude", ModelProtocolType.ANTHROPIC_MESSAGES, "https://api.anthropic.com", "https://api.anthropic.com",
                    true, new String[] {"claude-sonnet-4-6", "claude-haiku-4-5", "claude-sonnet-4-6", "claude-opus-4-8"}, null),
            new ModelProviderPreset("gemini", ModelProtocolType.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai", "https://generativelanguage.googleapis.com/v1beta/openai",
                    true, new String[] {"gemini-3-pro", "gemini-3-flash", "gemini-3-pro", "gemini-3-pro"}, null),
            new ModelProviderPreset("openrouter", ModelProtocolType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "https://openrouter.ai/api/v1",
                    true, new String[] {"", "", "", ""}, null),
            new ModelProviderPreset("groq", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1", "https://api.groq.com/openai/v1",
                    true, new String[] {"", "", "", ""}, null),
            new ModelProviderPreset("together", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.together.xyz/v1", "https://api.together.xyz/v1",
                    true, new String[] {"", "", "", ""}, null),
            new ModelProviderPreset("siliconflow", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.siliconflow.cn/v1", "https://api.siliconflow.cn/v1",
                    true, new String[] {"", "", "", ""}, null),
            new ModelProviderPreset("minimax", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.minimax.chat/v1", "https://api.minimax.chat/v1",
                    true, new String[] {"MiniMax-M3[1m]", "MiniMax-M3[1m]", "MiniMax-M3[1m]", "MiniMax-M3[1m]"}, null),
            new ModelProviderPreset("ollama", ModelProtocolType.OPENAI_COMPATIBLE, "http://127.0.0.1:11434/v1", "http://127.0.0.1:11434/v1",
                    false, new String[] {"", "", "", ""}, null),
            new ModelProviderPreset("lmstudio", ModelProtocolType.OPENAI_COMPATIBLE, "http://127.0.0.1:1234/v1", "http://127.0.0.1:1234/v1",
                    false, new String[] {"", "", "", ""}, null),
            new ModelProviderPreset("codex", ModelProtocolType.CODEX_RESPONSES, "https://api.openai.com/v1", "https://api.openai.com/v1",
                    true, new String[] {"gpt-5.3-codex", "gpt-5.3-mini", "gpt-5.3-codex", "gpt-5.3-codex"}, null)
    };

    private ModelProviderPresets() {
    }

    public static ModelProviderPreset[] all() {
        return PRESETS.clone();
    }

    public static ModelProviderPreset find(String id) {
        if (id == null) {
            return null;
        }
        for (ModelProviderPreset preset : PRESETS) {
            if (preset.getId().equals(id)) {
                return preset;
            }
        }
        return null;
    }
}
