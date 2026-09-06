package cn.lineai.mvp;

import android.content.Context;
import android.util.Log;
import cn.lineai.data.repository.IpcProviderStore;
import cn.lineai.ipc.BaseIpcProvider;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderConnectionState;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderScanner;
import cn.lineai.ipc.IpcProviderStateListener;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.ScannedProvider;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import cn.lineai.service.BuiltInTerminalProviderService;
import java.util.Collections;
import java.util.List;

public final class IpcProviderController implements IpcProviderStateListener {
    public interface Host {
        boolean isTerminalProviderExecutionMode();

        void applyTerminalProviderProjectPath(String path, String label);

        void clearTerminalProviderProjectPath();

        void requestIpcFileTreeLoad(boolean force);

        void refreshVisibleScreen(String screenId);

        void render();
    }

    private static final String TAG = "IpcProviderController";

    private final Context context;
    private final IpcProviderStore ipcProviderStore;
    private final IpcProviderScanner ipcProviderScanner;
    private final IpcProviderManager ipcProviderManager;
    private final Host host;
    private List<ScannedProvider> terminalProviderScanResults = Collections.emptyList();
    private boolean terminalProviderHasScanned;
    private boolean ipcProjectPathApplied;

    public IpcProviderController(
            Context context,
            IpcProviderStore ipcProviderStore,
            IpcProviderScanner ipcProviderScanner,
            IpcProviderManager ipcProviderManager,
            Host host
    ) {
        this.context = context.getApplicationContext();
        this.ipcProviderStore = ipcProviderStore;
        this.ipcProviderScanner = ipcProviderScanner;
        this.ipcProviderManager = ipcProviderManager;
        this.host = host;
        if (this.ipcProviderManager != null) {
            this.ipcProviderManager.addStateListener(this);
        }
        restoreIpcProviders();
    }

    @Override
    public void onStateChanged(BaseIpcProvider provider, IpcProviderConnectionState newState, Throwable cause) {
        onIpcProviderStateChanged(provider, newState, cause);
    }

    public List<ScannedProvider> onTerminalProviderScan() {
        if (ipcProviderScanner == null) {
            terminalProviderScanResults = Collections.emptyList();
        } else {
            terminalProviderScanResults = ipcProviderScanner.scan(context, IpcProviderType.TERMINAL);
        }
        terminalProviderHasScanned = true;
        return terminalProviderScanResults;
    }

    public List<ScannedProvider> getTerminalProviderScanResults() {
        return terminalProviderScanResults;
    }

    public boolean hasTerminalProviderScanned() {
        return terminalProviderHasScanned;
    }

    public void onTerminalProviderSaved(IpcProviderConfig config) {
        if (ipcProviderStore == null || config == null) {
            return;
        }
        IpcProviderConfig saved = ipcProviderStore.saveProvider(config);
        if (saved.isEnabled() && ipcProviderManager != null) {
            ensureSingleTerminalProvider(saved.getId());
        }
        refreshTerminalProviderScreen();
    }

    public void onTerminalProviderEnabledChanged(String id, boolean enabled) {
        if (ipcProviderStore == null || ipcProviderManager == null) {
            return;
        }
        ipcProviderStore.setProviderEnabled(id, enabled);
        if (enabled) {
            ensureSingleTerminalProvider(id);
        } else {
            ipcProviderManager.unregisterAndUnbind(id);
        }
        refreshTerminalProviderScreen();
    }

    public void onTerminalProviderDeleted(String id) {
        if (ipcProviderStore == null || ipcProviderManager == null) {
            return;
        }
        if (IpcProviderConfig.BUILT_IN_ID.equals(id)) {
            return;
        }
        ipcProviderManager.unregisterAndUnbind(id);
        ipcProviderStore.deleteProvider(id);
        refreshTerminalProviderScreen();
    }

    /**
     * 单选语义：启用指定 terminal provider 并停用其余同类型（含内置），
     * 保证同一时刻仅一个终端提供者绑定生效。
     */
    private void ensureSingleTerminalProvider(String activeId) {
        for (IpcProviderConfig config : terminalProviders()) {
            if (activeId.equals(config.getId())) {
                ipcProviderManager.registerAndBind(config);
            } else {
                ipcProviderManager.unregisterAndUnbind(config.getId());
                ipcProviderStore.setProviderEnabled(config.getId(), false);
            }
        }
    }

    private List<IpcProviderConfig> terminalProviders() {
        if (ipcProviderStore == null) {
            return Collections.emptyList();
        }
        return ipcProviderStore.getProvidersByType(IpcProviderType.TERMINAL);
    }

    private void restoreIpcProviders() {
        if (ipcProviderStore == null || ipcProviderManager == null) {
            return;
        }
        seedBuiltInProvider();
        IpcProviderConfig toBind = resolveProviderToBind(terminalProviders());
        if (toBind == null) {
            return;
        }
        try {
            ipcProviderManager.registerAndBind(toBind);
        } catch (RuntimeException e) {
            Log.w(TAG, "重连 IPC 提供者失败: " + toBind.getId(), e);
        }
    }

    /**
     * 首次启动时把内置终端提供者 seed 进 ipc_providers 表；
     * 用户此后的启用/禁用状态由表持久化。
     */
    private void seedBuiltInProvider() {
        for (IpcProviderConfig config : terminalProviders()) {
            if (config.isBuiltIn()) {
                return;
            }
        }
        ipcProviderStore.saveProvider(IpcProviderConfig.builder()
                .id(IpcProviderConfig.BUILT_IN_ID)
                .providerType(IpcProviderType.TERMINAL.getId())
                .name(context.getString(cn.lineai.R.string.builtin_terminal_provider_name))
                .packageName(context.getPackageName())
                .serviceClass(BuiltInTerminalProviderService.class.getName())
                .enabled(true)
                .build());
    }

    /**
     * 从 terminal provider 列表（updated_at DESC）选出应绑定的唯一 provider：
     * 第一个 enabled 的记录；全部禁用返回 null。
     */
    static IpcProviderConfig resolveProviderToBind(List<IpcProviderConfig> providers) {
        if (providers == null) {
            return null;
        }
        for (IpcProviderConfig config : providers) {
            if (config != null && config.isEnabled()) {
                return config;
            }
        }
        return null;
    }

    private void onIpcProviderStateChanged(
            BaseIpcProvider provider,
            IpcProviderConnectionState newState,
            Throwable cause) {
        if (provider == null || provider.getProviderType() != IpcProviderType.TERMINAL) {
            return;
        }
        if (newState == IpcProviderConnectionState.CONNECTED) {
            applyIpcProjectPath((TerminalIpcProvider) provider);
            return;
        }
        if ((newState == IpcProviderConnectionState.DISCONNECTED
                || newState == IpcProviderConnectionState.FAILED)
                && host != null
                && host.isTerminalProviderExecutionMode()
                && ipcProjectPathApplied) {
            host.clearTerminalProviderProjectPath();
            ipcProjectPathApplied = false;
            host.render();
        }
    }

    private void applyIpcProjectPath(TerminalIpcProvider provider) {
        if (provider == null || host == null) {
            return;
        }
        String home;
        try {
            home = provider.getHomePath();
        } catch (Exception e) {
            Log.w(TAG, "读取 IPC home 失败", e);
            home = "";
        }
        if (home.length() == 0) {
            return;
        }
        String label = provider.getConfig() == null ? "" : provider.getConfig().getName();
        host.applyTerminalProviderProjectPath(home, label);
        ipcProjectPathApplied = true;
        host.requestIpcFileTreeLoad(true);
        host.render();
    }

    private IpcProviderConfig findIpcProvider(String id) {
        if (id == null || id.length() == 0 || ipcProviderStore == null) {
            return null;
        }
        for (IpcProviderConfig config : ipcProviderStore.getProviders()) {
            if (id.equals(config.getId())) {
                return config;
            }
        }
        return null;
    }

    private void refreshTerminalProviderScreen() {
        if (host == null) {
            return;
        }
        host.refreshVisibleScreen("terminalProvider");
        host.render();
    }
}
