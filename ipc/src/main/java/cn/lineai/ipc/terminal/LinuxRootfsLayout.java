package cn.lineai.ipc.terminal;

import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * proot + Alpine rootfs 的磁盘布局与元数据。
 *
 * <p>目录结构（全部位于宿主 app 的 filesDir，主进程与 :terminal 进程同 uid 可互访）：
 * <ul>
 *   <li>{@code files/proot/alpine/} — Alpine mini rootfs 解压根</li>
 *   <li>{@code files/proot/alpine.tmp/} — 解压临时目录，成功后原子 rename</li>
 *   <li>{@code files/proot/rootfs.meta.json} — 安装元数据</li>
 * </ul></p>
 */
public final class LinuxRootfsLayout {

    public static final String DIR_NAME = "proot";
    public static final String ROOTFS_NAME = "alpine";
    public static final String META_NAME = "rootfs.meta.json";

    public static final String ALPINE_VERSION = "3.20";
    public static final String ALPINE_PATCH = "3.20.3";

    private LinuxRootfsLayout() {
    }

    /** rootfs 解压根目录。 */
    public static File rootfsDir(File filesDir) {
        return new File(baseDir(filesDir), ROOTFS_NAME);
    }

    /** 解压临时目录。 */
    public static File tmpDir(File filesDir) {
        return new File(baseDir(filesDir), ROOTFS_NAME + ".tmp");
    }

    /** 元数据文件。 */
    public static File metaFile(File filesDir) {
        return new File(baseDir(filesDir), META_NAME);
    }

    private static File baseDir(File filesDir) {
        return new File(filesDir, DIR_NAME);
    }

    /** rootfs 是否已解压（alpine/bin 目录存在视为有效）。 */
    public static boolean isInstalled(File filesDir) {
        return new File(rootfsDir(filesDir), "bin").isDirectory();
    }

    /**
     * 设备 ABI → Alpine arch。返回空串表示无可用 rootfs（不支持的 ABI）。
     * 映射：arm64-v8a→aarch64、armeabi-v7a→armv7、x86_64→x86_64。
     */
    public static String alpineArch() {
        String[] abis = Build.SUPPORTED_ABIS == null ? new String[0] : Build.SUPPORTED_ABIS;
        for (String abi : abis) {
            if ("arm64-v8a".equals(abi)) return "aarch64";
            if ("x86_64".equals(abi)) return "x86_64";
            if ("armeabi-v7a".equals(abi)) return "armv7";
        }
        return "";
    }

    /** minirootfs 下载 URL（HTTPS，dl-cdn.alpinelinux.org）。 */
    public static String minirootfsUrl(String arch) {
        return "https://dl-cdn.alpinelinux.org/alpine/v" + ALPINE_VERSION
                + "/releases/" + arch + "/alpine-minirootfs-" + ALPINE_PATCH + "-" + arch + ".tar.gz";
    }

    /** 国内镜像（清华 TUNA）下载 URL；内容与官方 CDN 字节一致。 */
    public static String minirootfsMirrorUrl(String arch) {
        return "https://mirrors.tuna.tsinghua.edu.cn/alpine/v" + ALPINE_VERSION
                + "/releases/" + arch + "/alpine-minirootfs-" + ALPINE_PATCH + "-" + arch + ".tar.gz";
    }

    /** 同目录 sha256 校验文件 URL。 */
    public static String minirootfsSha256Url(String arch) {
        return minirootfsUrl(arch) + ".sha256";
    }

    /** APK assets 内置的 rootfs 文件名（assets/rootfs/ 下）。 */
    public static String bundledAssetName(String arch) {
        return "rootfs/alpine-minirootfs-" + ALPINE_PATCH + "-" + arch + ".tar.gz";
    }

    /** 安装元数据。 */
    public static final class Meta {
        public final String alpineVersion;
        public final String arch;
        public final long installedAt;
        public final boolean prootSupported;
        /** proot 探测失败原因（supported=true 时为空）。 */
        public final String prootUnsupportedReason;

        public Meta(String alpineVersion, String arch, long installedAt, boolean prootSupported) {
            this(alpineVersion, arch, installedAt, prootSupported, "");
        }

        public Meta(String alpineVersion, String arch, long installedAt, boolean prootSupported,
                    String prootUnsupportedReason) {
            this.alpineVersion = alpineVersion == null ? "" : alpineVersion;
            this.arch = arch == null ? "" : arch;
            this.installedAt = installedAt;
            this.prootSupported = prootSupported;
            this.prootUnsupportedReason = prootUnsupportedReason == null ? "" : prootUnsupportedReason;
        }
    }

    /** 读元数据；不存在或损坏返回 null。 */
    public static Meta readMeta(File filesDir) {
        File file = metaFile(filesDir);
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = 0;
            while (read < buffer.length) {
                int n = input.read(buffer, read, buffer.length - read);
                if (n <= 0) break;
                read += n;
            }
            JSONObject json = new JSONObject(new String(buffer, 0, read, StandardCharsets.UTF_8));
            return new Meta(
                    json.optString("alpineVersion", ""),
                    json.optString("arch", ""),
                    json.optLong("installedAt", 0L),
                    json.optBoolean("prootSupported", false),
                    json.optString("prootUnsupportedReason", ""));
        } catch (Exception e) {
            return null;
        }
    }

    /** 写元数据（原子写：tmp + rename）。 */
    public static void writeMeta(File filesDir, Meta meta) throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("alpineVersion", meta.alpineVersion);
            json.put("arch", meta.arch);
            json.put("installedAt", meta.installedAt);
            json.put("prootSupported", meta.prootSupported);
            json.put("prootUnsupportedReason", meta.prootUnsupportedReason);
        } catch (Exception e) {
            throw new IOException(e);
        }
        File target = metaFile(filesDir);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create meta dir: " + parent);
        }
        File tmp = new File(parent, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(tmp)) {
            output.write(json.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("meta rename failed");
        }
    }

    /** 删除 rootfs 与元数据（清理入口）。 */
    public static void deleteAll(File filesDir) {
        deleteRecursive(rootfsDir(filesDir));
        deleteRecursive(tmpDir(filesDir));
        //noinspection ResultOfMethodCallIgnored
        metaFile(filesDir).delete();
    }

    /** 递归删除（public：安装流程清残留目标也用）。 */
    public static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /** 递归复制目录（rename 被占用文件阻断时的降级迁移）。 */
    public static boolean copyDirectory(File source, File target) {
        if (source == null || !source.isDirectory()) {
            return false;
        }
        if (target.exists() && !target.isDirectory()) {
            return false;
        }
        if (!target.exists() && !target.mkdirs()) {
            return false;
        }
        File[] children = source.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            File dest = new File(target, child.getName());
            if (child.isDirectory()) {
                if (!copyDirectory(child, dest)) {
                    return false;
                }
            } else if (child.isFile()) {
                try (FileInputStream input = new FileInputStream(child);
                     FileOutputStream output = new FileOutputStream(dest)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        output.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    return false;
                }
            }
            // 符号链接（isFile/isDirectory 均 false）：跳过，rootfs 内链接由使用方重建或忽略
        }
        return true;
    }
}
