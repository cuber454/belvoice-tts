#!/bin/bash
# Четвёртая попытка: хост-компилятор C/C++ — zig из pip-пакета (переменные CC/CXX через скрипты-обёртки).
set -o pipefail
export PATH="$HOME/.local/bin:$PATH"
REPO=/mnt/d/iiwork/work/belvoice-tts
WORK=/home/serge/et-build5
NDK=$HOME/android-ndk-r28c
LOG=$HOME/aar-build5.log
ZIG=$(python3 -c "import ziglang, os; print(os.path.join(os.path.dirname(ziglang.__file__), 'zig'))")
BIN=$HOME/.local/bin

echo "=== zig: $ZIG"
"$ZIG" version

printf '#!/bin/sh\nexec "%s" cc "$@"\n' "$ZIG" > "$BIN/zigcc"
printf '#!/bin/sh\nexec "%s" c++ "$@"\n' "$ZIG" > "$BIN/zigcxx"
chmod +x "$BIN/zigcc" "$BIN/zigcxx"
echo "=== обёртки:"
"$BIN/zigcc" --version | head -2

echo "=== сборка (лог: $LOG)"
cd "$REPO"
for f in tools/*.sh; do sed -i 's/\r$//' "$f"; done
CC="$BIN/zigcc" CXX="$BIN/zigcxx" \
ANDROID_NDK="$NDK" PYTHON_EXECUTABLE="$(command -v python3)" \
    bash tools/build_executorch_aar.sh "$WORK" >"$LOG" 2>&1
RC=$?
echo "=== rc=$RC"
grep -n -i -m 25 -E 'error|fatal|No CMAKE|undefined' "$LOG" | head -25
echo "=== хвост:"
tail -12 "$LOG"
echo "=== результат:"
ls -la app/libs/ 2>/dev/null || echo "AAR НЕ ПОЯВИЛСЯ"
