#!/bin/bash
# Третья попытка: хост-компилятор C++ через zig (pip-пакет, без sudo).
set -o pipefail
export PATH="$HOME/.local/bin:$PATH"
REPO=/mnt/d/iiwork/work/belvoice-tts
WORK=/home/serge/et-build3
NDK=$HOME/android-ndk-r28c
LOG=$HOME/aar-build3.log

echo "=== zig"
if ! command -v zig >/dev/null; then
    python3 -m pip install -q --user --break-system-packages ziglang
fi
zig version
zig c++ --version | head -1

echo "=== сборка (лог: $LOG)"
cd "$REPO"
for f in tools/*.sh; do sed -i 's/\r$//' "$f"; done
CC="zig cc" CXX="zig c++" \
ANDROID_NDK="$NDK" PYTHON_EXECUTABLE="$(command -v python3)" \
    bash tools/build_executorch_aar.sh "$WORK" >"$LOG" 2>&1
RC=$?
echo "=== rc=$RC"
grep -n -i -m 20 -E 'error|fatal|No CMAKE' "$LOG" | head -20
echo "=== хвост лога:"
tail -15 "$LOG"
echo "=== результат:"
ls -la app/libs/ 2>/dev/null || echo "AAR НЕ ПОЯВИЛСЯ"
