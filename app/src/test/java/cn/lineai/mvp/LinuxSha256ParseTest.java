package cn.lineai.mvp;

import org.junit.Assert;
import org.junit.Test;

/**
 * Alpine .sha256 校验文件解析：官方格式为 "<hash>  <filename>"（GNU sha256sum 风格），
 * 比较时必须只取哈希字段，否则永远 mismatch（真机 bug 修复的回归锁）。
 */
public final class LinuxSha256ParseTest {

    @Test
    public void officialFormatWithFilenameYieldsHashOnly() {
        String raw = "041fa34a81788242df9e78fa69b97ab45b8ec47ddbf88864755610414a7bf3de  alpine-minirootfs-3.20.3-aarch64.tar.gz";
        Assert.assertEquals("041fa34a81788242df9e78fa69b97ab45b8ec47ddbf88864755610414a7bf3de",
                LinuxEnvironmentController.extractSha256(raw));
    }

    @Test
    public void bareHashIsKeptAsIs() {
        Assert.assertEquals("abc123",
                LinuxEnvironmentController.extractSha256("abc123"));
    }

    @Test
    public void whitespaceIsTrimmed() {
        Assert.assertEquals("abc123",
                LinuxEnvironmentController.extractSha256("  abc123  \n"));
    }

    @Test
    public void blankInputYieldsEmpty() {
        Assert.assertEquals("", LinuxEnvironmentController.extractSha256(""));
        Assert.assertEquals("", LinuxEnvironmentController.extractSha256(null));
    }
}
