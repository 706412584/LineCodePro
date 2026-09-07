package cn.lineai.ipc.terminal;

import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * proot + 多发行版 rootfs 的磁盘布局与元数据。
 *
 * <p>目录结构（全部位于宿主 app 的 filesDir，主进程与 :terminal 进程同 uid 可互访）：
 * <ul>
 *   <li>{@code files/proot/<distroId>/} — 各发行版 rootfs 解压根（alpine / ubuntu / …）</li>
 *   <li>{@code files/proot/<distroId>.tmp.<millis>/} — 解压临时目录，成功后原子 rename</li>
 *   <li>{@code files/proot/meta-<distroId>.json} — 各发行版安装元数据</li>
 *   <li>{@code files/proot/lib、/tmp} — 运行时共享目录（SONAME 链接 / PROOT_TMP_DIR）</li>
 * </ul>
 * Alpine 的 distroId 即 "alpine"，与历史目录名一致，已装用户零迁移。</p>
 */
public final class LinuxRootfsLayout {

    public static final String DIR_NAME = "proot";
    /** 默认（历史）rootfs 目录名 = Alpine 的 distroId。 */
    public static final String ROOTFS_NAME = "alpine";
    /** 历史元数据文件名（旧版单发行版布局；读 alpine meta 时回退）。 */
    public static final String META_NAME = "rootfs.meta.json";

    private LinuxRootfsLayout() {
    }

    /** rootfs 解压根目录（默认 Alpine，兼容旧调用）。 */
    public static File rootfsDir(File filesDir) {
        return rootfsDir(filesDir, LinuxDistro.ALPINE.id);
    }

    /** 指定发行版的 rootfs 解压根目录。 */
    public static File rootfsDir(File filesDir, String distroId) {
        return new File(baseDir(filesDir), distroName(distroId));
    }

    /** 解压临时目录（固定名；遗留兼容用，勿再用于新安装）。 */
    public static File tmpDir(File filesDir) {
        return tmpDir(filesDir, LinuxDistro.ALPINE.id);
    }

    /** 指定发行版的解压临时目录（固定名遗留形态）。 */
    public static File tmpDir(File filesDir, String distroId) {
        return new File(baseDir(filesDir), distroName(distroId) + ".tmp");
    }

    /**
     * 新安装用的唯一临时目录（&lt;distroId&gt;.tmp.&lt;millis&gt;）。
     * 避开失败安装残留的固定名 tmp（文件被占用时残留可能删不干净，
     * 其中的旧文件会让 symlink 报 EEXIST、写入报 EROFS）。
     */
    public static File freshTmpDir(File filesDir, String distroId) {
        return new File(baseDir(filesDir), distroName(distroId) + ".tmp." + System.currentTimeMillis());
    }

    /** 清理指定发行版的所有临时目录（固定名 + 任意 .tmp.* 变体）。 */
    public static void cleanupTmpDirs(File filesDir, String distroId) {
        File base = baseDir(filesDir);
        File[] children = base.listFiles();
        if (children == null) {
            return;
        }
        String prefix = distroName(distroId) + ".tmp";
        for (File child : children) {
            if (child.getName().startsWith(prefix)) {
                deleteRecursive(child);
            }
        }
    }

    /** 指定发行版的元数据文件。 */
    public static File metaFile(File filesDir, String distroId) {
        return new File(baseDir(filesDir), "meta-" + distroName(distroId) + ".json");
    }

    private static File baseDir(File filesDir) {
        return new File(filesDir, DIR_NAME);
    }

    private static String distroName(String distroId) {
        return distroId == null || distroId.length() == 0 ? LinuxDistro.ALPINE.id : distroId;
    }

    /** rootfs 是否已解压（&lt;distro&gt;/bin 目录存在视为有效；默认 Alpine）。 */
    public static boolean isInstalled(File filesDir) {
        return isInstalled(filesDir, LinuxDistro.ALPINE.id);
    }

    /** 指定发行版的 rootfs 是否已解压。 */
    public static boolean isInstalled(File filesDir, String distroId) {
        return new File(rootfsDir(filesDir, distroId), "bin").isDirectory();
    }

    /** 设备首选 ABI → Alpine guest arch（遗留兼容；新代码用 {@link LinuxDistro#guestArch()}）。 */
    public static String alpineArch() {
        return LinuxDistro.ALPINE.guestArch();
    }

    /** 以下 URL 方法为遗留兼容入口，均委托 {@link LinuxDistro}。 */
    public static String minirootfsUrl(String arch) {
        String[] all = LinuxDistro.ALPINE.downloadUrls(arch);
        return all[all.length - 1];
    }

    public static String[] minirootfsMirrorUrls(String arch) {
        String[] all = LinuxDistro.ALPINE.downloadUrls(arch);
        String[] mirrors = new String[all.length - 1];
        System.arraycopy(all, 0, mirrors, 0, mirrors.length);
        return mirrors;
    }

    public static String minirootfsMirrorUrl(String arch) {
        return minirootfsMirrorUrls(arch)[0];
    }

    public static String minirootfsSha256Url(String arch) {
        return LinuxDistro.ALPINE.sha256Url(arch);
    }

    public static String bundledAssetName(String arch) {
        return LinuxDistro.ALPINE.bundledAssetName(arch);
    }

    /** 安装元数据。 */
    public static final class Meta {
        /** 发行版 id（旧格式缺省视为 alpine）。 */
        public final String distro;
        /** 发行版版本（旧格式回退 alpineVersion 字段）。 */
        public final String version;
        public final String arch;
        public final long installedAt;
        public final boolean prootSupported;
        /** proot 探测失败原因（supported=true 时为空）。 */
        public final String prootUnsupportedReason;

        public Meta(String distro, String version, String arch, long installedAt, boolean prootSupported) {
            this(distro, version, arch, installedAt, prootSupported, "");
        }

        public Meta(String distro, String version, String arch, long installedAt, boolean prootSupported,
                    String prootUnsupportedReason) {
            this.distro = distro == null || distro.length() == 0 ? LinuxDistro.ALPINE.id : distro;
            this.version = version == null ? "" : version;
            this.arch = arch == null ? "" : arch;
            this.installedAt = installedAt;
            this.prootSupported = prootSupported;
            this.prootUnsupportedReason = prootUnsupportedReason == null ? "" : prootUnsupportedReason;
        }
    }

    /**
     * 读指定发行版的元数据；不存在或损坏返回 null。
     * Alpine 回退历史单文件 {@code rootfs.meta.json}（旧版布局）。
     */
    public static Meta readMeta(File filesDir, String distroId) {
        String id = distroName(distroId);
        File file = metaFile(filesDir, id);
        if (!file.isFile() && LinuxDistro.ALPINE.id.equals(id)) {
            file = new File(baseDir(filesDir), META_NAME);
        }
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
            String version = json.optString("version", "");
            if (version.length() == 0) {
                version = json.optString("alpineVersion", "");
            }
            return new Meta(
                    json.optString("distro", LinuxDistro.ALPINE.id),
                    version,
                    json.optString("arch", ""),
                    json.optLong("installedAt", 0L),
                    json.optBoolean("prootSupported", false),
                    json.optString("prootUnsupportedReason", ""));
        } catch (Exception e) {
            return null;
        }
    }

    /** 写指定发行版的元数据（原子写：tmp + rename）。 */
    public static void writeMeta(File filesDir, String distroId, Meta meta) throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("distro", distroName(distroId));
            json.put("version", meta.version);
            // 旧版本 App 读此文件需要 alpineVersion 字段，双写兼容
            if (LinuxDistro.ALPINE.id.equals(distroName(distroId))) {
                json.put("alpineVersion", meta.version);
            }
            json.put("arch", meta.arch);
            json.put("installedAt", meta.installedAt);
            json.put("prootSupported", meta.prootSupported);
            json.put("prootUnsupportedReason", meta.prootUnsupportedReason);
        } catch (Exception e) {
            throw new IOException(e);
        }
        File target = metaFile(filesDir, distroId);
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

    /** 删除指定发行版的 rootfs、临时目录与元数据。 */
    public static void deleteDistro(File filesDir, String distroId) {
        deleteRecursive(rootfsDir(filesDir, distroId));
        cleanupTmpDirs(filesDir, distroId);
        //noinspection ResultOfMethodCallIgnored
        metaFile(filesDir, distroId).delete();
        if (LinuxDistro.ALPINE.id.equals(distroName(distroId))) {
            // 旧版单文件元数据一并清理
            //noinspection ResultOfMethodCallIgnored
            new File(baseDir(filesDir), META_NAME).delete();
        }
    }

    /** 删除全部发行版数据（彻底清理入口；保留运行时共享 lib/tmp）。 */
    public static void deleteAll(File filesDir) {
        File base = baseDir(filesDir);
        File[] children = base.listFiles();
        if (children != null) {
            for (File child : children) {
                String name = child.getName();
                boolean isSharedRuntime = "lib".equals(name) || "tmp".equals(name);
                if (!isSharedRuntime) {
                    deleteRecursive(child);
                }
            }
        }
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
