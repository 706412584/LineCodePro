package cn.lineai.ipc.terminal;

import java.io.File;
import org.junit.Assert;
import org.junit.Test;

public final class ProotCommandBuilderTest {

    private static final String PROOT = "/data/app/cn.lineai/lib/arm64/libproot.so";
    private static final File ROOTFS = new File("/data/user/0/cn.lineai/files/proot/alpine");

    @Test
    public void buildWrapsWithEnvOverridesAndBindings() {
        String command = ProotCommandBuilder.build(PROOT, ROOTFS, "/storage/emulated/0/AGG", "ls");
        Assert.assertTrue(command, command.startsWith("LD_LIBRARY_PATH="));
        // JVM/Windows 下 File 会给 Unix 路径加盘符前缀；断言尾段（真机上为字面路径）
        Assert.assertTrue(command, command.contains("LD_LIBRARY_PATH='")
                && command.contains("files/proot/lib:/data/app/cn.lineai/lib/arm64'"));
        Assert.assertTrue(command, command.contains("PROOT_LOADER='/data/app/cn.lineai/lib/arm64/libproot_loader.so'"));
        Assert.assertTrue(command, command.contains("PROOT_TMP_DIR='")
                && command.contains("files/proot/tmp'"));
        Assert.assertTrue(command, command.contains("-R '") && command.contains("files/proot/alpine'"));
        Assert.assertTrue(command, command.contains("-0 --link2symlink"));
        Assert.assertTrue(command, command.contains("-b /storage -b /sdcard -b /system"));
        Assert.assertTrue(command, command.contains("-w '/storage/emulated/0/AGG'"));
        Assert.assertTrue(command, command.contains("export HOME=/root TERM=xterm-256color PATH="));
        Assert.assertTrue(command, command.endsWith("ls'"));
    }

    @Test
    public void nonStorageCwdFallsBackToRoot() {
        String command = ProotCommandBuilder.build(PROOT, ROOTFS, "/data/user/0/cn.lineai/files", "pwd");
        Assert.assertTrue(command.contains("-w '/root'"));
    }

    @Test
    public void sdcardPrefixedCwdIsKept() {
        String command = ProotCommandBuilder.build(PROOT, ROOTFS, "/sdcard/AGG", "pwd");
        Assert.assertTrue(command.contains("-w '/sdcard/AGG'"));
    }

    @Test
    public void emptyCwdFallsBackToRoot() {
        String command = ProotCommandBuilder.build(PROOT, ROOTFS, "", "pwd");
        Assert.assertTrue(command.contains("-w '/root'"));
    }

    @Test
    public void singleQuotesInCommandAreEscaped() {
        String command = ProotCommandBuilder.build(PROOT, ROOTFS, "", "echo 'hi'");
        // shellQuote 把内嵌单引号转成 '\''；guest 脚本被单引号包裹
        Assert.assertTrue(command.contains("echo '\\''hi'\\''"));
    }

    @Test
    public void guestScriptInjectsPathBeforeCommand() {
        String script = ProotCommandBuilder.guestScript("true", "");
        Assert.assertTrue(script.startsWith("export HOME=/root TERM=xterm-256color PATH=/usr"));
        Assert.assertTrue(script.endsWith("; true"));
        Assert.assertFalse(script.contains("http_proxy"));
    }

    @Test
    public void guestScriptWithProxyExportsProxyVars() {
        String script = ProotCommandBuilder.guestScript("apk add git", "http://127.0.0.1:7890");
        Assert.assertTrue(script.contains(" http_proxy=http://127.0.0.1:7890"));
        Assert.assertTrue(script.contains(" https_proxy=http://127.0.0.1:7890"));
        Assert.assertTrue(script.endsWith("; apk add git"));
    }

    @Test
    public void buildWithProxyCarriesProxyIntoWrappedCommand() {
        String command = ProotCommandBuilder.build(PROOT, ROOTFS, "", "wget x", "http://127.0.0.1:7890");
        Assert.assertTrue(command.contains("http_proxy=http://127.0.0.1:7890"));
    }
}
