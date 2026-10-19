#!/bin/bash
# LF для скриптов (git на Windows отдал CRLF) и сборка ExecuTorch AAR.
set -e
export PATH="$HOME/.local/bin:$PATH"
cd /mnt/d/iiwork/work/belvoice-tts

echo "=== перевожу .sh на LF"
for f in tools/*.sh; do sed -i 's/\r$//' "$f"; done
file tools/build_executorch_aar.sh

echo "=== сборка AAR"
ANDROID_NDK=/mnt/c/AndroidSDK/ndk/30.0.16248370 \
PYTHON_EXECUTABLE="$(command -v python3)" \
    bash tools/build_executorch_aar.sh /home/serge/et-build 2>&1 | tail -60

echo "=== результат:"
ls -la app/libs/ 2>/dev/null || echo "AAR НЕ ПОЯВИЛСЯ"
