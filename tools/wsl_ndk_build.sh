#!/bin/bash
# Linux-NDK r28c + cmake 3.31 (у автора ≥3.28, но 4.x снимает совместимость) и сборка AAR.
set -e
export PATH="$HOME/.local/bin:$PATH"
REPO=/mnt/d/iiwork/work/belvoice-tts
WORK=/home/serge/et-build
NDK=$HOME/android-ndk-r28c

echo "=== cmake 3.31"
python3 -m pip install -q --user --break-system-packages "cmake==3.31.6"
cmake --version | head -1

if [ ! -d "$NDK" ]; then
    echo "=== качаю NDK r28c (linux, ~700 МБ)"
    cd "$HOME"
    curl -sSL -o ndk-r28c.zip https://dl.google.com/android/repository/android-ndk-r28c-linux.zip
    ls -la ndk-r28c.zip
    unzip -q ndk-r28c.zip
    rm -f ndk-r28c.zip
fi
ls -d "$NDK"
"$NDK"/toolchains/llvm/prebuilt/linux-x86_64/bin/clang --version | head -1

echo "=== сборка AAR"
cd "$REPO"
for f in tools/*.sh; do sed -i 's/\r$//' "$f"; done
ANDROID_NDK="$NDK" PYTHON_EXECUTABLE="$(command -v python3)" \
    bash tools/build_executorch_aar.sh "$WORK" 2>&1 | tail -40

echo "=== результат:"
ls -la app/libs/ 2>/dev/null || echo "AAR НЕ ПОЯВИЛСЯ"
