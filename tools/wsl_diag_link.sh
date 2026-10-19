#!/bin/bash
# Диагностика линковки: живы ли ar/ranlib/lld из NDK r28c.
NDK=$HOME/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin
echo "=== файлы:"
ls -la "$NDK"/llvm-ar "$NDK"/llvm-ranlib "$NDK"/ld.lld 2>&1 | head -5
echo "=== llvm-ar:"
"$NDK"/llvm-ar --version 2>&1 | head -3
echo "exit=$?"
echo "=== llvm-ranlib:"
"$NDK"/llvm-ranlib --version 2>&1 | head -3
echo "exit=$?"
echo "=== lld:"
"$NDK"/ld.lld --version 2>&1 | head -3
echo "exit=$?"
echo "=== какая команда линковки была (из build.make):"
grep -m2 -n "kleidiai.a" /home/serge/et-build/executorch/cmake-out-android-arm64-v8a/kleidiai/CMakeFiles/kleidiai.dir/build.make 2>/dev/null | head -5
grep -m3 -n "CMAKE_AR\|CMAKE_RANLIB\|link.txt\|ar qc" /home/serge/et-build/executorch/cmake-out-android-arm64-v8a/kleidiai/CMakeFiles/kleidiai.dir/link.txt 2>/dev/null
cat /home/serge/et-build/executorch/cmake-out-android-arm64-v8a/kleidiai/CMakeFiles/kleidiai.dir/link.txt 2>/dev/null | head -2
