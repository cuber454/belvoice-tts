#!/bin/bash
# Установка зависимостей в WSL (без sudo, в user-site) и сборка ExecuTorch AAR.
set -e
PIP="python3 -m pip"
NDK=/mnt/c/AndroidSDK/ndk/30.0.16248370
REPO=/mnt/d/iiwork/work/belvoice-tts
WORK=/home/serge/et-build

echo "=== pip: ставлю cmake, ninja, executorch 1.5.0"
$PIP install -q --user --break-system-packages --upgrade pip wheel
$PIP install -q --user --break-system-packages cmake ninja "executorch==1.5.0"
python3 -c "import torch, executorch; print('torch', torch.__version__); print('executorch ok')"
export PATH="$HOME/.local/bin:$PATH"
cmake --version | head -1
ninja --version

echo "=== сборка AAR (это долго: клон executorch + cmake + ninja)"
cd "$REPO"
ANDROID_NDK="$NDK" PYTHON_EXECUTABLE="$(command -v python3)" PATH="$PATH" \
    bash tools/build_executorch_aar.sh "$WORK" 2>&1 | tail -40
echo "=== готово, AAR:"
ls -la "$REPO/app/libs/" 2>/dev/null || echo "AAR НЕ ПОЯВИЛСЯ"
