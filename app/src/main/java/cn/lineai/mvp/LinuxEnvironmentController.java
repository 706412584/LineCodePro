package cn.lineai.mvp;

import android.content.Context;
import android.util.Log;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.terminal.AlpineTarExtractor;
import cn.lineai.ipc.terminal.LinuxDistro;
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

    /** 当前激活的发行版（settings 读取；未知/空回退 Alpine）。 */
    public LinuxDistro activeDistro() {
        return toolSettingsRepository == null
                ? LinuxDistro.ALPINE : LinuxDistro.byId(toolSettingsRepository.getActiveDistroId());
    }

    /** 切换激活发行版（不触发安装；UI 刷新状态显示）。 */
    public void setActiveDistro(String distroId) {
        if (toolSettingsRepository != null) {
            toolSettingsRepository.setActiveDistroId(LinuxDistro.byId(distroId).id);
        }
        refreshUi();
    }

    /** 当前状态（读磁盘 + 内存安装标志，按激活发行版）。 */
    public State state() {
        if (installing) {
            return State.INSTALLING;
        }
        String distroId = activeDistro().id;
        LinuxRootfsLayout.Meta meta = LinuxRootfsLayout.readMeta(context.getFilesDir(), distroId);
        if (meta == null || !LinuxRootfsLayout.isInstalled(context.getFilesDir(), distroId)) {
            return lastError.length() > 0 ? State.FAILED : State.MISSING;
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

    /** 已安装 rootfs 占用字节数（按激活发行版）；未安装返回 0。 */
    public long installedSizeBytes() {
        return directorySize(LinuxRootfsLayout.rootfsDir(context.getFilesDir(), activeDistro().id));
    }

    /** 启动安装（assets/下载 → 校验 → 解压 → 配置 → probe → 工具包 → meta）。幂等：安装中忽略。 */
    public void install() {
        if (installing) {
            return;
        }
        LinuxDistro distro = activeDistro();
        String arch = distro.guestArch();
        if (arch.length() == 0) {
            lastError = "unsupported device ABI for " + distro.id;
            Log.w(TAG, "unsupported ABI for " + distro.id + " rootfs");
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
                installInternal(distro, arch);
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

    /** 删除当前激活发行版（另一发行版保留，可随时切回）。 */
    public void delete() {
        LinuxRootfsLayout.deleteDistro(context.getFilesDir(), activeDistro().id);
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

    private void installInternal(LinuxDistro distro, String arch) throws Exception {
        File filesDir = context.getFilesDir();
        String distroId = distro.id;

        byte[] tarGz = obtainRootfs(distro, arch);

        reportProgress(PHASE_VERIFY, "");
        // sha256 URL 为空（Ubuntu：镜像不同步 SHA256SUMS）→ 跳过强校验，
        // 完整性由 gzip CRC + ustar 结构 + 末尾 probe 兜底
        String sha256Url = distro.sha256Url(arch);
        if (sha256Url.length() > 0) {
            // Alpine .sha256 文件格式为 "<hash>  <filename>"（GNU sha256sum 风格）；只取哈希字段
            String expectedSha256 = extractSha256(SimpleHttpClient.get(sha256Url, 15000, 30000));
            String actualSha256 = sha256Hex(tarGz);
            if (expectedSha256.length() > 0 && !expectedSha256.equalsIgnoreCase(actualSha256)) {
                throw new IOException("sha256 mismatch: expected " + expectedSha256 + " got " + actualSha256);
            }
        }

        reportProgress(PHASE_EXTRACT, "");
        // 清掉历史失败残留（固定名 tmp 与所有 .tmp.* 变体），再取全新唯一临时目录，
        // 避免残留文件导致 symlink EEXIST / 写入 EROFS
        LinuxRootfsLayout.cleanupTmpDirs(filesDir, distroId);
        File tmpDir = LinuxRootfsLayout.freshTmpDir(filesDir, distroId);
        AlpineTarExtractor.extract(tarGz, tmpDir);
        if (!new File(tmpDir, distro.sanityCheckFile()).isFile()) {
            throw new IOException("rootfs sanity check failed: " + distro.sanityCheckFile() + " missing");
        }

        reportProgress(PHASE_CONFIGURE, "");
        // DNS：Android 宿主无 /etc/resolv.conf，proot -R 不会带来可用的
        writeResolvConf(tmpDir);

        File rootfsDir = LinuxRootfsLayout.rootfsDir(filesDir, distroId);
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

        TerminalIpcProvider.ProbeResult probe = probeProot(rootfsDir, distro.probeCommand());
        boolean supported = probe.supported;
        if (supported) {
            reportProgress(PHASE_TOOLS, "");
            preinstallToolPackages(prootBin, rootfsDir, distro);
        }
        LinuxRootfsLayout.writeMeta(filesDir, distroId, new LinuxRootfsLayout.Meta(
                distroId, distro.version, arch,
                System.currentTimeMillis(), supported));
    }

    /**
     * rootfs 获取：APK assets 内置（仅 Alpine；离线）→ 镜像列表逐个尝试（列表末位为官方源）。
     * 全部失败抛异常（调用方记入 lastError 供 UI 重试）。
     */
    private byte[] obtainRootfs(LinuxDistro distro, String arch) throws Exception {
        // 1) assets 内置
        String assetName = distro.bundledAssetName(arch);
        if (assetName.length() > 0) {
            try (InputStream input = context.getAssets().open(assetName)) {
                reportProgress(PHASE_BUNDLED, "");
                return readAll(input);
            } catch (IOException noAsset) {
                // 老 APK / 未来瘦身场景，走下载
            }
        }
        // 2) 镜像逐个回退（末位为官方源）
        String[] urls = distro.downloadUrls(arch);
        for (String url : urls) {
            reportProgress(PHASE_DOWNLOAD, "0%");
            try {
                return SimpleHttpClient.downloadWithProgress(
                        url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS,
                        (read, total) -> reportProgress(PHASE_DOWNLOAD,
                                total > 0 ? (read * 100 / total) + "%" : (read / 1024) + " KB")).bytes;
            } catch (Exception mirrorFailure) {
                Log.w(TAG, "download failed (" + url + "): " + mirrorFailure.getMessage());
            }
        }
        throw new IOException("all download sources failed for " + distro.id);
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
     * 让 Linux 环境装完即可干活。命令按发行版包管理器（apk / apt-get）分派。
     * 失败不阻断安装——AI 后续可自行补装。
     */
    private void preinstallToolPackages(String prootBin, File rootfsDir, LinuxDistro distro) {
        try {
            Object found = ipcProviderManager == null
                    ? null : ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL);
            if (!(found instanceof TerminalIpcProvider)) {
                return;
            }
            TerminalIpcProvider provider = (TerminalIpcProvider) found;
            provider.executeShell(
                    ProotCommandBuilder.build(prootBin, rootfsDir, "", distro.preinstallCommand()),
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

    /** 在 :terminal 进程跑 guest 探测命令验证 proot 可用性（命令按发行版传入）。 */
    private TerminalIpcProvider.ProbeResult probeProot(File rootfsDir, String probeCommand) {
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
        return provider.probeProotDetailed(prootBin, rootfsDir, probeCommand);
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
