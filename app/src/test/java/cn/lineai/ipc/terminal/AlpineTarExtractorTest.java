package cn.lineai.ipc.terminal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AlpineTarExtractorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void extractsFilesDirectoriesAndSymlinks() throws Exception {
        byte[] tarGz = buildTarGz(new TarBuilder()
                .dir("./bin/")
                .file("./bin/busybox", "busybox-payload")
                .file("./etc/resolv.conf", "nameserver 8.8.8.8")
                .symlink("./bin/sh", "../bin/busybox")
                .hardlink("./bin/bb", "./bin/busybox"));
        File target = folder.newFolder("rootfs");
        AlpineTarExtractor.extract(tarGz, target);

        Assert.assertEquals("busybox-payload",
                new String(Files.readAllBytes(new File(target, "bin/busybox").toPath()), StandardCharsets.UTF_8));
        Assert.assertEquals("nameserver 8.8.8.8",
                new String(Files.readAllBytes(new File(target, "etc/resolv.conf").toPath()), StandardCharsets.UTF_8));
        // Windows 下 symlink 降级为文本文件（内容为目标路径）；不断言类型只断言存在
        Assert.assertTrue(new File(target, "bin/sh").exists());
        // 硬链接复制为普通文件
        Assert.assertEquals("busybox-payload",
                new String(Files.readAllBytes(new File(target, "bin/bb").toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void truncatedTarThrows() throws Exception {
        // 完整 tar = 512 header + 512 payload + 1024 结束块；截到 header 中段必然缺 payload/结束块
        byte[] tarGz = buildTarGz(new TarBuilder().file("./a.txt", "hello"));
        // gunzip 后再截断才能精确控制；这里直接对 gzip 流剪掉一半以上字节
        byte[] truncated = new byte[tarGz.length / 3];
        System.arraycopy(tarGz, 0, truncated, 0, truncated.length);
        File target = folder.newFolder("trunc");
        try {
            AlpineTarExtractor.extract(truncated, target);
            Assert.fail("expected IOException");
        } catch (IOException expected) {
        }
    }

    @Test
    public void pathTraversalEntryIsRejected() throws Exception {
        byte[] tarGz = buildTarGz(new TarBuilder().file("../evil.txt", "x"));
        File target = folder.newFolder("safe");
        try {
            AlpineTarExtractor.extract(tarGz, target);
            Assert.fail("expected IOException");
        } catch (IOException expected) {
        }
    }

    // ===== 测试内构造 ustar tar.gz =====

    private static byte[] buildTarGz(TarBuilder builder) throws IOException {
        byte[] tar = builder.toTarBytes();
        ByteArrayOutputStream gzipBuffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(gzipBuffer)) {
            gzip.write(tar);
        }
        return gzipBuffer.toByteArray();
    }

    private static final class TarBuilder {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        TarBuilder dir(String name) {
            out.write(header(name, '5', 0, ""), 0, 512);
            return this;
        }

        TarBuilder file(String name, String content) {
            byte[] payload = content.getBytes(StandardCharsets.UTF_8);
            out.write(header(name, '0', payload.length, ""), 0, 512);
            out.write(payload, 0, payload.length);
            int padding = (512 - payload.length % 512) % 512;
            out.write(new byte[padding], 0, padding);
            return this;
        }

        TarBuilder symlink(String name, String target) {
            out.write(header(name, '2', 0, target), 0, 512);
            return this;
        }

        TarBuilder hardlink(String name, String target) {
            out.write(header(name, '1', 0, target), 0, 512);
            return this;
        }

        byte[] toTarBytes() {
            out.write(new byte[1024], 0, 1024); // 结束双零块
            return out.toByteArray();
        }
    }

    private static byte[] header(String name, char type, long size, String linkName) {
        byte[] block = new byte[512];
        putString(block, 0, name);
        block[156] = (byte) type;
        putOctal(block, 124, size);
        putString(block, 157, linkName);
        putString(block, 257, "ustar");
        putString(block, 263, "00");
        for (int i = 148; i < 156; i++) {
            block[i] = ' ';
        }
        int checksum = 0;
        for (byte b : block) {
            checksum += (b & 0xFF);
        }
        putOctal(block, 148, checksum);
        return block;
    }

    private static void putString(byte[] block, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, block, offset, Math.min(bytes.length, 100));
    }

    private static void putOctal(byte[] block, int offset, long value) {
        byte[] bytes = Long.toOctalString(value).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, block, offset, Math.min(bytes.length, 11));
        block[offset + Math.min(bytes.length, 11)] = 0;
    }
}
