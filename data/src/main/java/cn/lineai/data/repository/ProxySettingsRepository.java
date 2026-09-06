package cn.lineai.data.repository;

/**
 * 网络代理设置仓库（host + port；空 = 不启用）。
 *
 * <p>消费方：AppProxy.apply（启动/设置变更时）与 ToolSettingsStore.getProxyUrl()
 * （proot 内 http_proxy 透传）。port 以字符串存储（SettingsRepository 无 int 通道）。</p>
 */
public final class ProxySettingsRepository {

    public static final String KEY_PROXY_HOST = "@lineai_proxy_host";
    public static final String KEY_PROXY_PORT = "@lineai_proxy_port";

    private final SettingsRepository settingsRepository;

    public ProxySettingsRepository(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public synchronized String getHost() {
        return settingsRepository.getString(KEY_PROXY_HOST, "").trim();
    }

    public synchronized int getPort() {
        String raw = settingsRepository.getString(KEY_PROXY_PORT, "0").trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public synchronized void set(String host, int port) {
        String safeHost = host == null ? "" : host.trim();
        int safePort = port > 0 && port <= 65535 ? port : 0;
        settingsRepository.setString(KEY_PROXY_HOST, safeHost);
        settingsRepository.setString(KEY_PROXY_PORT, String.valueOf(safePort));
    }

    /** 代理 URL（http://host:port）；未配置返回空串。 */
    public synchronized String getProxyUrl() {
        String host = getHost();
        int port = getPort();
        return host.isEmpty() || port <= 0 ? "" : "http://" + host + ":" + port;
    }
}
