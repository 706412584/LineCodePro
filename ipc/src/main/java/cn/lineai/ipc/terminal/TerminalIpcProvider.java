package cn.lineai.ipc.terminal;

import android.os.RemoteException;
import cn.lineai.ipc.BaseIpcProvider;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderType;
import java.io.File;
import org.json.JSONObject;

public final class TerminalIpcProvider extends BaseIpcProvider {

    public TerminalIpcProvider(IpcProviderConfig config) {
        super(config);
    }

    @Override
    public IpcProviderType getProviderType() {
        return IpcProviderType.TERMINAL;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    private ITerminalProviderService getService() {
        if (!isBound() || serviceBinder == null) {
            throw new IllegalStateException("终端提供者服务未绑定");
        }
        return ITerminalProviderService.Stub.asInterface(serviceBinder);
    }

    /**
     * 在 proot + Alpine rootfs 内执行命令。
     *
     * <p>命令经 {@link ProotCommandBuilder} 包装后仍走现有 {@code executeShell}
     * AIDL 通道（零 AIDL 改动，外部 provider 无感）。</p>
     */
    public TerminalShellResult executeShellInLinux(String command, String cwd, long timeoutMs,
                                                   String prootBin, File rootfsDir,
                                                   TerminalShellCallback callback) throws RemoteException {
        return executeShellInLinux(command, cwd, timeoutMs, prootBin, rootfsDir, "", callback);
    }

    /** 带全局 HTTP 代理的 Linux 执行（guest 内 export http_proxy/https_proxy）。 */
    public TerminalShellResult executeShellInLinux(String command, String cwd, long timeoutMs,
                                                   String prootBin, File rootfsDir, String proxyUrl,
                                                   TerminalShellCallback callback) throws RemoteException {
        String wrapped = ProotCommandBuilder.build(prootBin, rootfsDir, cwd, command, proxyUrl);
        // cwd 交给 proot -w 参数（guest 路径），host 侧工作目录用 provider 默认
        return executeShell(wrapped, "", timeoutMs, callback);
    }

    /** proot 探测结果：supported + 失败时的输出详情（供 UI 回显诊断）。 */
    public static final class ProbeResult {
        public final boolean supported;
        public final String output;

        public ProbeResult(boolean supported, String output) {
            this.supported = supported;
            this.output = output == null ? "" : output;
        }
    }

    /**
     * 探测当前设备是否支持 proot（SELinux/seccomp 兼容性）。
     * 失败时捕获输出（proot 的 stderr / loader 报错）供诊断回显。
     *
     * @param probeCommand guest 内探测命令（Alpine 用 busybox echo，其他发行版直接 echo）
     */
    public ProbeResult probeProotDetailed(String prootBin, File rootfsDir, String probeCommand) {
        String command = probeCommand == null || probeCommand.length() == 0
                ? "echo __lineai_proot_ok__" : probeCommand;
        StringBuilder output = new StringBuilder();
        try {
            TerminalShellResult result = executeShell(
                    ProotCommandBuilder.build(prootBin, rootfsDir, "", command),
                    "", 15000L, new TerminalShellCallback() {
                        @Override
                        public void onOutput(String content) {
                            synchronized (output) {
                                output.append(content == null ? "" : content);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            synchronized (output) {
                                output.append(error == null ? "" : error);
                            }
                        }

                        @Override
                        public void onComplete(int exitCode) {
                        }
                    });
            String text = output.toString().trim();
            if (result.isSuccess() && text.contains("__lineai_proot_ok__")) {
                return new ProbeResult(true, text);
            }
            return new ProbeResult(false, text.length() == 0
                    ? "proot exited with code " + result.getExitCode() : text);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            synchronized (output) {
                String streamed = output.toString().trim();
                return new ProbeResult(false, streamed.length() == 0 ? message : streamed + "\n" + message);
            }
        }
    }

    /** 兼容入口：Alpine busybox 探测，仅判断成败。 */
    public boolean probeProot(String prootBin, File rootfsDir) {
        return probeProotDetailed(prootBin, rootfsDir,
                "busybox echo __lineai_proot_ok__").supported;
    }

    public TerminalShellResult executeShell(String command, String cwd, long timeoutMs,
                                            TerminalShellCallback callback) throws RemoteException {
        ITerminalProviderService service = getService();
        ITerminalProviderCallback aidlCallback = new ITerminalProviderCallback.Stub() {
            @Override
            public void onOutput(String content) {
                if (callback != null) {
                    callback.onOutput(content);
                }
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }

            @Override
            public void onComplete(int exitCode) {
                if (callback != null) {
                    callback.onComplete(exitCode);
                }
            }
        };
        int exitCode = service.executeShell(command, cwd, timeoutMs, aidlCallback);
        return new TerminalShellResult(exitCode);
    }

    public byte[] readFile(String path) throws RemoteException {
        return getService().readFile(path);
    }

    public boolean writeFile(String path, byte[] data) throws RemoteException {
        return getService().writeFile(path, data);
    }

    public byte[] readFileChunk(String path, long offset, int size) throws RemoteException {
        return getService().readFileChunk(path, offset, size);
    }

    public boolean writeFileChunk(String path, long offset, byte[] data) throws RemoteException {
        return getService().writeFileChunk(path, offset, data);
    }

    public long getFileSize(String path) throws RemoteException {
        return getService().getFileSize(path);
    }

    public boolean deleteFile(String path) throws RemoteException {
        return getService().deleteFile(path);
    }

    public String[] listDir(String path) throws RemoteException {
        return getService().listDir(path);
    }

    public boolean fileExists(String path) throws RemoteException {
        return getService().fileExists(path);
    }

    public long fileSize(String path) throws RemoteException {
        return getService().fileSize(path);
    }

    public String listDirDetailed(String path) throws RemoteException {
        return getService().listDirDetailed(path);
    }

    public String getHomePath() throws RemoteException {
        String info = getService().getProviderInfo();
        if (info == null || info.length() == 0) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(info);
            return json.optString("home", "");
        } catch (Exception e) {
            return "";
        }
    }
}
