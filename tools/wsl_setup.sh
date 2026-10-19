#!/bin/bash
# Подготовка WSL к сборке ExecuTorch AAR: venv + cmake + ninja + executorch 1.5.0.
# Без sudo: всё ставим в venv (apt недоступен — sudo просит пароль).
set -e
VENV="$HOME/venv-et"
if [ ! -x "$VENV/bin/pip" ]; then
    echo "--- создаю venv"
    python3 -m venv "$VENV"
fi
"$VENV/bin/pip" install -q --upgrade pip wheel
echo "--- ставлю cmake, ninja, executorch 1.5.0 (тянет torch)"
"$VENV/bin/pip" install -q cmake ninja "executorch==1.5.0"
echo "--- проверка:"
"$VENV/bin/python" - <<'PY'
import torch, executorch
print("torch", torch.__version__)
print("executorch", getattr(executorch, "__version__", "?"))
PY
"$VENV/bin/cmake" --version | head -1
"$VENV/bin/ninja" --version
echo "--- готово"
