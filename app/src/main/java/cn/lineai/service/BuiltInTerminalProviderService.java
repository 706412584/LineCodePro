package cn.lineai.service;

import android.os.IBinder;
import cn.lineai.R;
import cn.lineai.ipc.service.AbstractIpcProviderService;
import cn.lineai.ipc.terminal.TerminalProviderBinder;

/**
 * 主 App 内置的终端提供者 Service。
 *
 * <p>跑在独立 {@code :terminal} 进程中，Shell 与文件操作不进入主进程；{@code exported=false}
 * 且无 intent-filter，仅限本 App 通过显式 Intent 绑定，外部应用无法触达。
 * AIDL 契约与外部 terminal-provider 完全一致（{@link TerminalProviderBinder}）。</p>
 */
public final class BuiltInTerminalProviderService extends AbstractIpcProviderService {

    @Override
    protected IBinder createBinder() {
        return new TerminalProviderBinder(this, getString(R.string.builtin_terminal_provider_name));
    }
}
