#!/usr/bin/env python3
"""بررسی سینتکس همه فایل‌های Lua با LuaJIT واقعی (از طریق lupa) — بدون اجرای کد بازی.

loadfile فقط کامپایل می‌کند؛ هیچ فایل بازی اجرا نمی‌شود.
استفاده: python3 check_lua_syntax_lupa.py <main-src-dir>
"""
import sys
from pathlib import Path

from lupa import LuaRuntime

lua = LuaRuntime(unpack_returned_tuples=True)
compile_lua = lua.eval(
    "function(path) local f, err = loadfile(path) if f then return true, nil else return false, err end end")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "main")
    files = sorted(root.rglob("*.lua"))
    errors = 0
    for f in files:
        ok, err = compile_lua(str(f))
        if not ok:
            errors += 1
            print(f"SYNTAX ERROR: {f}\n    {err}")
    print(f"checked {len(files)} lua files with LuaJIT (lupa)")
    if errors:
        print(f"lua syntax check: FAIL ({errors} errors)")
        return 1
    print("lua syntax check: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
