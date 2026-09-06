package cn.lineai.mvp;

import cn.lineai.model.OutputSettings;

public interface OutputSettingsController {
    OutputSettings getOutputSettings();

    void onCodeWrapChanged(boolean enabled);

    void onProcessAutoExpandChanged(boolean enabled);

    void onBrowserModeChanged(String mode);

    void onBrowserJavaScriptChanged(boolean enabled);

    void onAllowAnyHttpChanged(boolean enabled);

    void onBypassPathProtectionChanged(boolean enabled);

    String getProxyHost();

    int getProxyPort();

    /** host 为空表示清除代理；立即对全 app HTTP 生效。 */
    void onProxySettingsChanged(String host, int port);
}
