# Shadow Hero — قهرمان سایه‌ای

پروژه کامل و متمرکز بازی **قهرمان سایه‌ای**: هم کد بازی (LÖVE2D) و هم پوسته اندروید + خط تولید بیلد CI در یک مخزن.

## ساختار مخزن

```
ShadowHero/
├── main/                ← سورس کامل بازی LÖVE2D (main.lua، conf.lua، jupiter.lua، ART/، CODES/، MUSIC/)
├── app/                 ← ماژول اپلیکیشن اندروید (شناسه: com.SandSprogrammingGroup.RealShadowHero)
├── love/                ← ماژول ران‌تایم LÖVE (native + GUI جاوا با Material Design 3)
├── .github/workflows/   ← ورک‌فلوی بیلد خودکار و انتشار در Releases
├── build.gradle         ← AGP 8.1.1 / Gradle 8.2.1 / JDK 17
└── settings.gradle
```

## مشخصات بیلد اندروید

| مورد | مقدار |
|------|-------|
| minSdk | **26** (اندروید ۸٫۰) |
| targetSdk | **34** |
| compileSdk | 34 + NDK 26.1 |
| رابط کاربری | **Material Design 3** (تم تیره بنفش + Dynamic Colors روی اندروید ۱۲+) |
| APK | امضاشده و قابل نصب مستقیم |
| ABI | armeabi-v7a و arm64-v8a |

> سازگار با **اندروید ۸ (API 26) تا اندروید ۱۵** — روی اندروید ۱۵ بدون مشکل اجرا می‌شود.

## نحوه گرفتن بیلد (GitHub Actions)

1. به تب **Actions** بروید و ورک‌فلوی **android** را انتخاب کنید.
2. روی **Run workflow** بزنید (لینک مستقیم `game.love` اختیاری است؛ پیش‌فرض از پوشه `main/` ساخته می‌شود).
3. پس از پایان بیلد، APK امضاشده در بخش **Releases** (تگ `shadowhero-<date>`) و همچنین در **Artifacts** همان ران قابل دانلود است.

ورک‌فلو به‌صورت خودکار:
- محتوای پوشه `main/` را به `game.love` تبدیل و در assets قرار می‌دهد،
- keystore بیلد را می‌سازد (یا از secretهای `KEYSTORE_BASE64` و `SIGNING_STORE_PASSWORD` استفاده می‌کند)،
- `assembleNormal` را اجرا و APK امضاشده را در Releases منتشر می‌کند.

## بیلد لوکال (اختیاری)

```bash
mkdir -p app/src/main/assets
cd main && zip -r -X ../app/src/main/assets/game.love . && cd ..
./gradlew assembleNormal
# خروجی: app/build/outputs/apk/normal/release/
```

> نکته: اولین بیلد، ساب‌ماژول `love/src/jni/love` را از مخزن رسمی love2d/love می‌گیرد (`git submodule update --init --recursive`).
