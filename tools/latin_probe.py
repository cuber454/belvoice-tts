"""Прототип транслитерации латиницы в белорусские буквы — тест правил и проба на слух.

Правила те же, что потом лягут в Kotlin (text/Latin.kt): сначала словарь известных слов,
потом английские диграфы, потом по буквам. Аббревиатуры капсом читаются по буквам.
"""
import os
import subprocess
import sys
import wave

import numpy as np
import torch

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MODEL = os.path.join(HERE, "v5_cis_base_nostress.pt")
FFMPEG = os.environ.get(
    "FFMPEG",
    r"C:\Users\cubic\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.0.1-full_build\bin\ffmpeg.exe")
SR = 48000
SPEAKER = "bel_larisa"

# известные слова: как их реально зовут по-белорусски
WORDS = {
    "iphone": "айфон", "ipad": "айпад", "android": "андроід", "google": "гугл",
    "youtube": "ютуб", "telegram": "тэлеграм", "whatsapp": "вацап", "viber": "вайбер",
    "facebook": "фэйсбук", "instagram": "інстаграм", "tiktok": "цікток", "twitter": "твітэр",
    "windows": "віндоўз", "linux": "лінукс", "excel": "эксэль", "word": "ворд",
    "wifi": "вайфай", "wi": "вай", "fi": "фай", "bluetooth": "блутуз", "usb": "у-эс-бэ",
    "sms": "эс-эм-эс", "pdf": "пэ-дэ-эф", "gps": "джы-пі-эс", "tv": "тэ-вэ",
    "ok": "окей", "film": "фільм", "filmbell": "фільмбел", "netflix": "нэтфлікс",
    "amazon": "амазон", "microsoft": "майкрасофт", "email": "імэйл", "online": "анлайн",
    "internet": "інтэрнэт", "site": "сайт", "web": "вэб", "podcast": "подкаст",
    "chrome": "хром", "chromium": "хроміум", "photoshop": "фоташоп", "zoom": "зум",
    "spotify": "спатыфай", "github": "гітхаб", "chatgpt": "чэтджыпіці", "ai": "эй-ай",
}

# диграфы: порядок важен — длинные раньше
DIGRAPHS = [
    ("tch", "ч"), ("sch", "ш"), ("igh", "ай"), ("tion", "шн"), ("sion", "жн"),
    ("ph", "ф"), ("th", "т"), ("sh", "ш"), ("chr", "кр"), ("chl", "кл"), ("chn", "кн"), ("ch", "ч"), ("ck", "к"), ("qu", "кв"),
    ("oo", "у"), ("ee", "і"), ("ea", "і"), ("ai", "эй"), ("ay", "эй"), ("ey", "эй"),
    ("oi", "ой"), ("oy", "ой"), ("ou", "аў"), ("ow", "оў"), ("oa", "оў"), ("ie", "і"),
    ("wh", "ў"), ("ng", "нг"), ("kn", "н"), ("wr", "р"),
]

LETTERS = {
    "a": "а", "b": "б", "c": "к", "d": "д", "e": "е", "f": "ф", "g": "г", "h": "х",
    "i": "і", "j": "дж", "k": "к", "l": "л", "m": "м", "n": "н", "o": "о", "p": "п",
    "q": "к", "r": "р", "s": "с", "t": "т", "u": "у", "v": "в", "w": "ў", "x": "кс",
    "y": "ы", "z": "з",
}
# имена латинских букв — для аббревиатур капсом
NAMES = {
    "a": "а", "b": "бэ", "c": "цэ", "d": "дэ", "e": "е", "f": "эф", "g": "гэ",
    "h": "аш", "i": "і", "j": "джэй", "k": "ка", "l": "эл", "m": "эм", "n": "эн",
    "o": "о", "p": "пэ", "q": "кю", "r": "эр", "s": "эс", "t": "тэ", "u": "у",
    "v": "вэ", "w": "дабл-ю", "x": "экс", "y": "уай", "z": "зэт",
}
VOWELS = "аеёіоуыэюя"
SOFT = "еёіюя"
HARD = "дтзслнрц"


def word(w: str) -> str:
    low = w.lower()
    if low in WORDS:
        out = WORDS[low]
        return out
    if w.isupper() and 2 <= len(w) <= 5:      # USB, OK, PDF
        return "-".join(NAMES.get(c, LETTERS.get(c, "")) for c in low if c in LETTERS)
    s = low
    out = []
    i = 0
    while i < len(s):
        for d, r in DIGRAPHS:
            if s.startswith(d, i):
                if d in ("kn", "wr") and i > 0:
                    continue
                out.append(r)
                i += len(d)
                break
        else:
            c = s[i]
            if c == "c":
                nxt = s[i + 1] if i + 1 < len(s) else ""
                out.append("с" if nxt in "eiy" else "к")
            elif c == "y":
                out.append("й" if (i == 0 or s[i - 1] in "aeiou") else "ы")
            else:
                out.append(LETTERS.get(c, ""))
            i += 1
    res = "".join(out)
    if low.endswith("y") and len(low) > 1 and low[-2] not in "aeiou":
        res = res[:-1] + "і"
    # смягчение: «і» после твёрдых согласных читается твёрдо, поэтому «фільм» получаем через ь
    res = res.replace("ль", "ль")
    # двойные согласные схлопываем
    clean = []
    for ch in res:
        if clean and clean[-1] == ch and ch not in VOWELS:
            continue
        clean.append(ch)
    return "".join(clean)


def translate(text: str) -> str:
    out = []
    i = 0
    while i < len(text):
        if text[i].isascii() and text[i].isalpha():
            j = i
            while j < len(text) and text[j].isascii() and text[j].isalpha():
                j += 1
            out.append(word(text[i:j]))
            i = j
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def main():
    samples = ["FilmBell", "iPhone 15 Pro", "Samsung Galaxy", "USB-порт и Wi-Fi",
               "Спампаваць PDF", "Microsoft Word", "OK, дамовіліся", "Google Chrome"]
    for s in samples:
        print(f"{s:26s} -> {translate(s)}")

    big = torch.package.PackageImporter(MODEL).load_pickle("tts_models", "model")
    pieces = []
    for s in samples:
        t = translate(s).lower()
        with torch.no_grad():
            audio = big.apply_tts(text=t, speaker=SPEAKER, sample_rate=SR)
        pieces.append(np.asarray(audio, dtype="float32").reshape(-1))
        pieces.append(np.zeros(int(1.0 * SR), dtype="float32"))
    audio = np.clip(np.concatenate(pieces), -1, 1)
    tmp = os.path.join(ROOT, "build", "latin.wav")
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    with wave.open(tmp, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes((audio * 32767).astype("int16").tobytes())
    out = os.path.join(ROOT, "build", "latin.mp3")
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", tmp, "-codec:a", "libmp3lame",
                    "-b:a", "64k", "-ac", "1", out], check=True)
    print("\nзвук:", out, round(os.path.getsize(out) / 1024), "КБ")


if __name__ == "__main__":
    sys.exit(main())
