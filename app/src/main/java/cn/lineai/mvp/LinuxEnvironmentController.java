package cn.lineai.mvp;

import android.content.Context;
import android.util.Log;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.terminal.AlpineTarExtractor;
import cn.lineai.ipc.terminal.LinuxRootfsLayout;
import cn.lineai.ipc.terminal.ProotCommandBuilder;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import cn.lineai.security.SimpleHttpClient;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Linux 环境（proot + Alpine rootfs）生命周期控制器。
 *
 * <p>rootfs 获取顺序：APK assets 内置（离线可用）→ 清华镜像下载 → 官方 CDN 回退。
 * 安装阶段经 {@link Host#onInstallProgress} 实时回调 UI（下载百分比/解压/配置/工具包）；
 * 失败原因记录在 {@link #lastError} 供界面显示与重试。</p>
 */
public final class LinuxEnvironmentController {

    /** rootfs 状态，供 UI 显示。 */
    public enum State {
        MISSING, INSTALLING, INSTALLED, UNSUPPORTED, FAILED
    }

    /** 安装阶段 code（UI 端翻译；DOWNLOAD 时 detail 为百分比）。 */
    public static final String PHASE_BUNDLED = "bundled";
    public static final String PHASE_DOWNLOAD = "download";
    public static final String PHASE_VERIFY = "verify";
    public static final String PHASE_EXTRACT = "extract";
    public static final String PHASE_CONFIGURE = "configure";
    public static final String PHASE_TOOLS = "tools";
    public static final String PHASE_DONE = "done";

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
    private volatile boolean installing;
    private volatile String installPhase = "";
    private volatile String lastError = "";

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
            return lastError.length() > 0 ? State.FAILED : State.MISSING;
        }
        if (!meta.prootSupported && meta.prootUnsupportedReason.length() > 0) {
            lastError = meta.prootUnsupportedReason;
        }
        return meta.prootSupported ? State.INSTALLED : State.UNSUPPORTED;
    }

    /** 当前安装阶段（installing 时有效）。 */
    public String installPhase() {
        return installPhase;
    }

    /** 最近一次安装失败原因；成功后清空。 */
    public String lastError() {
        return lastError;
    }

    /** 已安装 rootfs 占用字节数；未安装返回 0。 */
    public long installedSizeBytes() {
        return directorySize(LinuxRootfsLayout.rootfsDir(context.getFilesDir()));
    }

    /** 启动安装（assets/下载 → 校验 → 解压 → 配置 → probe → 工具包 → meta）。幂等：安装中忽略。 */
    public void install() {
        if (installing) {
            return;
        }
        String arch = LinuxRootfsLayout.alpineArch();
        if (arch.length() == 0) {
            lastError = "unsupported device ABI";
            Log.w(TAG, "unsupported ABI for alpine rootfs");
            refreshUi();
            return;
        }
        installing = true;
        lastError = "";
        installPhase = "";
        refreshUi();
        backgroundTasks.execute("linecode-linux-install", () -> {
            Exception failure = null;
            try {
                installInternal(arch);
            } catch (Exception e) {
                failure = e;
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                Log.e(TAG, "linux env install failed", e);
            }
            final boolean ok = failure == null;
            installing = false;
            installPhase = ok ? PHASE_DONE : "";
            post(this::refreshUi);
        });
    }

    public void delete() {
        LinuxRootfsLayout.deleteAll(context.getFilesDir());
        lastError = "";
        refreshUi();
    }

    /** Alpine .sha256 文件格式为 "<hash>  <filename>"；只取哈希字段。包级可见供测试。 */
    static String extractSha256(String raw) {
        String value = raw == null ? "" : raw.trim();
        int separator = value.indexOf(' ');
        if (separator > 0) {
            value = value.substring(0, separator);
        }
        return value;
    }

    private void installInternal(String arch) throws Exception {
        File filesDir = context.getFilesDir();

        byte[] tarGz = obtainRootfs(arch);

        reportProgress(PHASE_VERIFY, "");
        // Alpine .sha256 文件格式为 "<hash>  <filename>"（GNU sha256sum 风格）；只取哈希字段
        String expectedSha256 = extractSha256(SimpleHttpClient.get(
                LinuxRootfsLayout.minirootfsSha256Url(arch),
                15000, 30000));
        String actualSha256 = sha256Hex(tarGz);
        if (expectedSha256.length() > 0 && !expectedSha256.equalsIgnoreCase(actualSha256)) {
            throw new IOException("sha256 mismatch: expected " + expectedSha256 + " got " + actualSha256);
        }

        reportProgress(PHASE_EXTRACT, "");
        File tmpDir = LinuxRootfsLayout.tmpDir(filesDir);
        LinuxRootfsLayout.deleteAll(filesDir);
        AlpineTarExtractor.extract(tarGz, tmpDir);
        if (!new File(tmpDir, "bin/busybox").isFile()) {
            throw new IOException("rootfs sanity check failed: bin/busybox missing");
        }

        reportProgress(PHASE_CONFIGURE, "");
        // DNS：Android 宿主无 /etc/resolv.conf，proot -R 不会带来可用的
        writeResolvConf(tmpDir);

        File rootfsDir = LinuxRootfsLayout.rootfsDir(filesDir);
        if (rootfsDir.exists()) {
            // 上轮失败安装可能残留非空目录（文件句柄未释放时 deleteRecursive 部分失败），
            // rename 到已存在目标会失败——先清目标再试
            LinuxRootfsLayout.deleteRecursive(rootfsDir);
        }
        if (!tmpDir.renameTo(rootfsDir)) {
            // 兜底：目标仍存在（删除被占用文件失败）→ 复制式迁移
            boolean moved = tmpDir.isDirectory()
                    && LinuxRootfsLayout.copyDirectory(tmpDir, rootfsDir);
            if (!moved) {
                throw new IOException("rootfs rename failed"
                        + (rootfsDir.exists() ? " (target exists and is locked)" : "")
                        + "; target=" + rootfsDir.getAbsolutePath()
                        + " free=" + filesDir.getFreeSpace() / (1024 * 1024) + "MB");
            }
            LinuxRootfsLayout.deleteRecursive(tmpDir);
        }

        // SONAME 链接 + PROOT_TMP_DIR 目录（真机验证的运行时布局）
        String prootBin = new File(
                context.getApplicationInfo().nativeLibraryDir, "libproot.so").getAbsolutePath();
        ProotCommandBuilder.ensureRuntimeLayout(prootBin, rootfsDir);

        TerminalIpcProvider.ProbeResult probe = probeProot(rootfsDir);
        boolean supported = probe.supported;
        if (supported) {
            reportProgress(PHASE_TOOLS, "");
            preinstallToolPackages(prootBin, rootfsDir);
        } else {
            // 失败原因进 lastError（UI 回显）与 meta（重启后仍可见）
            lastError = probe.output;
            Log.w(TAG, "proot unsupported on this device: " + probe.output);
        }
        LinuxRootfsLayout.writeMeta(filesDir, new LinuxRootfsLayout.Meta(
                LinuxRootfsLayout.ALPINE_PATCH, arch,
                System.currentTimeMillis(), supported,
                supported ? "" : probe.output));
    }

    /**
     * rootfs 获取：APK assets 内置（离线）→ 清华镜像（带进度）→ 官方 CDN。
     * sha256 在调用方统一校验。
     */
    private byte[] obtainRootfs(String arch) throws Exception {
        // 1) assets 内置
        try (InputStream input = context.getAssets().open(LinuxRootfsLayout.bundledAssetName(arch))) {
            reportProgress(PHASE_BUNDLED, "");
            return readAll(input);
        } catch (IOException noAsset) {
            // 老 APK / 未来瘦身场景，走下载
        }
        // 2) 清华镜像
        reportProgress(PHASE_DOWNLOAD, "0%");
        try {
            return SimpleHttpClient.downloadWithProgress(
                    LinuxRootfsLayout.minirootfsMirrorUrl(arch),
                    CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS,
                    (read, total) -> reportProgress(PHASE_DOWNLOAD,
                            total > 0 ? (read * 100 / total) + "%" : (read / 1024) + " KB")).bytes;
        } catch (Exception mirrorFailure) {
            Log.w(TAG, "mirror download failed, falling back to official CDN: " + mirrorFailure.getMessage());
        }
        // 3) 官方 CDN
        reportProgress(PHASE_DOWNLOAD, "0%");
        return SimpleHttpClient.downloadWithProgress(
                LinuxRootfsLayout.minirootfsUrl(arch),
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS,
                (read, total) -> reportProgress(PHASE_DOWNLOAD,
                        total > 0 ? (read * 100 / total) + "%" : (read / 1024) + " KB")).bytes;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(4 * 1024 * 1024);
        byte[] chunk = new byte[65536];
        int read;
        while ((read = input.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private void reportProgress(String phase, String detail) {
        installPhase = detail.length() == 0 ? phase : phase + ":" + detail;
        post(this::refreshUi);
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
    /** 探测 proot；provider 不可用时返回带原因的失败结果（而非静默 false）。 */
    private TerminalIpcProvider.ProbeResult probeProot(File rootfsDir) {
        if (ipcProviderManager == null) {
            return new TerminalIpcProvider.ProbeResult(false, "terminal provider manager unavailable");
        }
        Object found = ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL);
        if (!(found instanceof TerminalIpcProvider)) {
            return new TerminalIpcProvider.ProbeResult(false, "built-in terminal provider not bound yet");
        }
        TerminalIpcProvider provider = (TerminalIpcProvider) found;
        if (!provider.isBound()) {
            return new TerminalIpcProvider.ProbeResult(false, "built-in terminal provider not bound yet");
        }
        String prootBin = new File(
                context.getApplicationInfo().nativeLibraryDir, "libproot.so").getAbsolutePath();
        return provider.probeProotDetailed(prootBin, rootfsDir);
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
