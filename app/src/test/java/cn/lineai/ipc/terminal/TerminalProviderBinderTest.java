package cn.lineai.ipc.terminal;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public final class TerminalProviderBinderTest {

    private TerminalProviderBinder binder;
    private File dir;

    @Before
    public void setUp() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        dir = Files.createTempDirectory("binder-test").toFile();
        binder = new TerminalProviderBinder(context, "test");
    }

    @Test
    public void writeThenReadFileChunkRoundTrips() throws Exception {
        File file = new File(dir, "a.txt");
        byte[] payload = "hello binder".getBytes(StandardCharsets.UTF_8);
        Assert.assertTrue(binder.writeFileChunk(file.getAbsolutePath(), 0L, payload));
        Assert.assertArrayEquals(payload,
                binder.readFileChunk(file.getAbsolutePath(), 0L, payload.length));
    }

    @Test
    public void readFileChunkHonorsOffsetAndBounds() throws Exception {
        File file = new File(dir, "b.txt");
        Assert.assertTrue(binder.writeFileChunk(file.getAbsolutePath(), 0L,
                "0123456789".getBytes(StandardCharsets.UTF_8)));
        byte[] middle = binder.readFileChunk(file.getAbsolutePath(), 3L, 4);
        Assert.assertEquals("3456", new String(middle, StandardCharsets.UTF_8));
        // offset 超出文件长度返回空
        Assert.assertEquals(0, binder.readFileChunk(file.getAbsolutePath(), 100L, 4).length);
    }

    @Test
    public void writeFileChunkAppendUsesNegativeOffset() throws Exception {
        File file = new File(dir, "c.txt");
        Assert.assertTrue(binder.writeFileChunk(file.getAbsolutePath(), 0L,
                "ab".getBytes(StandardCharsets.UTF_8)));
        Assert.assertTrue(binder.writeFileChunk(file.getAbsolutePath(), -1L,
                "cd".getBytes(StandardCharsets.UTF_8)));
        Assert.assertEquals("abcd", new String(binder.readFile(file.getAbsolutePath()), StandardCharsets.UTF_8));
    }

    @Test
    public void getFileSizeReportsBytesAndMissingAsMinusOne() throws Exception {
        File file = new File(dir, "d.txt");
        Assert.assertTrue(binder.writeFileChunk(file.getAbsolutePath(), 0L,
                "12345".getBytes(StandardCharsets.UTF_8)));
        Assert.assertEquals(5, binder.getFileSize(file.getAbsolutePath()));
        Assert.assertEquals(-1, binder.getFileSize(new File(dir, "missing").getAbsolutePath()));
    }

    @Test
    public void writeFileCreatesParentDirectories() throws Exception {
        File file = new File(dir, "nested/deep/e.txt");
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
        Assert.assertTrue(binder.writeFile(file.getAbsolutePath(), payload));
        Assert.assertArrayEquals(payload, binder.readFile(file.getAbsolutePath()));
    }

    @Test
    public void listDirDetailedReturnsNameDirAndSize() throws Exception {
        Assert.assertTrue(binder.writeFileChunk(new File(dir, "f.txt").getAbsolutePath(), 0L,
                "abc".getBytes(StandardCharsets.UTF_8)));
        Assert.assertTrue(new File(dir, "sub").mkdir());
        JSONArray entries = new JSONArray(binder.listDirDetailed(dir.getAbsolutePath()));
        JSONObject fileEntry = null;
        JSONObject dirEntry = null;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if ("f.txt".equals(entry.optString("name"))) {
                fileEntry = entry;
            }
            if ("sub".equals(entry.optString("name"))) {
                dirEntry = entry;
            }
        }
        Assert.assertNotNull(fileEntry);
        Assert.assertFalse(fileEntry.optBoolean("dir"));
        Assert.assertEquals(3, fileEntry.optLong("size"));
        Assert.assertNotNull(dirEntry);
        Assert.assertTrue(dirEntry.optBoolean("dir"));
    }

    @Test
    public void listDirDetailedOnMissingDirReturnsEmptyArray() {
        Assert.assertEquals("[]", binder.listDirDetailed(new File(dir, "nope").getAbsolutePath()));
    }

    @Test
    public void deleteFileRemovesExistingAndReportsMissing() throws Exception {
        File file = new File(dir, "g.txt");
        Assert.assertTrue(binder.writeFileChunk(file.getAbsolutePath(), 0L, new byte[0]));
        Assert.assertTrue(binder.deleteFile(file.getAbsolutePath()));
        Assert.assertFalse(binder.deleteFile(file.getAbsolutePath()));
    }

    @Test
    public void fileExistsReflectsFilesystem() throws Exception {
        File file = new File(dir, "h.txt");
        Assert.assertFalse(binder.fileExists(file.getAbsolutePath()));
        Assert.assertTrue(binder.writeFileChunk(file.getAbsolutePath(), 0L, new byte[0]));
        Assert.assertTrue(binder.fileExists(file.getAbsolutePath()));
    }

    @Test
    public void providerInfoCarriesNameAndHome() throws Exception {
        JSONObject info = new JSONObject(binder.getProviderInfo());
        Assert.assertEquals("test", info.optString("name"));
        Assert.assertEquals("terminal", binder.getProviderType());
    }

    @Test
    public void nullOrEmptyPathsAreRejected() {
        Assert.assertEquals(0, binder.readFile(null).length);
        Assert.assertFalse(binder.writeFile(null, new byte[0]));
        Assert.assertFalse(binder.deleteFile(null));
        Assert.assertFalse(binder.fileExists(null));
        Assert.assertEquals(-1, binder.fileSize(null));
    }
}
