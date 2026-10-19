"""Проверка ударений на слух: слово звучит дважды — вариант акцентора и вариант словаря.

Запуск:  python tools/say_words.py words.txt [out.mp3]
words.txt — по слову в строке, «#» — комментарий.

Печатает таблицу «слово — акцентор / словарь» и пишет mp3: вариант акцентора, пауза 0,4 с,
вариант словаря, пауза 1,2 с. Если варианты совпали, слово звучит один раз.

Нужна модель: tools/v5_cis_base_nostress.pt (скачивается один раз, в git не хранится).
"""
import os
import subprocess
import sys
import wave

import numpy as np
import torch
from torch.jit.mobile import _load_for_lite_interpreter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MODEL = os.path.join(HERE, "v5_cis_base_nostress.pt")
ACCENTOR = os.path.join(ROOT, "app", "src", "main", "assets", "silero", "accentor.ptl")
DICT = os.path.join(ROOT, "build", "bel-stress.txt")
FFMPEG = os.environ.get(
    "FFMPEG",
    r"C:\Users\cubic\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.0.1-full_build\bin\ffmpeg.exe")

SR = 48000
SPEAKER = "bel_larisa"
VOWELS = "аеёіоуыэюя"


def accentor_variant(acc, word):
    vowels = [i for i, c in enumerate(word) if c in VOWELS]
    if not vowels:
        return word
    with torch.no_grad():
        row = acc([word])[0][0]
    best = max(range(min(len(vowels), len(row))), key=lambda j: float(row[j]))
    p = vowels[best]
    return word[:p] + "+" + word[p:]


def load_dict():
    d = {}
    if os.path.exists(DICT):
        with open(DICT, encoding="utf-8") as f:
            for line in f:
                if line.startswith("#"):
                    continue
                parts = line.split()
                if len(parts) == 2:
                    d[parts[0]] = parts[1]
    return d


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    words = [l.strip().lower() for l in open(sys.argv[1], encoding="utf-8")
             if l.strip() and not l.startswith("#")]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "build", "words.mp3")
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)

    acc = _load_for_lite_interpreter(ACCENTOR)
    big = torch.package.PackageImporter(MODEL).load_pickle("tts_models", "model")
    dict_words = load_dict()

    print(f"{'слово':20s} {'акцентор':22s} словарь")
    pieces = []
    for w in words:
        a = accentor_variant(acc, w)
        d = dict_words.get(w)
        print(f"{w:20s} {a:22s} {d or '— нет в словаре'}")
        variants = [a] if not d or d == a else [a, d]
        for i, v in enumerate(variants):
            with torch.no_grad():
                audio = big.apply_tts(text=v, speaker=SPEAKER, sample_rate=SR)
            pieces.append(np.asarray(audio, dtype="float32").reshape(-1))
            if i < len(variants) - 1:
                pieces.append(np.zeros(int(0.4 * SR), dtype="float32"))
        pieces.append(np.zeros(int(1.2 * SR), dtype="float32"))

    audio = np.clip(np.concatenate(pieces), -1, 1)
    tmp = os.path.join(ROOT, "build", "words.wav")
    with wave.open(tmp, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes((audio * 32767).astype("int16").tobytes())
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", tmp, "-codec:a", "libmp3lame",
                    "-b:a", "64k", "-ac", "1", out], check=True)
    print("\nзвук:", out, round(os.path.getsize(out) / 1024), "КБ")


if __name__ == "__main__":
    main()
