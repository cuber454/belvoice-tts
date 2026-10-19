#!/bin/bash
# Разведка окружения WSL под сборку ExecuTorch AAR (без sudo).
echo "--- user: $(whoami)  home: $HOME"
echo "--- python:"; (python3 --version 2>&1 || echo MISSING)
echo "--- python3-venv:"; (python3 -c "import venv; print('ok')" 2>&1 || echo MISSING)
echo "--- tools:"
for t in cmake ninja git zip unzip curl pip3 g++; do printf '%-6s ' "$t"; (command -v "$t" || echo MISSING); done
echo "--- cmake version:"; (cmake --version 2>&1 | head -1 || echo MISSING)
echo "--- ndk:"; ls -d /mnt/c/AndroidSDK/ndk/* 2>/dev/null
echo "--- sudo без пароля:"; (sudo -n true 2>/dev/null && echo "ДА" || echo "нет")
echo "--- venv-et:"; ls -d "$HOME/venv-et" 2>/dev/null || echo "нет"
echo "--- память:"; free -g | awk 'NR<=2'
