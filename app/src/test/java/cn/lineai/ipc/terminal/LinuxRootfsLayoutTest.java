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
        LinuxRootfsLayout.Meta meta = new LinuxRootfsLayout.Meta("3.20.3", "aarch64", 1234567890L, true);
        LinuxRootfsLayout.writeMeta(filesDir, meta);

        LinuxRootfsLayout.Meta read = LinuxRootfsLayout.readMeta(filesDir);
        Assert.assertNotNull(read);
        Assert.assertEquals("3.20.3", read.alpineVersion);
        Assert.assertEquals("aarch64", read.arch);
        Assert.assertEquals(1234567890L, read.installedAt);
        Assert.assertTrue(read.prootSupported);
    }

    @Test
    public void missingMetaReturnsNull() {
        Assert.assertNull(LinuxRootfsLayout.readMeta(context().getFilesDir()));
    }

    @Test
    public void installDetectionRequiresBinDirectory() throws Exception {
        File filesDir = context().getFilesDir();
        Assert.assertFalse(LinuxRootfsLayout.isInstalled(filesDir));
        File bin = new File(LinuxRootfsLayout.rootfsDir(filesDir), "bin");
        //noinspection ResultOfMethodCallIgnored
        bin.mkdirs();
        Assert.assertTrue(LinuxRootfsLayout.isInstalled(filesDir));
    }

    @Test
    public void deleteAllRemovesRootfsAndMeta() throws Exception {
        File filesDir = context().getFilesDir();
        File bin = new File(LinuxRootfsLayout.rootfsDir(filesDir), "bin");
        //noinspection ResultOfMethodCallIgnored
        bin.mkdirs();
        LinuxRootfsLayout.writeMeta(filesDir,
                new LinuxRootfsLayout.Meta("3.20.3", "aarch64", 1L, true));

        LinuxRootfsLayout.deleteAll(filesDir);

        Assert.assertFalse(LinuxRootfsLayout.isInstalled(filesDir));
        Assert.assertNull(LinuxRootfsLayout.readMeta(filesDir));
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
    public void bundledAssetNameMatchesArch() {
        Assert.assertEquals("rootfs/alpine-minirootfs-3.20.3-aarch64.tar.gz",
                LinuxRootfsLayout.bundledAssetName("aarch64"));
    }
}
