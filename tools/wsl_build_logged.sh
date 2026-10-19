#!/bin/bash
# Сборка ExecuTorch AAR с полным логом (tail в прошлый раз съел код ошибки).
set -o pipefail
export PATH="$HOME/.local/bin:$PATH"
REPO=/mnt/d/iiwork/work/belvoice-tts
WORK=/home/serge/et-build2
NDK=$HOME/android-ndk-r28c
LOG=$HOME/aar-build.log

cmake --version | head -1
"$NDK"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar --version | head -1 || echo "llvm-ar НЕ РАБОТАЕТ"

cd "$REPO"
for f in tools/*.sh; do sed -i 's/\r$//' "$f"; done
ANDROID_NDK="$NDK" PYTHON_EXECUTABLE="$(command -v python3)" \
    bash tools/build_executorch_aar.sh "$WORK" >"$LOG" 2>&1
RC=$?
echo "=== rc=$RC"
echo "=== последние строки лога:"
tail -50 "$LOG"
echo "=== результат:"
ls -la app/libs/ 2>/dev/null || echo "AAR НЕ ПОЯВИЛСЯ"
