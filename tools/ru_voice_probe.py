"""Может ли русский голос (v5_5_ru) читать по-белорусски?

Проверка: в алфавите русской модели нет «і» и «ў». Пробуем два обхода — «ў» как «в» и как «у»,
а «і» как «и» — и сравниваем с нашим белорусским голосом на той же фразе.

Пишет mp3: 1–2 русские голоса с подменами букв, 3 — наш bel_larisa.
"""
import os
import re
import subprocess
import sys
import wave

import numpy as np
import torch
from torch.jit.mobile import _load_for_lite_interpreter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RU = os.path.join(HERE, "v5_5_ru.pt")
BEL = os.path.join(HERE, "v5_cis_base_nostress.pt")
ACCENTOR = os.path.join(ROOT, "app", "src", "main", "assets", "silero", "accentor.ptl")
FFMPEG = os.environ.get(
    "FFMPEG",
    r"C:\Users\cubic\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.0.1-full_build\bin\ffmpeg.exe")
SR = 48000
VOWELS = "аеёіоуыэюя"
TEXT = "Быў цёплы дзень, і мы чыталі кнігу пра нашу краіну."
NUMBERS = ["перш+ы", "друг+і", "тр+эці"]


def accent(acc, text):
    out = []
    for token in re.split(r"(\W+)", text):
        if not token or not any(c.isalpha() for c in token):
            out.append(token); continue
        w = token.lower()
        v = [i for i, c in enumerate(w) if c in VOWELS]
        if len(v) < 2:
            out.append(token); continue
        with torch.no_grad():
            row = acc([w])[0][0]
        k = max(range(min(len(v), len(row))), key=lambda j: float(row[j]))
        p = v[k]
        out.append(token[:p] + "+" + token[p:])
    return "".join(out)


def main():
    acc = _load_for_lite_interpreter(ACCENTOR)
    marked = accent(acc, TEXT)
    print("наша фраза с ударениями:", marked)

    variants = [
        ("русский голос, ў→в, і→и", marked.replace("ў", "в").replace("ў", "в").replace("і", "и").replace("Ў", "В").replace("І", "И")),
        ("русский голос, ў→у, і→и", marked.replace("ў", "у").replace("і", "и")),
    ]
    for name, t in variants:
        print(f"  {name}: {t}")

    pieces = []

    def say(model, text, speaker, sr=SR):
        with torch.no_grad():
            a = model.apply_tts(text=text, speaker=speaker, sample_rate=sr)
        return np.asarray(a, dtype="float32").reshape(-1)

    ru = torch.package.PackageImporter(RU).load_pickle("tts_models", "model")
    print("русские голоса:", sorted(set(ru.speaker_to_ids)) if hasattr(ru, "speaker_to_ids") else "?")

    for i, (name, t) in enumerate(variants):
        pieces.append(say(ru, NUMBERS[i], "kseniya"))
        pieces.append(np.zeros(int(0.35 * SR), dtype="float32"))
        pieces.append(say(ru, t.lower(), "kseniya"))
        pieces.append(np.zeros(int(1.1 * SR), dtype="float32"))

    bel = torch.package.PackageImporter(BEL).load_pickle("tts_models", "model")
    pieces.append(say(bel, NUMBERS[2], "bel_larisa"))
    pieces.append(np.zeros(int(0.35 * SR), dtype="float32"))
    pieces.append(say(bel, marked.lower(), "bel_larisa"))
    pieces.append(np.zeros(int(1.1 * SR), dtype="float32"))

    audio = np.clip(np.concatenate(pieces), -1, 1)
    out = os.path.join(ROOT, "build", "ru-bel.mp3")
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    tmp = out + ".wav"
    with wave.open(tmp, "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR)
        f.writeframes((audio * 32767).astype("int16").tobytes())
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", tmp, "-codec:a", "libmp3lame",
                    "-b:a", "64k", "-ac", "1", out], check=True)
    print("звук:", out, round(os.path.getsize(out) / 1024), "КБ")


if __name__ == "__main__":
    sys.exit(main())
