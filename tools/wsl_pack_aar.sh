#!/bin/bash
# Упаковка ExecuTorch AAR из уже собранного дерева (шаги 33-41 build_executorch_aar.sh).
set -e
REPO=/mnt/d/iiwork/work/belvoice-tts
WORK=/home/serge/et-build5
NDK=$HOME/android-ndk-r28c
AAR=$REPO/app/libs/executorch-1.5.0-xnnpack.aar
BUILD=$WORK/executorch/cmake-out-android-arm64-v8a

mkdir -p "$REPO/app/libs"
SO=$(ls "$BUILD"/extension/android/*.so | head -1)
echo "=== so: $SO"
"$NDK"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip "$SO"

echo "=== проверка импортов libc (android-23):"
if "$NDK"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm -D --undefined-only "$SO" | grep '@LIBC_'; then
    echo "ОШИБКА: импорт libc новее API 23" >&2; exit 1
fi
echo "ok"

rm -rf "$WORK/aar"
mkdir -p "$WORK/aar/jni/arm64-v8a"
cd "$WORK/aar"
unzip -q "$WORK/executorch.aar"
cp "$SO" jni/arm64-v8a/libexecutorch.so
rm -f "$AAR"
zip -q -r "$AAR" .
ls -la "$AAR"
echo "=== размер: $(du -m "$AAR" | cut -f1) МБ"
