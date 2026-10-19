#!/bin/bash
echo "HOME=$HOME"
echo "--- zig в PATH:"; command -v zig || echo "нет"
echo "--- console scripts:"
ls -la "$HOME/.local/bin" | grep -i -E 'zig|flatc|ninja|cmake' || true
echo "--- пакет ziglang:"
python3 -c "import ziglang, os; d=os.path.dirname(ziglang.__file__); print(d); print([f for f in os.listdir(d) if 'zig' in f][:10])"
