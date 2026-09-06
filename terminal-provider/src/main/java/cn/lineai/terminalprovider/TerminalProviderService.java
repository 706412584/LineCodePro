package cn.lineai.terminalprovider;

import android.os.IBinder;
import android.util.Log;
import cn.lineai.ipc.service.AbstractIpcProviderService;
import cn.lineai.ipc.terminal.TerminalProviderBinder;

public final class TerminalProviderService extends AbstractIpcProviderService {
    private static final String TAG = "TerminalProvider";

    @Override
    protected IBinder createBinder() {
        return new TerminalProviderBinder(this, "Android Shell Terminal Provider");
    }

    @Override
    protected void onProviderDestroy() {
        // 共享线程池由 :ipc 库统一管理生命周期，此处无需 shutdown。
        // 保留钩子供未来按需扩展。
        Log.i(TAG, "TerminalProviderService 销毁");
    }
}
