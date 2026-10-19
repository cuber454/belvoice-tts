"""Проба с озвученными номерами: «Першы. <слово>. Другі. <слово>…»

Нужно для проверки на слух без экрана: Серж говорит «пяты нумар», и по номеру мы попадаем
в нужный вариант. Номера читаются по-белорусски, с ударением от акцентора.

Запуск: python tools/numbered_probe.py out.mp3 <файл со списком: по строке на вариант>
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
FFMPEG = os.environ.get(
    "FFMPEG",
    r"C:\Users\cubic\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.0.1-full_build\bin\ffmpeg.exe")
SR = 48000
SPEAKER = "bel_larisa"
VOWELS = "аеёіоуыэюя"

NUMBERS = ["нуль", "перш+ы", "друг+і", "тр+эці", "чацв+ёрты", "п+яты", "ш+осты", "с+ёмы",
           "в+осьмы", "дзяв+яты", "дзяся+ты", "адзінаццаты", "дванаццаты", "трынаццаты"]


def mark(acc, word):
    """Ставит «+» акцентором, если в слове больше одной гласной и метки ещё нет."""
    if "+" in word:
        return word
    vowels = [i for i, c in enumerate(word) if c in VOWELS]
    if len(vowels) < 2:
        return word
    with torch.no_grad():
        row = acc([word])[0][0]
    best = max(range(min(len(vowels), len(row))), key=lambda j: float(row[j]))
    p = vowels[best]
    return word[:p] + "+" + word[p:]


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out = sys.argv[1]
    items = []
    for l in open(sys.argv[2], encoding="utf-8"):
        l = l.strip()
        if not l or l.startswith("#"):
            continue
        if "|" in l:
            sp, txt = l.split("|", 1)
            items.append((sp.strip(), txt.strip()))
        else:
            items.append((None, l))
    if len(items) > len(NUMBERS) - 1:
        sys.exit(f"максимум {len(NUMBERS) - 1} вариантов")

    big = torch.package.PackageImporter(MODEL).load_pickle("tts_models", "model")
    acc = _load_for_lite_interpreter(ACCENTOR)

    pieces = []
    for i, (sp, item) in enumerate(items, start=1):
        number = NUMBERS[i]
        word = mark(acc, item)
        speaker = sp or SPEAKER
        print(f"{i:2d}. {number:14s} {speaker:13s} {word}")
        for text in (number.rstrip("."), word):
            with torch.no_grad():
                audio = big.apply_tts(text=text, speaker=speaker, sample_rate=SR)
            pieces.append(np.asarray(audio, dtype="float32").reshape(-1))
            pieces.append(np.zeros(int(0.35 * SR), dtype="float32"))
        pieces.append(np.zeros(int(0.9 * SR), dtype="float32"))

    os.makedirs(os.path.dirname(out), exist_ok=True)
    audio = np.clip(np.concatenate(pieces), -1, 1)
    tmp = out + ".wav"
    with wave.open(tmp, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes((audio * 32767).astype("int16").tobytes())
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", tmp, "-codec:a", "libmp3lame",
                    "-b:a", "64k", "-ac", "1", out], check=True)
    print("звук:", out, round(os.path.getsize(out) / 1024), "КБ")


if __name__ == "__main__":
    main()
