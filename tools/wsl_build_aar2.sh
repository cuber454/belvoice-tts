#!/bin/bash
# Доустановка torch в WSL и сборка ExecuTorch AAR.
set -e
export PATH="$HOME/.local/bin:$PATH"

echo "=== инструменты"
cmake --version | head -1
ninja --version

if ! python3 -c "import torch" 2>/dev/null; then
    echo "=== ставлю torch (CPU) в user-site"
    python3 -m pip install -q --user --break-system-packages torch --index-url https://download.pytorch.org/whl/cpu
fi
python3 -c "import torch; print('torch', torch.__version__)"

echo "=== сборка AAR (клон executorch + cmake + ninja; долго)"
cd /mnt/d/iiwork/work/belvoice-tts
ANDROID_NDK=/mnt/c/AndroidSDK/ndk/30.0.16248370 \
PYTHON_EXECUTABLE="$(command -v python3)" \
    bash tools/build_executorch_aar.sh /home/serge/et-build 2>&1 | tail -50

echo "=== результат:"
ls -la app/libs/ 2>/dev/null || echo "AAR НЕ ПОЯВИЛСЯ"
