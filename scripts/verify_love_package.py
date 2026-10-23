#!/usr/bin/env python3
"""تست صحت بسته game.love — بدون نیاز به LÖVE یا اندروید.

بررسی‌ها:
1) main.lua و conf.lua در ریشه آرشیو zip هستند (قرارداد بوت LÖVE).
2) همه require("...")های لیتورال به فایل موجود داخل بسته اشاره می‌کنند.
3) مسیرهای لیتورال love.graphics.newImage / love.audio.newSource / newFont داخل بسته موجودند.

استفاده: python3 verify_love_package.py <game.love> <source-dir-main/>
خروجی: کد برگشت ۰ = سالم؛ ۱ = شکست (با فهرست موارد مفقود)
"""
import re
import sys
import zipfile
from pathlib import Path


def lua_module_candidates(module: str):
    p = module.replace(".", "/")
    return {p + ".lua", p + "/init.lua"}


def strip_lua_comments(text: str) -> str:
    """حذف کامنت‌های بلوکی و خطی Lua تا کد داخل کامنت بررسی نشود."""
    text = re.sub(r"--\[(=*)\[[\s\S]*?\]\1\]", "", text)
    text = re.sub(r"--[^\n]*", "", text)
    return text


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: verify_love_package.py <game.love> <main-src-dir>")
        return 2
    love_path, src_dir = Path(sys.argv[1]), Path(sys.argv[2])

    with zipfile.ZipFile(love_path) as z:
        names = set(z.namelist())
        lua_sources = {}
        for n in sorted(names):
            if n.endswith(".lua") and not n.endswith("/"):
                try:
                    lua_sources[n] = strip_lua_comments(z.read(n).decode("utf-8", "replace"))
                except Exception as e:  # noqa: BLE001
                    print(f"READ ERROR {n}: {e}")
                    return 1

        errors = []

        # 1) نقطه بوت
        for required in ("main.lua", "conf.lua", "jupiter.lua"):
            if required not in names:
                errors.append(f"missing boot file at zip root: {required}")

        # 2) resolve کردن requireهای لیتورال
        req_re = re.compile(r"""require\s*\(\s*["']([^"']+)["']\s*\)|require\s+["']([^"']+)["']""")
        unresolved = set()
        checked_requires = 0
        for name, text in lua_sources.items():
            for m in req_re.finditer(text):
                mod = m.group(1) or m.group(2)
                if not mod or "$" in mod or (".." in mod):
                    continue  # مسیر داینامیک/غیرمعتبر از این بررسی مستثناست
                checked_requires += 1
                cands = lua_module_candidates(mod)
                if not (cands & names):
                    unresolved.add(f"{mod} (required by {name})")
        for u in sorted(unresolved):
            errors.append(f"unresolved require: {u}")

        # 3) منابع لیتورال
        res_re = re.compile(
            r"""(?:newImage|newSource|newFont|newImageFont)\(\s*["']([^"']+)["']""")
        missing_res = set()
        checked_res = 0
        for name, text in lua_sources.items():
            for m in res_re.finditer(text):
                p = m.group(1)
                if "%" in p or (".png" not in p and ".ogg" not in p and ".mp3" not in p
                                and ".wav" not in p and ".ttf" not in p):
                    continue  # داینامیک یا بدون پسوند شناخته‌شده
                checked_res += 1
                if p not in names:
                    missing_res.add(f"{p} (referenced by {name})")
        for r in sorted(missing_res):
            errors.append(f"missing resource: {r}")

    print("boot files: FAIL" if any("boot file" in e for e in errors) else "boot files: OK")
    print(f"requires checked: {checked_requires}, unresolved: {len(unresolved)}")
    print(f"resources checked: {checked_res}, missing: {len(missing_res)}")
    if errors:
        print("\n".join("ERROR: " + e for e in errors))
        return 1
    print("game.love package integrity: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
