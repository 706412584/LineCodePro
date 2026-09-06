package cn.lineai.mvp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.terminal.AlpineTarExtractor;
import cn.lineai.ipc.terminal.LinuxRootfsLayout;
import cn.lineai.ipc.terminal.ProotCommandBuilder;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import cn.lineai.security.SimpleHttpClient;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Linux 环境（proot + Alpine rootfs）生命周期控制器。
 *
 * <p>负责 rootfs 的下载（sha256 校验）、解压、安装后探测与删除；
 * 状态变更通过 {@link Host} 回调刷新设置页。</p>
 */
public final class LinuxEnvironmentController {

    /** rootfs 状态，供 UI 显示。 */
    public enum State {
        MISSING, INSTALLING, INSTALLED, UNSUPPORTED
    }

    public interface Host {
        void refreshLinuxEnvUi();

        void postToMainThread(Runnable action);

        boolean isMainThread();
    }

    private static final String TAG = "LinuxEnvController";
    private static final int CONNECT_TIMEOUT_MS = 60000;
    private static final int READ_TIMEOUT_MS = 180000;
    private static final long PREINSTALL_TIMEOUT_MS = 300000L;

    private final Context context;
    private final ToolSettingsStore toolSettingsRepository;
    private final IpcProviderManager ipcProviderManager;
    private final BackgroundTaskRunner backgroundTasks;
    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean installing;

    public LinuxEnvironmentController(
            Context context,
            ToolSettingsStore toolSettingsRepository,
            IpcProviderManager ipcProviderManager,
            BackgroundTaskRunner backgroundTasks,
            Host host
    ) {
        this.context = context.getApplicationContext();
        this.toolSettingsRepository = toolSettingsRepository;
        this.ipcProviderManager = ipcProviderManager;
        this.backgroundTasks = backgroundTasks;
        this.host = host;
    }

    public boolean isInstalling() {
        return installing;
    }

    public boolean isEnabled() {
        return toolSettingsRepository != null && toolSettingsRepository.isLinuxEnvEnabled();
    }

    public void setEnabled(boolean enabled) {
        if (toolSettingsRepository != null) {
            toolSettingsRepository.setLinuxEnvEnabled(enabled);
        }
        refreshUi();
    }

    /** 当前状态（读磁盘 + 内存安装标志）。 */
    public State state() {
        if (installing) {
            return State.INSTALLING;
        }
        LinuxRootfsLayout.Meta meta = LinuxRootfsLayout.readMeta(context.getFilesDir());
        if (meta == null || !LinuxRootfsLayout.isInstalled(context.getFilesDir())) {
            return State.MISSING;
        }
        return meta.prootSupported ? State.INSTALLED : State.UNSUPPORTED;
    }

    /** 已安装 rootfs 占用字节数；未安装返回 0。 */
    public long installedSizeBytes() {
        return directorySize(LinuxRootfsLayout.rootfsDir(context.getFilesDir()));
    }

    /** 启动安装（下载 → 校验 → 解压 → resolv.conf → probe → meta）。幂等：安装中忽略。 */
    public void install() {
        if (installing) {
            return;
        }
        String arch = LinuxRootfsLayout.alpineArch();
        if (arch.length() == 0) {
            Log.w(TAG, "unsupported ABI for alpine rootfs");
            return;
        }
        installing = true;
        refreshUi();
        backgroundTasks.execute("linecode-linux-install", () -> {
            Exception failure = null;
            try {
                installInternal(arch);
            } catch (Exception e) {
                failure = e;
                Log.e(TAG, "linux env install failed", e);
            }
            final Exception error = failure;
            installing = false;
            post(() -> {
                if (error != null && host != null) {
                    // 错误细节进日志即可；UI 状态由 state() 重新计算（MISSING）
                    Log.w(TAG, "install failure surfaced: " + error.getMessage());
                }
                refreshUi();
            });
        });
    }

    public void delete() {
        LinuxRootfsLayout.deleteAll(context.getFilesDir());
        refreshUi();
    }

    private void installInternal(String arch) throws Exception {
        File filesDir = context.getFilesDir();

        byte[] tarGz = SimpleHttpClient.download(
                LinuxRootfsLayout.minirootfsUrl(arch),
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS).bytes;
        String expectedSha256 = SimpleHttpClient.get(
                LinuxRootfsLayout.minirootfsSha256Url(arch),
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS).trim();
        String actualSha256 = sha256Hex(tarGz);
        if (expectedSha256.length() > 0 && !expectedSha256.equalsIgnoreCase(actualSha256)) {
            throw new IOException("sha256 mismatch: expected " + expectedSha256 + " got " + actualSha256);
        }

        File tmpDir = LinuxRootfsLayout.tmpDir(filesDir);
        LinuxRootfsLayout.deleteAll(filesDir);
        AlpineTarExtractor.extract(tarGz, tmpDir);
        if (!new File(tmpDir, "bin/busybox").isFile()) {
            throw new IOException("rootfs sanity check failed: bin/busybox missing");
        }

        // DNS：Android 宿主无 /etc/resolv.conf，proot -R 不会带来可用的
        writeResolvConf(tmpDir);

        File rootfsDir = LinuxRootfsLayout.rootfsDir(filesDir);
        if (!tmpDir.renameTo(rootfsDir)) {
            throw new IOException("rootfs rename failed");
        }

        // SONAME 链接 + PROOT_TMP_DIR 目录（真机验证的运行时布局）
        String prootBin = new File(
                context.getApplicationInfo().nativeLibraryDir, "libproot.so").getAbsolutePath();
        ProotCommandBuilder.ensureRuntimeLayout(prootBin, rootfsDir);

        boolean supported = probeProot(rootfsDir);
        if (supported) {
            preinstallToolPackages(prootBin, rootfsDir);
        }
        LinuxRootfsLayout.writeMeta(filesDir, new LinuxRootfsLayout.Meta(
                LinuxRootfsLayout.ALPINE_PATCH, arch,
                System.currentTimeMillis(), supported));
    }

    /**
     * 预装高频工具包（git/ripgrep/fd/jq/wget/unzip/openssh-client，约 35MB），
     * 让 Linux 环境装完即可干活。失败不阻断安装——AI 后续可自行 apk add。
     */
    private void preinstallToolPackages(String prootBin, File rootfsDir) {
        try {
            Object found = ipcProviderManager == null
                    ? null : ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL);
            if (!(found instanceof TerminalIpcProvider)) {
                return;
            }
            TerminalIpcProvider provider = (TerminalIpcProvider) found;
            provider.executeShell(
                    ProotCommandBuilder.build(prootBin, rootfsDir, "",
                            "apk add git ripgrep fd jq wget unzip openssh-client"),
                    "", PREINSTALL_TIMEOUT_MS, null);
        } catch (Exception e) {
            Log.w(TAG, "preinstall tool packages failed (non-fatal): " + e.getMessage());
        }
    }

    private void writeResolvConf(File rootfsDir) throws IOException {
        File etc = new File(rootfsDir, "etc");
        if (!etc.isDirectory()) {
            throw new IOException("rootfs etc/ missing");
        }
        File resolv = new File(etc, "resolv.conf");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(resolv)) {
            output.write(("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 在 :terminal 进程跑 guest busybox 探测 proot 可用性。 */
    private boolean probeProot(File rootfsDir) {
        if (ipcProviderManager == null) {
            return false;
        }
        Object found = ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL);
        if (!(found instanceof TerminalIpcProvider)) {
            return false;
        }
        TerminalIpcProvider provider = (TerminalIpcProvider) found;
        if (!provider.isBound()) {
            return false;
        }
        String prootBin = new File(
                context.getApplicationInfo().nativeLibraryDir, "libproot.so").getAbsolutePath();
        return provider.probeProot(prootBin, rootfsDir);
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static long directorySize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        long total = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                total += child.isDirectory() ? directorySize(child) : child.length();
            }
        }
        return total;
    }

    private void refreshUi() {
        if (host != null) {
            host.refreshLinuxEnvUi();
        }
    }

    private void post(Runnable action) {
        if (host != null && !host.isMainThread()) {
            host.postToMainThread(action);
        } else {
            action.run();
        }
    }
}
