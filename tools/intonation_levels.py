"""Проба пяти уровней интонации: та же математика, что в Intonation.kt, но на модели напрямую.

Нужно, чтобы выбрать силу до сборки APK: каждый вариант звучит со своим номером («Першы», «Другі»…),
Серж отвечает «трэці» — этот уровень и ставим.

Уровни (см. Intonation.kt):
  вопрос  — подъём на последней трети фразы: 16 / 28 / 40 / 52 / 64 %;
  восклицание — вся фраза выше на 5 / 11 / 17 / 23 / 29 % и спад в самом конце на 10 / 20 / 30 / 40 / 50 %.

Запуск: python tools/intonation_levels.py tools/proba-15.mp3 question
        python tools/intonation_levels.py tools/proba-15.mp3 exclaim
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
MODEL = os.path.join(HERE, "v5_cis_base_nostress.pt")
ACCENTOR = os.path.join(ROOT, "app", "src", "main", "assets", "silero", "accentor.ptl")
FFMPEG = os.environ.get(
    "FFMPEG",
    r"C:\Users\cubic\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.0.1-full_build\bin\ffmpeg.exe")
SR = 48000
SPEAKER = "bel_larisa"
VOWELS = "аеёіоуыэюя"
NUMBERS = ["нуль", "перш+ы", "друг+і", "тр+эці", "чацв+ёрты", "п+яты"]
QUESTION = "ты прыйдзеш заўтра?"
EXCLAIM = "якая прыгожая ран+іца!"
# те же таблицы, что в Intonation.kt (индекс = сила)
RISE = [0.0, 0.16, 0.28, 0.40, 0.52, 0.64]
EXCL_BASE = [0.0, 1.05, 1.11, 1.17, 1.23, 1.29]
EXCL_DROP = [0.0, 0.10, 0.20, 0.30, 0.40, 0.50]


def accent(acc, text):
    """«+» перед ударной гласной, по словам; готовые «+» не трогаем."""
    out = []
    for token in re.split(r"(\W+)", text):
        if not token or not any(c.isalpha() for c in token):
            out.append(token); continue
        w = token.lower()
        if "+" in w:
            out.append(token); continue
        vowels = [i for i, c in enumerate(w) if c in VOWELS]
        if len(vowels) < 2:
            out.append(token); continue
        with torch.no_grad():
            row = acc([w])[0][0]
        best = max(range(min(len(vowels), len(row))), key=lambda j: float(row[j]))
        p = vowels[best]
        out.append(token[:p] + "+" + token[p:])
    return "".join(out)


def pitches_for(kind, level, n):
    p = np.ones(n, dtype="float32")
    if kind == "question":
        tail = max(3, int(n * 0.35))
        cut = n - tail
        for i in range(cut, n):
            k = (i - cut + 1) / tail
            p[i] = 1.0 + RISE[level] * k * k
    else:
        p[:] = EXCL_BASE[level]
        last = max(3, int(n * 0.12))
        for i in range(n - last, n):
            k = (i - (n - last) + 1) / last
            p[i] = EXCL_BASE[level] * (1.0 - EXCL_DROP[level] * k)
    return p


def synth(model, pk, acc, text, kind=None, level=0):
    marked = accent(acc, text.lower())
    seq, _ = pk.preprocess_tacotron(marked)
    seq = seq.unsqueeze(0)
    n = seq.shape[1]
    spk = pk.speaker_to_ids[0][SPEAKER]
    pitches = torch.from_numpy(pitches_for(kind, level, n) if kind else np.ones(n, dtype="float32")).unsqueeze(0)
    rates = torch.ones(1, n)
    args = [seq, torch.LongTensor([spk]), SR, None, rates, pitches, None, None, "cpu", -1, False]
    with torch.no_grad():
        out = model(*args)
    return np.asarray(out[0].reshape(-1), dtype="float32")


def main():
    if len(sys.argv) < 3 or sys.argv[2] not in ("question", "exclaim"):
        sys.exit(__doc__)
    dst, kind = sys.argv[1], sys.argv[2]
    text = QUESTION if kind == "question" else EXCLAIM
    big = torch.package.PackageImporter(MODEL).load_pickle("tts_models", "model")
    pk = big.packages[0]; model = pk.models[0]
    acc = _load_for_lite_interpreter(ACCENTOR)

    pieces = []
    for level in range(1, 6):
        print(f"{level}. сила {level}: вопрос {RISE[level]:.2f} / восклицание {EXCL_BASE[level]:.2f} - {EXCL_DROP[level]:.2f}")
        pieces.append(synth(model, pk, acc, NUMBERS[level]))
        pieces.append(np.zeros(int(0.30 * SR), dtype="float32"))
        pieces.append(synth(model, pk, acc, text, kind, level))
        pieces.append(np.zeros(int(1.0 * SR), dtype="float32"))

    audio = np.clip(np.concatenate(pieces), -1, 1)
    tmp = dst + ".wav"
    with wave.open(tmp, "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR)
        f.writeframes((audio * 32767).astype("int16").tobytes())
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", tmp, "-codec:a", "libmp3lame",
                    "-b:a", "64k", "-ac", "1", dst], check=True)
    os.remove(tmp)
    print("звук:", dst, round(os.path.getsize(dst) / 1024), "КБ")


if __name__ == "__main__":
    main()
