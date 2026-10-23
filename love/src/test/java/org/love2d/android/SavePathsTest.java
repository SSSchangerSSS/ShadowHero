package org.love2d.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * تست قرارداد فایل مشترک Java/Lua (بدون نیاز به دستگاه یا شبیه‌ساز).
 * مسیرها، فرمت chooser.txt، اعتبارسنجی شناسه اپیزود و مهاجرت ذخیره‌های قدیمی را پوشش می‌دهد.
 */
public class SavePathsTest {

    private File baseDir;

    @Before
    public void setUp() throws IOException {
        baseDir = Files.createTempDirectory("shadowhero-test").toFile();
    }

    @Test
    public void pathsFollowTheContract() {
        assertEquals(new File(baseDir, "save"), SavePaths.saveRoot(baseDir));
        assertEquals(new File(baseDir, "save/archive"), SavePaths.archiveDir(baseDir));
        assertEquals(new File(baseDir, "save/archive/chooser.txt"), SavePaths.chooserFile(baseDir));
        assertEquals(new File(baseDir, "save/archive/season_one_e1.txt"),
                SavePaths.lockFile(baseDir, "season_one_e1.txt"));
        assertEquals(new File(baseDir, "save/archive/s1e1_save.txt"),
                SavePaths.episodeSaveFile(baseDir, "s1e1"));
    }

    @Test
    public void chooserContentMatchesLuaParser() {
        // Lua: local k, v = l:match("^(..-)=(.+)$") → k="1", v=شناسه اپیزود
        assertEquals("1=train", SavePaths.chooserContent("train"));
        assertEquals("1=s1e1", SavePaths.chooserContent("s1e1"));
        assertEquals("1=s2e3", SavePaths.chooserContent("s2e3"));
    }

    @Test
    public void invalidEpisodeIdsFallBackToTraining() {
        assertEquals("train", SavePaths.normalizeEpisodeId("1=s1e1"));
        assertEquals("train", SavePaths.normalizeEpisodeId("s3e1")); // هنوز ساخته نشده
        assertEquals("train", SavePaths.normalizeEpisodeId("s9e9"));
        assertEquals("train", SavePaths.normalizeEpisodeId(null));
        assertEquals("train", SavePaths.normalizeEpisodeId(""));
        assertEquals("train", SavePaths.normalizeEpisodeId("../evil"));
        // همه اپیزودهای واقعی باید معتبر بمانند
        for (String id : new String[]{"train", "s1e1", "s1e2", "s1e3", "s2e1", "s2e2", "s2e3"}) {
            assertTrue(id, SavePaths.isValidEpisodeId(id));
        }
        assertFalse(SavePaths.isValidEpisodeId("s4e3")); // در level_chooser کامنت است
    }

    @Test
    public void writeChooserIsAtomicAndCorrect() throws IOException {
        File chooser = SavePaths.writeChooser(baseDir, "s1e1");
        assertTrue(chooser.isFile());
        assertEquals(new File(baseDir, "save/archive/chooser.txt"), chooser);
        assertEquals("1=s1e1", new String(Files.readAllBytes(chooser.toPath()), "UTF-8"));
        // هیچ فایل موقتی باقی نمی‌ماند
        File[] archive = SavePaths.archiveDir(baseDir).listFiles();
        assertEquals(1, archive.length);

        // نوشتن مجدد بدون باقی‌ماندگی، محتوای جدید
        SavePaths.writeChooser(baseDir, "train");
        assertEquals("1=train", new String(Files.readAllBytes(chooser.toPath()), "UTF-8"));
    }

    @Test
    public void unsafeNamesAreRejected() {
        try {
            SavePaths.lockFile(baseDir, "../evil.txt");
            fail("path traversal must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        try {
            SavePaths.lockFile(baseDir, "a/b.txt");
            fail("subdirectories must be rejected in lock files");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void migrateLegacyFilesMovesOnlyMissingOnes() throws IOException {
        File root = SavePaths.saveRoot(baseDir);
        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
        Files.write(new File(root, "s1e1_save.txt").toPath(), "1=100\n2=3\n3=42\n".getBytes("UTF-8"));
        Files.write(new File(root, "season_one_e1.txt").toPath(), "1=1\n".getBytes("UTF-8"));
        Files.write(new File(root, "music_state.txt").toPath(), "1=0\n".getBytes("UTF-8")); // غیرقابل مهاجرت
        // فایل جدید موجود است؛ قدیمی نباید بازنویسی شود
        File archive = SavePaths.archiveDir(baseDir);
        //noinspection ResultOfMethodCallIgnored
        archive.mkdirs();
        Files.write(new File(archive, "s1e1_save.txt").toPath(), "1=500\n2=3\n3=7\n".getBytes("UTF-8"));

        int moved = SavePaths.migrateLegacyFiles(baseDir);

        assertEquals(1, moved); // فقط season_one_e1.txt
        assertEquals("1=500", Files.readAllLines(new File(archive, "s1e1_save.txt").toPath()).get(0));
        assertTrue(new File(archive, "season_one_e1.txt").isFile());
        assertTrue(new File(root, "music_state.txt").isFile()); // دست‌نخورده
        assertNull(Arrays.stream(archive.listFiles()).filter(f -> f.getName().equals("music_state.txt")).findFirst().orElse(null));
    }

    @Test
    public void migrateOnEmptyBaseIsNoOp() {
        assertEquals(0, SavePaths.migrateLegacyFiles(baseDir));
    }

    @Test
    public void migrateFromLegacyUnnamedIdentityDir() throws IOException {
        // بیلدهای بدون t.identity در conf.lua → ریشه ذخیره LÖVE: <ext>/save/unnamed
        File unnamed = new File(SavePaths.saveRoot(baseDir), "unnamed");
        //noinspection ResultOfMethodCallIgnored
        unnamed.mkdirs();
        Files.write(new File(unnamed, "s2e1_save.txt").toPath(), "1=250\n2=2\n3=31\n".getBytes("UTF-8"));
        Files.write(new File(unnamed, "season_one_e1.txt").toPath(), "1=1\n".getBytes("UTF-8"));

        int moved = SavePaths.migrateLegacyFiles(baseDir);

        assertEquals(2, moved);
        File archive = SavePaths.archiveDir(baseDir);
        assertEquals("1=250", Files.readAllLines(new File(archive, "s2e1_save.txt").toPath()).get(0));
        assertTrue(new File(archive, "season_one_e1.txt").isFile());
        // فایل‌های اصلی دست‌نخورده باقی می‌مانند (کپی، نه جابه‌جایی مخرب)
        assertTrue(new File(unnamed, "s2e1_save.txt").isFile());
    }
}
