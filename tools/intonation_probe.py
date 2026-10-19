"""Проба интонации: один и тот же текст с разными контурами высоты.

Модель принимает массив pitches по символам, поэтому вопрос можно сделать подъёмом в конце,
а восклицание — повышенным тоном со спадом. Скрипт делает серию и озвучивает номера.

Запуск: python tools/intonation_probe.py <какой набор: question|exclaim> out.mp3
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
NUMBERS = ["нуль", "перш+ы", "друг+і", "тр+эці", "чацв+ёрты", "п+яты", "ш+осты", "с+ёмы",
           "в+осьмы", "дзяв+яты", "дзяся+ты", "адзінаццаты"]

QUESTION = "Ты прыйдзеш заўтра"
EXCLAIM = "Якая прыгожая раніца"


def accent(acc, text):
    """Ставит «+» по словам, акцентором."""
    out = []
    for token in re.split(r"(\W+)", text):
        if not token or not any(c.isalpha() for c in token):
            out.append(token); continue
        w = token.lower()
        vowels = [i for i, c in enumerate(w) if c in VOWELS]
        if len(vowels) < 2:
            out.append(token); continue
        with torch.no_grad():
            row = acc([w])[0][0]
        best = max(range(min(len(vowels), len(row))), key=lambda j: float(row[j]))
        p = vowels[best]
        out.append(token[:p] + "+" + token[p:])
    return "".join(out)


def contour(name, n):
    """Массив коэффициентов высоты по символам."""
    p = np.ones(n, dtype="float32")
    tail = max(1, int(n * 0.35))
    cut = n - tail
    if name == "base":
        return p
    if name == "rise_smooth":                     # плавный подъём на последней трети
        for i in range(cut, n):
            p[i] = 1.0 + 0.30 * (i - cut) / tail
    elif name == "rise_last":                     # подъём только в конце, круче
        for i in range(cut, n):
            p[i] = 1.0 + 0.45 * ((i - cut) / tail) ** 2
    elif name == "rise_high":                     # сильный подъём
        for i in range(cut, n):
            p[i] = 1.0 + 0.60 * (i - cut) / tail
    elif name == "excl_all":                      # всё выше на 15 %
        p[:] = 1.15
    elif name == "excl_fall":                     # выше и резкий спад в конце
        p[:] = 1.15
        last = max(1, int(n * 0.15))
        for i in range(n - last, n):
            p[i] = 1.15 - 0.35 * (i - (n - last)) / last
    elif name == "excl_rise_fall":                # подъём к концу и обрыв
        p[:] = 1.10
        for i in range(cut, n):
            p[i] = 1.10 + 0.30 * (i - cut) / tail
        last = max(1, int(n * 0.10))
        for i in range(n - last, n):
            p[i] = 1.40 - 0.60 * (i - (n - last)) / last
    return p


def rates_for(name, n):
    r = np.ones(n, dtype="float32")
    tail = max(1, int(n * 0.35))
    if name == "rise_slow":                       # подъём + замедление в конце
        cut = n - tail
        for i in range(cut, n):
            r[i] = 1.0 - 0.20 * (i - cut) / tail
    return r


def synth(model, pk, acc, text, contour_name="base"):
    marked = accent(acc, text.lower())   # в таблице символов модели только строчные
    seq, _ = pk.preprocess_tacotron(marked)
    seq = seq.unsqueeze(0)
    n = seq.shape[1]
    spk = pk.speaker_to_ids[0][SPEAKER]
    pitches = torch.from_numpy(contour(contour_name, n)).unsqueeze(0)
    rates = torch.from_numpy(rates_for(contour_name, n)).unsqueeze(0)
    args = [seq, torch.LongTensor([spk]), SR, None, rates, pitches, None, None, "cpu", -1, False]
    with torch.no_grad():
        out = model(*args)
    return np.asarray(out[0].reshape(-1), dtype="float32")


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    kind, dst = sys.argv[1], sys.argv[2]
    if kind == "question":
        text = QUESTION + "?"
        items = [("база (як зараз)", "base"), ("плавны пад'ём", "rise_smooth"),
                 ("пад'ём у канцы", "rise_last"), ("моцны пад'ём", "rise_high"),
                 ("пад'ём + запаволенне", "rise_slow")]
    else:
        text = EXCLAIM + "!"
        items = [("база (як зараз)", "base"), ("усё вышэй", "excl_all"),
                 ("вышэй + спад", "excl_fall"), ("пад'ём і абрыў", "excl_rise_fall")]

    big = torch.package.PackageImporter(MODEL).load_pickle("tts_models", "model")
    pk = big.packages[0]; model = pk.models[0]
    acc = _load_for_lite_interpreter(ACCENTOR)

    pieces = []
    for i, (label, cname) in enumerate(items, start=1):
        num = NUMBERS[i]
        print(f"{i}. {label:22s} контур {cname}")
        pieces.append(synth(model, pk, acc, num, "base"))
        pieces.append(np.zeros(int(0.35 * SR), dtype="float32"))
        pieces.append(synth(model, pk, acc, text, cname))
        pieces.append(np.zeros(int(1.1 * SR), dtype="float32"))

    audio = np.clip(np.concatenate(pieces), -1, 1)
    tmp = dst + ".wav"
    with wave.open(tmp, "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR)
        f.writeframes((audio * 32767).astype("int16").tobytes())
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", tmp, "-codec:a", "libmp3lame",
                    "-b:a", "64k", "-ac", "1", dst], check=True)
    print("звук:", dst, round(os.path.getsize(dst) / 1024), "КБ")


if __name__ == "__main__":
    main()
