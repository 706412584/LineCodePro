package cn.lineai.ipc.terminal;

import android.os.Build;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置 Linux 环境支持的发行版注册表。
 *
 * <p>每个发行版定义：下载镜像列表、ABI→guest arch 映射、解压后 sanity 检查文件、
 * probe 命令与预装命令。发行版差异全部收敛在本类，调用方（安装控制器 / 命令路由）
 * 只调方法不写 if-distro 分支。</p>
 *
 * <p>DEBIAN 暂不可用：其可靠 base 源（images.linuxcontainers.org）只有 rootfs.tar.xz，
 * 现有解压器仅支持 gzip；待解压器支持 xz 后放开。</p>
 */
public enum LinuxDistro {

    ALPINE("alpine", "Alpine", "3.20.3", true),
    UBUNTU("ubuntu", "Ubuntu", "22.04.5", true),
    DEBIAN("debian", "Debian", "12", false);

    public final String id;
    public final String displayName;
    public final String version;
    public final boolean available;

    LinuxDistro(String id, String displayName, String version, boolean available) {
        this.id = id;
        this.displayName = displayName;
        this.version = version;
        this.available = available;
    }

    /** 按 id 查找；未知或空回退默认 Alpine。 */
    public static LinuxDistro byId(String id) {
        if (id != null && id.length() > 0) {
            for (LinuxDistro distro : values()) {
                if (distro.id.equals(id)) {
                    return distro;
                }
            }
        }
        return ALPINE;
    }

    /** 可安装的发行版列表（UI 选择底单用）。 */
    public static List<LinuxDistro> availableDistros() {
        List<LinuxDistro> result = new ArrayList<>();
        for (LinuxDistro distro : values()) {
            if (distro.available) {
                result.add(distro);
            }
        }
        return result;
    }

    /**
     * 设备首选 ABI → 发行版 guest arch。空串表示该发行版无此架构包。
     * Alpine：aarch64 / armv7 / x86_64；Ubuntu（ubuntu-base 命名）：arm64 / armhf / amd64。
     */
    public String guestArch() {
        String[] abis = Build.SUPPORTED_ABIS == null ? new String[0] : Build.SUPPORTED_ABIS;
        for (String abi : abis) {
            String arch = guestArchFor(abi);
            if (arch.length() > 0) {
                return arch;
            }
        }
        return "";
    }

    private String guestArchFor(String abi) {
        if ("arm64-v8a".equals(abi)) {
            return this == UBUNTU ? "arm64" : "aarch64";
        }
        if ("x86_64".equals(abi)) {
            return this == UBUNTU ? "amd64" : "x86_64";
        }
        if ("armeabi-v7a".equals(abi)) {
            return this == UBUNTU ? "armhf" : "armv7";
        }
        return "";
    }

    /** APK assets 内置 rootfs 文件名（仅 Alpine 内置；其余发行版走下载，返回空串）。 */
    public String bundledAssetName(String guestArch) {
        if (this != ALPINE || guestArch.length() == 0) {
            return "";
        }
        return "rootfs/alpine-minirootfs-" + version + "-" + guestArch + ".tar.gz";
    }

    /**
     * 下载 URL 列表（国内镜像按优先级 + 官方兜底，逐个回退）。
     * Alpine：清华/华为云/中科大/腾讯云 → dl-cdn；Ubuntu：华为云/阿里云/清华 → cdimage。
     */
    public String[] downloadUrls(String guestArch) {
        if (guestArch.length() == 0) {
            return new String[0];
        }
        if (this == ALPINE) {
            String suffix = "alpine/v3.20/releases/" + guestArch
                    + "/alpine-minirootfs-" + version + "-" + guestArch + ".tar.gz";
            return new String[] {
                    "https://mirrors.tuna.tsinghua.edu.cn/" + suffix,
                    "https://repo.huaweicloud.com/" + suffix,
                    "https://mirrors.ustc.edu.cn/" + suffix,
                    "https://mirrors.cloud.tencent.com/" + suffix,
                    "https://dl-cdn.alpinelinux.org/" + suffix,
            };
        }
        if (this == UBUNTU) {
            // 注意：官方 cdimage.ubuntu.com 无 ubuntu-cdimage 前缀（镜像站才有）
            String suffix = "ubuntu-cdimage/ubuntu-base/releases/22.04/release"
                    + "/ubuntu-base-" + version + "-base-" + guestArch + ".tar.gz";
            return new String[] {
                    "https://repo.huaweicloud.com/" + suffix,
                    "https://mirrors.aliyun.com/" + suffix,
                    "https://mirrors.tuna.tsinghua.edu.cn/" + suffix,
                    "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release"
                            + "/ubuntu-base-" + version + "-base-" + guestArch + ".tar.gz",
            };
        }
        return new String[0];
    }

    /**
     * .sha256 校验文件 URL；空串表示跳过强校验。
     * Alpine 官方 CDN 同目录有 .sha256；Ubuntu 的 SHA256SUMS 镜像站不保证同步且回退会混源，
     * 完整性由 gzip CRC + ustar 结构 + 安装末尾 probe 三层兜底。
     */
    public String sha256Url(String guestArch) {
        if (this != ALPINE || guestArch.length() == 0) {
            return "";
        }
        return "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/" + guestArch
                + "/alpine-minirootfs-" + version + "-" + guestArch + ".tar.gz.sha256";
    }

    /** 解压后的 sanity 检查文件（相对 rootfs 根）。 */
    public String sanityCheckFile() {
        return this == ALPINE ? "bin/busybox" : "bin/sh";
    }

    /** 安装完成后的 probe 命令（在 proot 内执行，成功输出即支持）。 */
    public String probeCommand() {
        return this == ALPINE
                ? "busybox echo __lineai_proot_ok__"
                : "echo __lineai_proot_ok__";
    }

    /** 预装高频工具命令（失败不阻断安装；AI 后续可自行补装）。 */
    public String preinstallCommand() {
        if (this == ALPINE) {
            return "apk add git ripgrep fd jq wget unzip openssh-client";
        }
        return "apt-get update -qq && DEBIAN_FRONTEND=noninteractive "
                + "apt-get install -y -qq --no-install-recommends "
                + "git ripgrep fd-find jq wget unzip openssh-client";
    }

    /** 预装包管理器名（提示文案用：apk / apt-get）。 */
    public String packageManagerName() {
        return this == ALPINE ? "apk" : "apt-get";
    }
}
