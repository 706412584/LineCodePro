package cn.lineai.ipc.terminal;

import android.content.Context;
import java.io.File;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public final class LinuxRootfsLayoutTest {

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    @Test
    public void metaRoundTrip() throws Exception {
        File filesDir = context().getFilesDir();
        LinuxRootfsLayout.Meta meta = new LinuxRootfsLayout.Meta(
                "alpine", "3.20.3", "aarch64", 1234567890L, true);
        LinuxRootfsLayout.writeMeta(filesDir, "alpine", meta);

        LinuxRootfsLayout.Meta read = LinuxRootfsLayout.readMeta(filesDir, "alpine");
        Assert.assertNotNull(read);
        Assert.assertEquals("alpine", read.distro);
        Assert.assertEquals("3.20.3", read.version);
        Assert.assertEquals("aarch64", read.arch);
        Assert.assertEquals(1234567890L, read.installedAt);
        Assert.assertTrue(read.prootSupported);
    }

    @Test
    public void ubuntuMetaRoundTripPerDistro() throws Exception {
        File filesDir = context().getFilesDir();
        LinuxRootfsLayout.Meta meta = new LinuxRootfsLayout.Meta(
                "ubuntu", "22.04.5", "arm64", 42L, true);
        LinuxRootfsLayout.writeMeta(filesDir, "ubuntu", meta);

        LinuxRootfsLayout.Meta read = LinuxRootfsLayout.readMeta(filesDir, "ubuntu");
        Assert.assertNotNull(read);
        Assert.assertEquals("ubuntu", read.distro);
        Assert.assertEquals("22.04.5", read.version);
        Assert.assertTrue(LinuxRootfsLayout.isInstalled(filesDir, "ubuntu")
                == new File(LinuxRootfsLayout.rootfsDir(filesDir, "ubuntu"), "bin").isDirectory());

        // 各发行版 meta 相互独立
        Assert.assertNull(LinuxRootfsLayout.readMeta(filesDir, "alpine"));
        LinuxRootfsLayout.deleteDistro(filesDir, "ubuntu");
        Assert.assertNull(LinuxRootfsLayout.readMeta(filesDir, "ubuntu"));
    }

    @Test
    public void legacyMetaStillReadableAsAlpine() throws Exception {
        File filesDir = context().getFilesDir();
        // 旧版单发行版格式：rootfs.meta.json，字段 alpineVersion，无 distro/version
        File legacy = new File(filesDir, "proot/rootfs.meta.json");
        //noinspection ResultOfMethodCallIgnored
        legacy.getParentFile().mkdirs();
        java.nio.file.Files.write(legacy.toPath(),
                ("{\"alpineVersion\":\"3.20.3\",\"arch\":\"aarch64\",\"installedAt\":7,"
                        + "\"prootSupported\":true,\"prootUnsupportedReason\":\"\"}").getBytes("UTF-8"));

        LinuxRootfsLayout.Meta read = LinuxRootfsLayout.readMeta(filesDir, "alpine");
        Assert.assertNotNull(read);
        Assert.assertEquals("alpine", read.distro);
        Assert.assertEquals("3.20.3", read.version);
        Assert.assertEquals(7L, read.installedAt);
    }

    @Test
    public void alpineWriteAlsoEmitsLegacyField() throws Exception {
        File filesDir = context().getFilesDir();
        LinuxRootfsLayout.writeMeta(filesDir, "alpine", new LinuxRootfsLayout.Meta(
                "alpine", "3.20.3", "aarch64", 1L, true));
        File file = LinuxRootfsLayout.metaFile(filesDir, "alpine");
        String json = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
        // 旧版本 App 兼容：alpine 字段 meta 同时带 alpineVersion
        Assert.assertTrue(json, json.contains("\"alpineVersion\":\"3.20.3\""));
    }

    @Test
    public void missingMetaReturnsNull() {
        Assert.assertNull(LinuxRootfsLayout.readMeta(context().getFilesDir(), "alpine"));
        Assert.assertNull(LinuxRootfsLayout.readMeta(context().getFilesDir(), "ubuntu"));
    }

    @Test
    public void installDetectionRequiresBinDirectory() throws Exception {
        File filesDir = context().getFilesDir();
        Assert.assertFalse(LinuxRootfsLayout.isInstalled(filesDir));
        File bin = new File(LinuxRootfsLayout.rootfsDir(filesDir), "bin");
        //noinspection ResultOfMethodCallIgnored
        bin.mkdirs();
        Assert.assertTrue(LinuxRootfsLayout.isInstalled(filesDir));
        Assert.assertTrue(LinuxRootfsLayout.isInstalled(filesDir, "alpine"));
        Assert.assertFalse(LinuxRootfsLayout.isInstalled(filesDir, "ubuntu"));
    }

    @Test
    public void deleteDistroRemovesOnlyThatDistro() throws Exception {
        File filesDir = context().getFilesDir();
        File alpineBin = new File(LinuxRootfsLayout.rootfsDir(filesDir, "alpine"), "bin");
        File ubuntuBin = new File(LinuxRootfsLayout.rootfsDir(filesDir, "ubuntu"), "bin");
        //noinspection ResultOfMethodCallIgnored
        alpineBin.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        ubuntuBin.mkdirs();
        LinuxRootfsLayout.writeMeta(filesDir, "ubuntu", new LinuxRootfsLayout.Meta(
                "ubuntu", "22.04.5", "arm64", 1L, true));

        LinuxRootfsLayout.deleteDistro(filesDir, "ubuntu");

        Assert.assertTrue(LinuxRootfsLayout.isInstalled(filesDir, "alpine"));
        Assert.assertFalse(LinuxRootfsLayout.isInstalled(filesDir, "ubuntu"));
        Assert.assertNull(LinuxRootfsLayout.readMeta(filesDir, "ubuntu"));
    }

    @Test
    public void deleteAllKeepsSharedRuntimeDirs() throws Exception {
        File filesDir = context().getFilesDir();
        File lib = new File(filesDir, "proot/lib");
        File alpineBin = new File(LinuxRootfsLayout.rootfsDir(filesDir, "alpine"), "bin");
        File ubuntuBin = new File(LinuxRootfsLayout.rootfsDir(filesDir, "ubuntu"), "bin");
        //noinspection ResultOfMethodCallIgnored
        lib.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        alpineBin.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        ubuntuBin.mkdirs();

        LinuxRootfsLayout.deleteAll(filesDir);

        Assert.assertTrue(lib.isDirectory());
        Assert.assertFalse(alpineBin.isDirectory());
        Assert.assertFalse(ubuntuBin.isDirectory());
    }

    @Test
    public void urlsPointAtAlpineCdnWithArch() {
        String url = LinuxRootfsLayout.minirootfsUrl("aarch64");
        Assert.assertTrue(url.startsWith("https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-"));
        Assert.assertTrue(url.endsWith("-aarch64.tar.gz"));
        Assert.assertEquals(url + ".sha256", LinuxRootfsLayout.minirootfsSha256Url("aarch64"));
    }

    @Test
    public void mirrorUrlPointsAtTunaWithSameArtifactName() {
        String official = LinuxRootfsLayout.minirootfsUrl("aarch64");
        String mirror = LinuxRootfsLayout.minirootfsMirrorUrl("aarch64");
        Assert.assertTrue(mirror.startsWith("https://mirrors.tuna.tsinghua.edu.cn/alpine/"));
        Assert.assertEquals(official.substring(official.lastIndexOf('/') + 1),
                mirror.substring(mirror.lastIndexOf('/') + 1));
    }

    @Test
    public void mirrorListCoversMajorDomesticMirrorsWithSameArtifactName() {
        String official = LinuxRootfsLayout.minirootfsUrl("aarch64");
        String expectedArtifact = official.substring(official.lastIndexOf('/') + 1);
        String[] mirrors = LinuxRootfsLayout.minirootfsMirrorUrls("aarch64");
        Assert.assertEquals(4, mirrors.length);
        Assert.assertTrue(mirrors[0].startsWith("https://mirrors.tuna.tsinghua.edu.cn/"));
        Assert.assertTrue(mirrors[1].startsWith("https://repo.huaweicloud.com/"));
        Assert.assertTrue(mirrors[2].startsWith("https://mirrors.ustc.edu.cn/"));
        Assert.assertTrue(mirrors[3].startsWith("https://mirrors.cloud.tencent.com/"));
        for (String mirror : mirrors) {
            Assert.assertTrue(mirror, mirror.endsWith(expectedArtifact));
        }
    }

    @Test
    public void bundledAssetNameMatchesArch() {
        Assert.assertEquals("rootfs/alpine-minirootfs-3.20.3-aarch64.tar.gz",
                LinuxRootfsLayout.bundledAssetName("aarch64"));
    }
}
