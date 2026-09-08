package cn.lineai.ipc.terminal;

import java.io.File;

/**
 * proot 包装命令构造器。
 *
 * <p>把用户命令包装成 proot 调用，交由现有 {@code executeShell} AIDL 通道执行。
 * 真机验证过的完整形态（termux 构建 proot 在本 app uid 下的运行要求）：</p>
 *
 * <pre>
 * LD_LIBRARY_PATH=<symDir>:<libDir> PROOT_LOADER=<libDir>/libproot_loader.so \
 *   PROOT_TMP_DIR=<tmpDir> <libDir>/libproot.so \
 *   -r <rootfs> -0 --link2symlink -b /dev -b /proc -b /sys -b /system \
 *   -b /storage -b /sdcard \
 *   -w <cwd> /bin/sh -c 'export ...; <command>'
 * </pre>
 *
 * <p>关键点：
 * <ul>
 *   <li>{@code symDir}（filesDir/proot/lib）内建 {@code libtalloc.so.2}、
 *       {@code libandroid-shmem.so} 符号链接指向 nativeLibraryDir 的库——
 *       Android linker 按 SONAME 查找（libtalloc.so.2），jniLibs 的
 *       {@code lib} 前缀命名规则无法承载 soname，运行时以 symlink 补齐。</li>
 *   <li>{@code PROOT_TMP_DIR} 指向 app 可写目录（默认 /tmp 不可写，
 *       proot 报 "can't create glue rootfs"）。</li>
 *   <li>变量赋值前缀在 POSIX sh 中合法，无需改动 ProcessBuilder/AIDL 环境传递。</li>
 *   <li>{@code -r 而非 -R}：-R 会在推荐绑定中把宿主 /tmp（tmpfs，ColorOS 等系统上
 *       属 shell uid、app 不可写）盖到 guest /tmp 上，apt/dpkg 建临时文件直接 EPERM；
 *       -r 只换根，/dev /proc /sys 由我们显式绑定，guest /tmp 用 rootfs 自带目录
 *       （真机 OPPO PJA110 验证）。</li>
 *   <li>{@code -0} 伪 root：apk/apt 等按 euid==0 分支的工具在非 root 环境下行为正确
 *       （借鉴 termux proot-distro 的标准用法）。</li>
 *   <li>{@code --link2symlink}：Android 数据目录文件系统不支持硬链接，dpkg 备份
 *       （link status → status-old）与 apk 解包的硬链接操作会直接 EPERM；
 *       该扩展使 proot 把硬链接降级为符号链接。</li>
 *   <li>{@code -b /system}：容器内可访问宿主系统库（部分动态链接的宿主二进制需要）。</li>
 *   <li>{@code -b /storage} 使 host/guest 路径一致，工作区（/storage/emulated/0/...）
 *       的 cwd 语义对 guest 直接生效。</li>
 * </ul></p>
 */
public final class ProotCommandBuilder {

    private static final String GUEST_PATH =
            "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

    private ProotCommandBuilder() {
    }

    /**
     * 构造完整包装命令（无代理）。
     *
     * @param prootBin proot 可执行文件绝对路径（nativeLibraryDir/libproot.so）
     * @param rootfsDir rootfs 解压根目录
     * @param cwd       期望工作目录（host 路径）；不在 /storage 树内时回退 /root
     * @param command   用户原始命令
     */
    public static String build(String prootBin, File rootfsDir, String cwd, String command) {
        return build(prootBin, rootfsDir, cwd, command, "");
    }

    /**
     * 构造完整包装命令。
     *
     * @param proxyUrl 全局 HTTP 代理（http://host:port）；空串表示不代理。
     *                 非空时 guest 内 export http_proxy/https_proxy，apk 等走代理。
     */
    public static String build(String prootBin, File rootfsDir, String cwd, String command, String proxyUrl) {
        String libDir = parentDir(prootBin);
        String loader = libDir + "/libproot_loader.so";
        String rootfs = rootfsDir == null ? "" : rootfsDir.getAbsolutePath();
        // shell 命令内的路径必须是字面 '/'（File 在部分平台会转成 '\')
        rootfs = rootfs.replace('\\', '/');
        String symDir = siblingDir(rootfs, "lib");
        String tmpDir = siblingDir(rootfs, "tmp");
        String workDir = guestWorkDir(cwd);
        return "LD_LIBRARY_PATH=" + shellQuote(symDir + ":" + libDir)
                + " PROOT_LOADER=" + shellQuote(loader)
                + " PROOT_TMP_DIR=" + shellQuote(tmpDir)
                + " " + shellQuote(prootBin)
                + " -r " + shellQuote(rootfs)
                + " -0 --link2symlink"
                + " -b /dev -b /proc -b /sys -b /system"
                + " -b /storage -b /sdcard"
                + " -w " + shellQuote(workDir)
                + " /bin/sh -c " + shellQuote(guestScript(command, proxyUrl));
    }

    /**
     * 确保 symDir/tmpDir 及 SONAME 链接就绪（幂等，且自愈 APK 重装后的路径漂移）。
     * 调用方（安装流程/执行路径）在跑 proot 前调用一次。
     *
     * @param prootBin proot 可执行文件路径
     * @param rootfsDir rootfs 目录（symDir/tmpDir 与其同级，位于 filesDir/proot/ 下）
     */
    public static void ensureRuntimeLayout(String prootBin, File rootfsDir) throws java.io.IOException {
        if (prootBin == null || rootfsDir == null) {
            return;
        }
        File prootDir = rootfsDir.getParentFile();
        if (prootDir == null) {
            return;
        }
        File libDir = new File(rootfsDir.getParentFile(), "lib");
        File tmpDir = new File(rootfsDir.getParentFile(), "tmp");
        //noinspection ResultOfMethodCallIgnored
        libDir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        tmpDir.mkdirs();
        // APK 重装后 nativeLibraryDir 变化：链接失效则重建
        ensureLink(new File(libDir, "libtalloc.so.2"),
                new File(parentDir(prootBin), "libtalloc.so"));
        ensureLink(new File(libDir, "libandroid-shmem.so"),
                new File(parentDir(prootBin), "libandroid-shmem.so"));
    }

    /** 目标不存在或指向变化时（重新）创建符号链接。 */
    private static void ensureLink(File link, File target) throws java.io.IOException {
        if (!target.isFile()) {
            return;
        }
        String targetPath = target.getAbsolutePath();
        try {
            String current = android.system.Os.readlink(link.getAbsolutePath());
            if (targetPath.equals(current)) {
                return; // 已指向正确目标
            }
        } catch (Exception notALinkOrMissing) {
            // 无链接或读取失败 → 下方重建
        }
        //noinspection ResultOfMethodCallIgnored
        link.delete();
        try {
            android.system.Os.symlink(targetPath, link.getAbsolutePath());
        } catch (Exception e) {
            throw new java.io.IOException("cannot link " + link + " -> " + target, e);
        }
    }

    /** rootfs 同级目录名（filesDir/proot/<name>）。 */
    private static String siblingDir(String rootfsPath, String name) {
        int index = rootfsPath.lastIndexOf('/');
        return index > 0 ? rootfsPath.substring(0, index) + "/" + name : name;
    }

    private static String parentDir(String path) {
        int index = path == null ? -1 : path.lastIndexOf('/');
        return index > 0 ? path.substring(0, index) : ".";
    }

    /** guest 内执行的脚本：注入 PATH/HOME/TERM（及可选代理）后运行原命令。 */
    static String guestScript(String command, String proxyUrl) {
        StringBuilder script = new StringBuilder();
        script.append("export HOME=/root TERM=xterm-256color PATH=").append(GUEST_PATH);
        if (proxyUrl != null && proxyUrl.length() > 0) {
            script.append(" http_proxy=").append(proxyUrl)
                    .append(" https_proxy=").append(proxyUrl);
        }
        script.append("; ").append(command);
        return script.toString();
    }

    /** host cwd → guest cwd；非 /storage 前缀回退 /root。 */
    static String guestWorkDir(String cwd) {
        if (cwd != null && (cwd.startsWith("/storage/") || cwd.equals("/storage")
                || cwd.startsWith("/sdcard/") || cwd.equals("/sdcard"))) {
            return cwd;
        }
        return "/root";
    }

    /** POSIX 单引号转义。 */
    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
