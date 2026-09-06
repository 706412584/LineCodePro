package cn.lineai.ipc.terminal;

import android.system.Os;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Alpine minirootfs（tar.gz, ustar 格式）解压器。
 *
 * <p>纯 Java 实现：512 字节 ustar header 解析，支持普通文件 / 目录 / 符号链接 / 硬链接。
 * 解压到临时目录，校验入口存在后由调用方原子 rename 到最终位置。</p>
 */
public final class AlpineTarExtractor {

    private static final int BLOCK_SIZE = 512;
    private static final long MAX_ENTRY_BYTES = 512L * 1024L * 1024L;

    private AlpineTarExtractor() {
    }

    /**
     * 解压 tar.gz 到 targetDir（须已存在或可创建）。
     *
     * @throws IOException 格式损坏 / 磁盘错误 / 不支持的 entry 类型
     */
    public static void extract(File tarGzFile, File targetDir) throws IOException {
        try (InputStream raw = new java.io.FileInputStream(tarGzFile);
             InputStream in = new GZIPInputStream(raw)) {
            extractStream(in, targetDir);
        }
    }

    /**
     * 解压 tar.gz 到 targetDir（须已存在或可创建）。
     *
     * <p>symlink 创建失败（ROM 禁止 app uid 调 symlink()，如 OPPO ColorOS）时，
     * 降级为复制链接目标文件（保证 guest 内可执行）；目标尚未解出时延迟到
     * tar 处理完毕后统一补做。</p>
     *
     * @throws IOException 格式损坏 / 磁盘错误 / 不支持的 entry 类型
     */
    public static void extract(byte[] tarGzBytes, File targetDir) throws IOException {
        try (InputStream in = new GZIPInputStream(
                new java.io.ByteArrayInputStream(tarGzBytes))) {
            extractStream(in, targetDir);
        }
    }

    private static void extractStream(InputStream in, File targetDir) throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("cannot create target dir: " + targetDir);
        }
        // symlink 创建失败的条目（ROM 禁 symlink）：tar 全部解完后按目标复制降级
        List<String[]> deferredSymlinks = new ArrayList<>();
        byte[] header = new byte[BLOCK_SIZE];
        while (true) {
            if (!readFully(in, header)) {
                throw new IOException("truncated tar: missing header");
            }
            if (isZeroBlock(header)) {
                break; // 结束标志（两个全零块，读到第一个即停）
            }
            String name = fieldName(header);
            if (name.length() == 0) {
                continue;
            }
            long size = fieldOctal(header, 124, 12);
            long mode = fieldOctal(header, 100, 8);
            char type = typeFlag(header);
            String linkName = fieldString(header, 157, 100);
            if (size < 0 || size > MAX_ENTRY_BYTES) {
                throw new IOException("entry too large: " + name + " " + size);
            }
            File out = resolveSafe(targetDir, name);
            switch (type) {
                case '0': // 普通文件（含 GNU 旧式 '\0'）
                case '\0':
                    writeFile(in, out, size, mode);
                    skipPadding(in, size);
                    break;
                case '5':
                    if (!out.exists() && !out.mkdirs()) {
                        throw new IOException("cannot mkdir: " + out);
                    }
                    break;
                case '2': // 符号链接
                    if (out.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        out.delete();
                    }
                    if (!createSymlink(linkName, out)) {
                        // ROM 禁 symlink：延迟到所有文件解完后复制目标降级
                        deferredSymlinks.add(new String[] {out.getAbsolutePath(), linkName});
                    }
                    break;
                case '1': // 硬链接
                    File source = resolveSafe(targetDir, linkName);
                    if (!source.isFile()) {
                        throw new IOException("hardlink source missing: " + linkName);
                    }
                    copyFile(source, out);
                    break;
                case 'x': // pax 扩展头：跳过 payload 继续
                case 'g':
                    skipFully(in, size);
                    skipPadding(in, size);
                    break;
                default:
                    // 未知类型（设备文件等）：跳过 payload，rootfs 内不需要
                    skipFully(in, size);
                    skipPadding(in, size);
                    break;
            }
        }
        // 降级补做：按链接目标复制实体文件（目标此时已全部解出）
        for (String[] entry : deferredSymlinks) {
            File link = new File(entry[0]);
            File source = resolveLinkTarget(targetDir, link.getParentFile(), entry[1]);
            if (source == null || !source.isFile()) {
                throw new IOException("symlink target unavailable for copy fallback: "
                        + entry[0] + " -> " + entry[1]);
            }
            copyFile(source, link);
        }
    }

    /**
     * 解析链接目标（支持绝对路径按 rootfs 根、相对路径按链接所在目录）。
     * 找不到返回 null。
     */
    private static File resolveLinkTarget(File rootfsDir, File linkDir, String target) {
        if (target == null || target.length() == 0) {
            return null;
        }
        File resolved;
        if (target.startsWith("/")) {
            resolved = new File(rootfsDir, target.substring(1));
        } else {
            resolved = new File(linkDir, target);
        }
        if (resolved.isFile()) {
            return resolved;
        }
        // 目标自身是（已降级复制的）链接文件：跟一层
        try {
            File canonical = resolved.getCanonicalFile();
            if (canonical.isFile() && !canonical.equals(resolved)) {
                return canonical;
            }
        } catch (IOException ignored) {
        }
        return resolved.isFile() ? resolved : null;
    }

    /** 写入普通文件并按 tar mode 位恢复执行权限（busybox 等在 rootfs 内必须可执行）。 */
    private static void writeFile(InputStream in, File out, long size, long mode) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot mkdir: " + parent);
        }
        if (out.exists() && !out.delete()) {
            // 残留同名文件且删除失败（可能只读）：强制造一次覆盖语义
            if (!out.canWrite()) {
                //noinspection ResultOfMethodCallIgnored
                out.setWritable(true);
            }
        }
        try (OutputStream output = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int want = (int) Math.min(buffer.length, remaining);
                int n = in.read(buffer, 0, want);
                if (n <= 0) {
                    throw new IOException("truncated tar: file payload " + out);
                }
                output.write(buffer, 0, n);
                remaining -= n;
            }
        }
        if ((mode & 0111) != 0) {
            // owner/group/other 任一执行位 → owner 可执行即可（app uid 即属主）
            //noinspection ResultOfMethodCallIgnored
            out.setExecutable(true, true);
        }
    }

    /**
     * 创建符号链接。
     *
     * @return true 表示真实 symlink 建成；false 表示本 ROM 禁止 symlink，
     *         调用方应走复制目标的降级路径（见 extractStream 尾部）。
     */
    private static boolean createSymlink(String target, File link) throws IOException {
        try {
            Os.symlink(target, link.getAbsolutePath());
            return true;
        } catch (Exception primary) {
            // EEXIST：残留同名文件——删除后重试一次
            //noinspection ResultOfMethodCallIgnored
            link.delete();
            try {
                Os.symlink(target, link.getAbsolutePath());
                return true;
            } catch (Exception retried) {
                // 任何失败（ROM 禁止 EPERM/EROFS、EEXIST 残留、JVM 测试环境 not-mocked）
                // 都交给复制目标降级——复制出的实体文件对 guest 完全可用
                return false;
            }
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot mkdir: " + parent);
        }
        try (InputStream in = new java.io.FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    /** 防路径穿越：entry 名归一化后必须落在 targetDir 内。 "./"（根目录 entry）合法。 */
    private static File resolveSafe(File targetDir, String name) throws IOException {
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // "./"、"."、"/" 等根目录 entry：目标即 targetDir 自身
        if (normalized.length() == 0 || ".".equals(normalized)) {
            return targetDir;
        }
        File resolved = new File(targetDir, normalized);
        String canonical;
        try {
            canonical = resolved.getCanonicalPath();
        } catch (IOException e) {
            throw new IOException("cannot resolve entry: " + name, e);
        }
        String base = targetDir.getCanonicalPath() + File.separator;
        if (!canonical.startsWith(base)) {
            throw new IOException("entry escapes target: " + name);
        }
        return new File(canonical);
    }

    /** 跳过 ustar 512 字节对齐的尾部 padding（payload 之后）。 */
    private static void skipPadding(InputStream in, long size) throws IOException {
        long padding = (512 - size % 512) % 512;
        if (padding > 0) {
            skipFully(in, padding);
        }
    }

    private static boolean readFully(InputStream in, byte[] buffer) throws IOException {
        int read = 0;
        while (read < buffer.length) {
            int n = in.read(buffer, read, buffer.length - read);
            if (n <= 0) {
                return false;
            }
            read += n;
        }
        return true;
    }

    private static void skipFully(InputStream in, long size) throws IOException {
        long remaining = size;
        while (remaining > 0) {
            long n = in.skip(remaining);
            if (n <= 0) {
                // 某些流 skip 返回 0：单字节读推进
                if (in.read() < 0) {
                    throw new IOException("truncated tar: skip payload");
                }
                remaining--;
            } else {
                remaining -= n;
            }
        }
    }

    private static boolean isZeroBlock(byte[] header) {
        for (byte b : header) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String fieldName(byte[] header) {
        return fieldString(header, 0, 100);
    }

    private static char typeFlag(byte[] header) {
        byte b = header[156];
        return b == 0 ? '\0' : (char) b;
    }

    private static String fieldString(byte[] header, int offset, int length) {
        int end = offset;
        int max = Math.min(offset + length, header.length);
        while (end < max && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long fieldOctal(byte[] header, int offset, int length) {
        String value = fieldString(header, offset, length).trim();
        if (value.length() == 0) {
            return 0;
        }
        // GNU 空格结尾的八进制
        int end = 0;
        while (end < value.length() && Character.digit(value.charAt(end), 8) >= 0) {
            end++;
        }
        if (end == 0) {
            return -1;
        }
        try {
            return Long.parseLong(value.substring(0, end), 8);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
