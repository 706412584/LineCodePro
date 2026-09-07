package cn.lineai.ipc.terminal;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public final class LinuxDistroTest {

    @Test
    public void byIdFallsBackToAlpineForUnknownOrEmpty() {
        Assert.assertEquals(LinuxDistro.ALPINE, LinuxDistro.byId(null));
        Assert.assertEquals(LinuxDistro.ALPINE, LinuxDistro.byId(""));
        Assert.assertEquals(LinuxDistro.ALPINE, LinuxDistro.byId("fedora"));
        Assert.assertEquals(LinuxDistro.UBUNTU, LinuxDistro.byId("ubuntu"));
    }

    @Test
    public void availableDistrosExcludeDebian() {
        List<LinuxDistro> distros = LinuxDistro.availableDistros();
        Assert.assertTrue(distros.contains(LinuxDistro.ALPINE));
        Assert.assertTrue(distros.contains(LinuxDistro.UBUNTU));
        Assert.assertFalse(distros.contains(LinuxDistro.DEBIAN));
        Assert.assertFalse(LinuxDistro.DEBIAN.available);
    }

    @Test
    public void alpineUrlsPointAtAlpineMirrorsAndCdn() {
        String[] urls = LinuxDistro.ALPINE.downloadUrls("aarch64");
        Assert.assertEquals(5, urls.length);
        Assert.assertTrue(urls[0].startsWith("https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.20/releases/aarch64/"));
        Assert.assertTrue(urls[urls.length - 1].startsWith("https://dl-cdn.alpinelinux.org/alpine/"));
        for (String url : urls) {
            Assert.assertTrue(url, url.endsWith("alpine-minirootfs-3.20.3-aarch64.tar.gz"));
        }
        Assert.assertEquals(
                "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz.sha256",
                LinuxDistro.ALPINE.sha256Url("aarch64"));
    }

    @Test
    public void ubuntuUrlsPointAtUbuntuMirrorsAndCdimage() {
        String[] urls = LinuxDistro.UBUNTU.downloadUrls("arm64");
        Assert.assertEquals(4, urls.length);
        Assert.assertTrue(urls[0].startsWith(
                "https://repo.huaweicloud.com/ubuntu-cdimage/ubuntu-base/releases/22.04/release/"));
        Assert.assertTrue(urls[urls.length - 1].startsWith("https://cdimage.ubuntu.com/ubuntu-base/"));
        for (String url : urls) {
            Assert.assertTrue(url, url.endsWith("ubuntu-base-22.04.5-base-arm64.tar.gz"));
        }
        // Ubuntu 跳过 sha256 强校验
        Assert.assertEquals("", LinuxDistro.UBUNTU.sha256Url("arm64"));
    }

    @Test
    public void guestArchMappingPerDistro() {
        // Robolectric 默认 SUPPORTED_ABIS 为空 → guestArch 返回空串是合法行为；
        // 这里校验输出域落在该发行版的合法 arch 集合内
        String arch = LinuxDistro.ALPINE.guestArch();
        Assert.assertTrue(arch.equals("") || arch.equals("aarch64")
                || arch.equals("armv7") || arch.equals("x86_64"));
        String ubuntuArch = LinuxDistro.UBUNTU.guestArch();
        Assert.assertTrue(ubuntuArch.equals("") || ubuntuArch.equals("arm64")
                || ubuntuArch.equals("armhf") || ubuntuArch.equals("amd64"));
    }

    @Test
    public void sanityAndProbeCommandsPerDistro() {
        Assert.assertEquals("bin/busybox", LinuxDistro.ALPINE.sanityCheckFile());
        Assert.assertEquals("bin/sh", LinuxDistro.UBUNTU.sanityCheckFile());
        Assert.assertEquals("busybox echo __lineai_proot_ok__", LinuxDistro.ALPINE.probeCommand());
        Assert.assertEquals("echo __lineai_proot_ok__", LinuxDistro.UBUNTU.probeCommand());
    }

    @Test
    public void preinstallCommandsPerPackageManager() {
        Assert.assertTrue(LinuxDistro.ALPINE.preinstallCommand().startsWith("apk add "));
        Assert.assertTrue(LinuxDistro.UBUNTU.preinstallCommand().contains("apt-get update"));
        Assert.assertTrue(LinuxDistro.UBUNTU.preinstallCommand().contains("fd-find"));
        Assert.assertFalse(LinuxDistro.UBUNTU.preinstallCommand().contains("apk"));
        Assert.assertEquals("apk", LinuxDistro.ALPINE.packageManagerName());
        Assert.assertEquals("apt-get", LinuxDistro.UBUNTU.packageManagerName());
    }

    @Test
    public void bundledAssetOnlyForAlpine() {
        Assert.assertEquals("rootfs/alpine-minirootfs-3.20.3-aarch64.tar.gz",
                LinuxDistro.ALPINE.bundledAssetName("aarch64"));
        Assert.assertEquals("", LinuxDistro.UBUNTU.bundledAssetName("arm64"));
        Assert.assertEquals("", LinuxDistro.ALPINE.bundledAssetName(""));
    }
}
