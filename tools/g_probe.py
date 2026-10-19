"""Проба белорусского «г»: контрольные слова + варианты записи «Google».

Печатает варианты и пишет mp3, чтобы Серж выбрал на слух. Заодно показывает, ставится ли
ударение (для одного гласного акцентор «+» не ставит — возможно, из-за этого и звучит чужо).
"""
import os
import subprocess
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

# контроль: в белорусском «гор..», «гар..», «гул..» — фрикативное [ɣ]
CONTROL = ["горад", "гара", "гуляць", "гусь", "гэта", "нага", "магу"]
# варианты записи Google
VARIANTS = ["гугл", "г+угл", "гугэл", "гугел", "гугль", "гугал"]


def accentor_mark(acc, word):
    """Как акцентор поставил бы «+» (для сравнения)."""
    if "+" in word or "´" in word:
        return word
    vowels = [i for i, c in enumerate(word) if c in VOWELS]
    if len(vowels) < 2:
        return word + "  (гласная одна — акцентор не метит)"
    with torch.no_grad():
        row = acc([word])[0][0]
    best = max(range(min(len(vowels), len(row))), key=lambda j: float(row[j]))
    p = vowels[best]
    return word[:p] + "+" + word[p:]


def main():
    big = torch.package.PackageImporter(MODEL).load_pickle("tts_models", "model")
    acc = _load_for_lite_interpreter(ACCENTOR)

    print("контроль (как модель читает «г» в родных словах):")
    for w in CONTROL:
        print(f"   {w:12s} акцентор: {accentor_mark(acc, w)}")
    print("\nварианты Google:")
    for w in VARIANTS:
        print(f"   {w:12s} акцентор: {accentor_mark(acc, w)}")

    pieces = []
    for w in CONTROL + VARIANTS:
        t = accentor_mark(acc, w)
        t = t.split("  ")[0]
        with torch.no_grad():
            audio = big.apply_tts(text=t, speaker=SPEAKER, sample_rate=SR)
        pieces.append(np.asarray(audio, dtype="float32").reshape(-1))
        pieces.append(np.zeros(int(1.0 * SR), dtype="float32"))

    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    audio = np.clip(np.concatenate(pieces), -1, 1)
    tmp = os.path.join(ROOT, "build", "g.wav")
    with wave.open(tmp, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes((audio * 32767).astype("int16").tobytes())
    out = os.path.join(ROOT, "build", "g.mp3")
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", tmp, "-codec:a", "libmp3lame",
                    "-b:a", "64k", "-ac", "1", out], check=True)
    print("\nзвук:", out, round(os.path.getsize(out) / 1024), "КБ")


if __name__ == "__main__":
    main()
