#!/usr/bin/env bash
# بررسی سینتکس همه فایل‌های Lua با LuaJIT (همان مفسر موتور بازی) — بدون اجرای کد
# استفاده: ./check_lua_syntax.sh <main-src-dir>
set -uo pipefail
ROOT="${1:-main}"

if ! command -v luajit >/dev/null 2>&1; then
  # fallback: LuaJIT واقعی از طریق بسته python lupa (همان مفسر موتور بازی)
  if python3 -c "import lupa" 2>/dev/null; then
    exec python3 "$(dirname "$0")/check_lua_syntax_lupa.py" "$ROOT"
  fi
  echo "neither luajit binary nor python-lupa found"
  exit 2
fi

fail=0
count=0
while IFS= read -r -d '' f; do
  count=$((count + 1))
  # loadfile فقط کامپایل می‌کند؛ هیچ کدی اجرا نمی‌شود
  if ! luajit -e "local f, err = loadfile([==[$f]==]); if not f then print(err) os.exit(1) end" ; then
    echo "SYNTAX ERROR: $f"
    fail=1
  fi
done < <(find "$ROOT" -name '*.lua' -print0)

echo "checked $count lua files"
if [ "$fail" -ne 0 ]; then
  echo "lua syntax check: FAIL"
  exit 1
fi
echo "lua syntax check: OK"
