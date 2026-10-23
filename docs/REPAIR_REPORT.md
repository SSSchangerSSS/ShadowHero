# گزارش تعمیر و پایدارسازی — قهرمان سایه‌ای (ShadowHero)

تاریخ: 2026-10-23 | شاخه کاری: `fix/p0-crash` | نقطه شروع: `68d282f` (master)

---

## ۱. وضعیت اولیه ثبت‌شده (بر اساس کد واقعی، نه مستندات)

| مورد | مقدار واقعی | منبع |
|---|---|---|
| شاخه / SHA بررسی‌شده | `fix/p0-crash` از `master@68d282f` | git |
| LÖVE | ساب‌ماژول `love/src/jni/love` → `love2d/love@216d5ca4` (2021-08-21، خط 11.x) | `.gitmodules` + GitHub API |
| SDL2 | vendored در `love/src/jni/SDL2` (هم shared هم static می‌سازد) | `SDL2/Android.mk:90,105` |
| AGP / Gradle / JDK | 8.1.1 / 8.2.1 (wrapper) / 17 | `build.gradle`، `gradle-wrapper.properties` |
| NDK | `21.3.6528147` (هر دو ماژول) | `app/build.gradle`، `love/build.gradle` |
| variant | `normal` + task `assembleNormalRelease` | `app/build.gradle` + ورک‌فلو |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 | `app/build.gradle` |
| شناسه اپلیکیشن | `com.SandSprogrammingGroup.RealShadowHero` (تغییر نکرد) | `app/build.gradle` |

مستندات README با پیکربندی واقعی مقایسه شد؛ ادعای README مبنای هیچ تصمیمی نبود.

## ۲. علت‌های کرش — تأییدشده با شواهد

### علت ۱ (قطعی): ناسازگاری نوع `loading_bar` → ClassCastException
- `love/src/main/res/layout/dialog_loading.xml` خط 11: `R.id.loading_bar` از نوع `CircularProgressIndicator`
- `love/src/main/java/org/love2d/android/gotoLevel.java` (قبل از فیکس) خط 14 و 43: `import LinearProgressIndicator` + `LinearProgressIndicator bar = view.findViewById(R.id.loading_bar)`
- نتیجه: **هر تاچ روی «آموزش» یا هر دکمه اپیزود** → `gotoLevel.onPreExecute` → `ClassCastException` هنگام cast → بستن برنامه. دکمه‌های فقط-منو (`start`, `Season_ONE`, …) که `gotoLevel` را صدا نمی‌زنند سالم می‌مانند — دقیقاً همان علامت گزارش‌شده.
- «بازتولید روی دستگاه انجام نشد» (دستگاه/شبیه‌ساز در دسترس نیست — بخش ۶)؛ این تحلیل بر اساس کد است و نوع crash آن (CCE در زمان cast در UI thread) از نظر معنایی جاوا قطعی است.

### علت ۲ (قطعی از تحلیل زنجیره کامل): ناسازگاری مسیر ذخیره Java↔Lua → کرش در `love.load`
زنجیره کامل: دکمه → `MainActivity.launchEpisode` → `gotoLevel` → نوشتن `chooser.txt` → `GameActivity` (SDL) → بارگذاری `game.love` → `love.load` → `episode_init()` → کرش.

شواهد مسیر فایل:
- Java می‌نویسد: `<getExternalFilesDir(null)>/save/archive/chooser.txt` (این مسیر در **هر دو نسل** Java — قالب اصلی `9483d62` خط 87 و نسخه فعلی — یکسان است)
- Lua می‌خواند: `episode_init_state.lua` → `jupiter.load("chooser.txt")` → `love.filesystem` ریشه
- ریشه ذخیره LÖVE اندروید (سورس love@216d5ca4): `Filesystem.cpp` بخش `LOVE_ANDROID`: `save_path_full = storage_path + "/save/" + save_identity` که `storage_path = SDL_AndroidGetExternalStoragePath()` (چون `t.externalstorage=true`) و identity از `boot.lua` (خط 332-364، 568) مشتق می‌شود؛ conf.lua بازی **هیچ `t.identity` نداشت** → ریشه Lua = `<ext>/save/<مشتق-از-نام-آرشیو>` (مثل `game`) ≠ `save/archive`
- نتیجه: `jupiter.load` مقدار **nil** برمی‌گرداند → `episode_init_state.lua` خط 7 (نسخه قبل از فیکس): `self.episode = choosed_episode[1]` روی nil → خطای Lua هنگام `love.load` → صفحه بازی هرگز بالا نمی‌آید

### فرضیه‌های بررسی‌شده و ردشده (با شواهد)
| فرضیه | شواهد رد | نتیجه |
|---|---|---|
| نبود `libSDL2.so` در APK (حذف توسط packagingOptions) عامل کرش است | `love@216d5ca4/Android.mk:101`: `LOCAL_STATIC_LIBRARIES := … SDL2_static` → liblove.so وابستگی رانتایم به SDL2 ندارد؛ `GameActivity.getLibraries()` هم SDL2 را بارگذاری نمی‌کند | حذف SDL2 **درست** است؛ تغییر نکرد |
| `love.filesystem.exists/getLastModified` حذف‌شده در LÖVE 11 عامل کرش است | `wrap_Filesystem.cpp` در کامیت پین‌شده (خطوط 945-960) هر چهار API `getInfo/exists/getLastModified/getSize` را ثبت می‌کند (deprecated ولی موجود) | کرش نمی‌دهد؛ فقط هشدار deprecation |

## ۳. تغییرات اعمال‌شده و دلیل هرکدام

### P0 — رفع کرش
| فایل | تغییر | دلیل |
|---|---|---|
| `main/conf.lua` | افزودن `t.identity = "archive"` | پین‌کردن ریشه ذخیره Lua روی `<ext>/save/archive` = همان مسیر Java؛ فیکس ساختاری قرارداد (روش رسمی LÖVE، نه راه‌حل هک‌گونه) |
| `love/.../gotoLevel.java` | بازنویسی: `CircularProgressIndicator` (هم‌نوع XML)، `AsyncTask<Void,Void,Boolean>` با برگرداندن نتیجه واقعی، نوشتن اتمیک chooser، دیالوگ خطای فارسی با «تلاش دوباره»، گارد تک‌نمونه (`launch`)، گارد `isFinishing/isDestroyed` | رفع CCE + «شکست نوشتن chooser.txt هرگز نباید به اجرای GameActivity منجر شود» + مهار دبل‌تپ |
| `love/.../SavePaths.java` (جدید) | تک‌منبع‌حقیقت مسیرها + `writeChooser` اتمیک + `migrateLegacyFiles` + sanitize ضد path-traversal + اعتبارسنجی شناسه اپیزود | قرارداد Java↔Lua یک‌جا، تست‌پذیر بدون دستگاه |
| `love/.../MainActivity.java` | سوییچ مسیرها به SavePaths، مهاجرت یک‌باره ذخیره‌های قدیمی (`save` و `save/unnamed` → `archive`)، پارس امن ستاره/زمان (رفع خطر `StringIndexOutOfBounds` روی خط کوتاه/خراب)، try/catch صداها | P1: سلامت ذخیره‌ها؛ «شکست صدا نباید مانع بازی شود» |
| `love/.../GameActivity.java` | `onBackPressed`: دیالوگ متریال + دکمه‌های فارسی + `FLAG_ACTIVITY_CLEAR_TOP|SINGLE_TOP` + `finish()` | رفع «چند منوی اصلی» و خاموشی تمیز موتور هنگام بازگشت به منو |
| `love/.../episode_init_state.lua` | اعتبارسنجی کامل انتخاب: فقط شناسه‌های واقعاً موجود (`train,s1e1..s2e3`)؛ نامعتبر/ناموجود/خراب → `train` (بازیابی کنترل‌شده)؛ گارد جدول خراب در `write_to_next` | «انتخاب نامعتبر نباید به level ناقص منجر شود» |
| `main/jupiter.lua` | بازنویسی `save`: ساخت محتوا در حافظه + `love.filesystem.write` (بستن قطعی فایل، برگرداندن خطا، ساخت پوشه والد) — حذف متغیرهای global (`file`,`x`) | رفع نشتی فایل باز و شکست خاموش |
| `main/main.lua` | `save_best_score_for_episode`: گارد فایل خالی/خراب/ناقص (بازنویسی به‌جای کرش مقایسه با nil) | P1 سلامت ذخیره‌ها |

### نکته مهاجرت ذخیره‌ها (بدون از دست رفتن پیشرفت)
- اپ فعلی (`com.SandSprogrammingGroup.RealShadowHero`) تا امروز هیچ بیلد سالمی نداشته (بیلد دیروز پیش از هر نوشتاری کرش می‌کرد) → ریسک از دست رفتن ذخیره کاربران این بسته: **صفر**.
- اپ بازار (`com.undeniables.ShadowHero`) applicationId جدا و دیتای جدا دارد و دست نخورده می‌ماند.
- با این حال `migrateLegacyFiles` فایل‌های سرگردان در `save/` و `save/unnamed/` را (در صورت وجود) بدون بازنویسی مقصد به `save/archive` کپی می‌کند — تست واحد دارد.

### P2 — بهبودهای همراه (اندازه‌گیری‌شده، نه بازطراحی گسترده)
- دیالوگ‌های سیستمی به Material3 و متن فارسی متمرکز در `strings.xml` (شامل دکمه‌های Yes/No که قبلاً انگلیسی بودند)
- متن‌های تکراری از کد به `strings.xml` منتقل شد؛ هویت بصری/تصاویر بازی دست نخورد
- بازطراحی گسترده منو/SCALE.lua طبق brief به بعد از تأیید اجرایی P0 موکول شد (بخش ۶)

## ۴. تست‌ها و نتایج واقعی

### اجراشده در همین محیط ✅
| تست | فرمان | نتیجه |
|---|---|---|
| سینتکس همه فایل‌های Lua با LuaJIT واقعی (بدون اجرا) | `./scripts/check_lua_syntax.sh main` | `checked 47 lua files … OK` |
| صحت بسته game.love (بوت در ریشه، resolve شدن ۱۱۲ require، ۱۲۵۸ منبع) | `python3 scripts/verify_love_package.py <game.love> main` | `game.love package integrity: OK` (۲ false positive اولیه مربوط به کد کامنت‌شده بود و اسکریپت اصلاح شد) |
| فراخوانی‌های save/unlock در Lua | ردیابی استاتیک | همه‌ی callers پوشش داده شدند (`level.name` = `s1e1` …) |

### اجراشده در CI (GitHub Actions، بدون انتشار) ✅/⏳
- `./gradlew :love:testNormalReleaseUnitTest` — تست‌های JVM قرارداد SavePaths (۷ تست: مسیرها، فرمت chooser، اعتبارسنجی شناسه، اتمیک بودن، رد traversal، مهاجرت از ریشه و از unnamed)
- `./gradlew assembleNormalRelease` — بیلد واقعی APK
- نتیجه: پس از پوش شاخه، ران «build-test» لینک داده می‌شود؛ این گزارش با وضعیت نهایی ران به‌روز می‌شود.

### اجرانشده (صریحاً ثبت می‌شود) ❌
- نصب تمیز روی دستگاه، ۲۰ چرخه شروع/بازگشت، چندلمسی، توقف/ادامه، برد/باخت، بازگشت پس از kill فرایند، نسبت‌های ۱۸:۹/۲۰:۹ — **دستگاه/شبیه‌ساز اندروید در این محیط موجود نیست.** ادعای «بدون کرش» به این سناریوها تعمیم داده نمی‌شود.
- APK امضاشده release در این شاخه ساخته نشد (طبق brief: بدون انتشار خودکار؛ ورک‌فلوی build-test فقط artifact unsigned می‌سازد).

## ۵. تأیید اجرایی روی دستگاه (برای شما)
```bash
# نصب بیلد تست (unsigned — فقط روی دستگاه developer)
adb install -r -t app-normal-release-unsigned.apk
# لاگ زنده هنگام ورود به مرحله
adb logcat -c && adb logcat | grep -E "gotoLevel|GameActivity|LOVE|FATAL|ClassCast|AndroidRuntime"
```
سناریوی حداقلی تأیید: منو → «آموزش» → رندر صحنه + ورودی لمسی ≥۳۰ ثانیه → back → «بله» → منو → اپیزود s1e1 → تکرار. انتظار: بدون `AndroidRuntime: FATAL` و بدون `ClassCastException`.

## ۶. محدودیت‌ها و خطرهای باقی‌مانده
1. تأیید نهایی اجرایی نیازمند دستگاه واقعی است (بخش ۴/۵) — رفع کرش تا زمان اجرای سناریوی ۵ «تحلیل‌محور» تلقی شود.
2. `AsyncTask` منسوخ است ولی بر اساس brief framework جدید اضافه نشد؛ رفتار آن روی API 26-34 سالم است.
3. `t.console = true` در conf.lua روی اندروید release لاگ کنسول اضافه می‌کند — کاندیدای حذف در P2 (تغییر داده نشد تا رفتار فعلی حفظ شود).
4. `SCALE.lua` و چیدمان افقی منو (P2 کامل) هنوز بازطراحی نشده‌اند؛ فقط مسیر بحرانی P0/P1 اصلاح شد.
5. ریسک کم‌احتمال: اگر روی دستگاهی `getExternalFilesDir(null)` null برگرداند، `SplashActivity.baseDir` به `getFilesDir()` fallback می‌کند ولی identity LÖVE همچنان external را ترجیح می‌دهد — این حالت در دستگاه‌های عادی رخ نمی‌دهد و فقط ثبت شد.

## ۷. ساخت و بیلد
```bash
# تست کامل محلی/CI (بدون انتشار)
./scripts/check_lua_syntax.sh main
mkdir -p app/src/main/assets && (cd main && zip -r -X ../app/src/main/assets/game.love .)
python3 scripts/verify_love_package.py app/src/main/assets/game.love main
./gradlew :love:testNormalReleaseUnitTest assembleNormalRelease
# خروجی APK: app/build/outputs/apk/normal/release/
```
