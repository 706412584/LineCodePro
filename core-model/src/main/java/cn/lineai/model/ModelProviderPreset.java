package cn.lineai.model;

/**
 * 服务商预设（cc-haha providerPresets 语义转译）：连接信息 + 4 槽位默认模型。
 *
 * <p>{@code defaultModels} 顺序固定为 main/haiku/sonnet/opus；
 * 值可带 {@code [1m]} 后缀（表示 1M 上下文），由表单侧解析为 contextSize。
 * {@code regionalEndpoints} 为 {label, baseUrl} 对，仅多区域服务商（如智谱）非空。</p>
 */
public final class ModelProviderPreset {
    private final String id;
    private final ModelProtocolType protocolType;
    private final String baseUrl;
    private final String placeholder;
    private final boolean needsApiKey;
    private final String[] defaultModels;
    private final String[][] regionalEndpoints;

    public ModelProviderPreset(String id, ModelProtocolType protocolType, String baseUrl, String placeholder) {
        this(id, protocolType, baseUrl, placeholder, true,
                new String[] {"", "", "", ""}, null);
    }

    public ModelProviderPreset(String id, ModelProtocolType protocolType, String baseUrl, String placeholder,
                               boolean needsApiKey, String[] defaultModels, String[][] regionalEndpoints) {
        this.id = id;
        this.protocolType = protocolType;
        this.baseUrl = baseUrl;
        this.placeholder = placeholder;
        this.needsApiKey = needsApiKey;
        this.defaultModels = defaultModels == null || defaultModels.length < 4
                ? new String[] {"", "", "", ""}
                : defaultModels.clone();
        this.regionalEndpoints = regionalEndpoints;
    }

    public String getId() {
        return id;
    }

    public ModelProtocolType getProtocolType() {
        return protocolType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public boolean needsApiKey() {
        return needsApiKey;
    }

    /** 4 槽位默认模型（main/haiku/sonnet/opus 顺序；可带 [1m] 后缀）。 */
    public String[] getDefaultModels() {
        return defaultModels.clone();
    }

    /** 区域端点（{label, baseUrl} 对）；null 表示单一端点。 */
    public String[][] getRegionalEndpoints() {
        return regionalEndpoints;
    }
}
