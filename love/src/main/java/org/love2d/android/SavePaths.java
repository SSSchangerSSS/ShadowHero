package org.love2d.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * قرارداد واحد مسیرهای ذخیره‌سازی بین لایه Java و لایه Lua.
 *
 * ریشه ذخیره‌سازی LÖVE در اندروید با t.identity="archive" در conf.lua پین شده است:
 *   {@code <getExternalFilesDir(null)>/save/archive}  (سورس: boot.lua → setIdentity → Filesystem.cpp و بخش LOVE_ANDROID)
 * پوشه رسمی داده‌های بازی (قرارداد کانونی): {@code <base>/save/archive}
 *
 * فایل‌های قرارداد:
 * - chooser.txt     : Java می‌نویسد (محتوای "1=<id>") و Lua با jupiter.load("chooser.txt") می‌خواند
 * - season_*_e*.txt : قفل/باز بودن اپیزود ("1=1" باز، "0" قفل — نوشته Java، به‌روزرسانی Lua)
 * - <id>_save.txt    : امتیاز/ستاره/زمان ("1=score"، "2=stars"، "3=time" — نوشته Lua، خوانده Java)
 *
 * این کلاس هیچ وابستگی به Android SDK ندارد تا تست JVM خام روی آن ممکن باشد.
 */
public final class SavePaths {

    /** نام فایل انتخاب مرحله — مشترک بین gotoLevel و episode_init_state.lua */
    public static final String CHOOSER_FILE = "chooser.txt";

    /** زیرپوشه کانونی داده‌ها؛ معادل پیشوند "archive/" در سمت Lua */
    public static final String ARCHIVE_SUBDIR = "archive";

    /**
     * شناسه‌های اپیزودهایی که واقعاً در level_chooser.lua پیاده‌سازی شده‌اند
     * (فصل ۳ و ۴ هنوز کامنت‌اند؛ اگر روزی فعال شدند، این الگو و تست باید همگام شوند)
     */
    private static final Pattern VALID_EPISODE = Pattern.compile("^(train|s[12]e[1-3])$");

    /** نام‌های مجاز فایل‌های قرارداد؛ جلوگیری از path traversal */
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    private SavePaths() {
    }

    /** ریشه ذخیره LÖVE: {@code <base>/save} */
    public static File saveRoot(File baseDir) {
        return new File(baseDir, "save");
    }

    /** پوشه کانونی داده‌های بازی: {@code <base>/save/archive} */
    public static File archiveDir(File baseDir) {
        return new File(saveRoot(baseDir), ARCHIVE_SUBDIR);
    }

    /** فایل انتخاب مرحله: {@code <base>/save/archive/chooser.txt} */
    public static File chooserFile(File baseDir) {
        return new File(archiveDir(baseDir), CHOOSER_FILE);
    }

    /** فایل قفل اپیزود، با پاک‌سازی نام (بدون traversal) */
    public static File lockFile(File baseDir, String fileName) {
        return new File(archiveDir(baseDir), sanitize(fileName));
    }

    /** فایل امتیاز اپیزود: {@code archive/<id>_save.txt} */
    public static File episodeSaveFile(File baseDir, String episodeId) {
        return new File(archiveDir(baseDir), sanitize(episodeId) + "_save.txt");
    }

    /**
     * فقط شناسه‌های واقعاً موجود مجازند؛ بقیه به «train» نگاشت می‌شوند تا
     * انتخاب نامعتبر به اجرای مرحله ناقص یا خاموش منجر نشود.
     */
    public static String normalizeEpisodeId(String episodeId) {
        return isValidEpisodeId(episodeId) ? episodeId : "train";
    }

    public static boolean isValidEpisodeId(String episodeId) {
        return episodeId != null && VALID_EPISODE.matcher(episodeId).matches();
    }

    /** محتوای قراردادی chooser.txt که level_chooser.lua به شناسه اپیزود تبدیل می‌کند */
    public static String chooserContent(String episodeId) {
        return "1=" + normalizeEpisodeId(episodeId);
    }

    /**
     * نوشتن اتمیک chooser.txt: اول در فایل موقت، سپس جایگزینی نهایی.
     * به این ترتیب هرگز chooser نیمه‌نوشته/خراب باقی نمی‌ماند.
     *
     * @return فایل نهایی نوشته‌شده
     * @throws IOException در صورت هر شکست (پوشه، نوشتن یا جایگزینی)
     */
    public static File writeChooser(File baseDir, String episodeId) throws IOException {
        File archive = archiveDir(baseDir);
        //noinspection ResultOfMethodCallIgnored
        archive.mkdirs();
        if (!archive.isDirectory()) {
            throw new IOException("cannot create archive dir: " + archive);
        }
        String content = chooserContent(episodeId);
        File tmp = new File(archive, CHOOSER_FILE + ".tmp");
        File dest = new File(archive, CHOOSER_FILE);
        writeStringAtomically(tmp, dest, content);
        return dest;
    }

    /**
     * فایل‌هایی که ممکن است در بیلدهای قدیمی‌تر/خراب در جای اشتباه نوشته شده باشند
     * (ریشه save یا save/unnamed — identityهای پیش‌فرض LÖVE) به پوشه کانونی منتقل می‌شوند؛
     * فایل موجود در مقصد هرگز بازنویسی نمی‌شود.
     *
     * @return تعداد فایل‌های منتقل‌شده
     */
    public static int migrateLegacyFiles(File baseDir) {
        int moved = 0;
        moved += migrateFrom(baseDir, saveRoot(baseDir));
        // identity پیش‌فرض LÖVE وقتی conf.lua فاقد t.identity است (boot.lua مسیر مشتق می‌کند)
        moved += migrateFrom(baseDir, new File(saveRoot(baseDir), "unnamed"));
        return moved;
    }

    private static int migrateFrom(File baseDir, File legacyDir) {
        File[] candidates = legacyDir.listFiles();
        if (candidates == null) {
            return 0;
        }
        File archive = archiveDir(baseDir);
        //noinspection ResultOfMethodCallIgnored
        archive.mkdirs();
        int moved = 0;
        for (File candidate : candidates) {
            String name = candidate.getName();
            if (!candidate.isFile() || !isMigratable(name)) {
                continue;
            }
            File dest = new File(archive, name);
            if (dest.exists()) {
                continue; // پیشرفت جدید ارزشمندتر است؛ فایل قدیمی دست‌نخورده می‌ماند
            }
            try {
                copyFile(candidate, dest);
                moved++;
            } catch (IOException ignored) {
                // مهاجرت اختیاری است؛ شکست آن نباید اجرای بازی را متوقف کند
            }
        }
        return moved;
    }

    private static boolean isMigratable(String name) {
        if (!SAFE_NAME.matcher(name).matches()) {
            return false;
        }
        return name.equals(CHOOSER_FILE)
                || name.endsWith("_save.txt")
                || name.startsWith("season_")
                || name.endsWith(".save");
    }

    private static String sanitize(String fileName) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("unsafe save file name: " + fileName);
        }
        return fileName.toLowerCase(Locale.US);
    }

    private static void writeStringAtomically(File tmp, File dest, String content) throws IOException {
        OutputStream out = new FileOutputStream(tmp);
        try {
            out.write(content.getBytes("UTF-8"));
            out.flush();
        } finally {
            out.close();
        }
        if (dest.exists() && !dest.delete()) {
            throw new IOException("cannot replace " + dest);
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("cannot commit " + tmp + " -> " + dest);
        }
    }

    private static void copyFile(File src, File dest) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
