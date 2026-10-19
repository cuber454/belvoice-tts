"""Ставит ударения в словаре латинских слов (для Latin.kt) и печатает готовые пары.

Акцентор умеет ставить «+» в словах с двумя и более гласными; слова с одной гласной
(гугл, блог, чат) приходится помечать вручную — их список в MANUAL.
"""
import os
import sys

import torch
from torch.jit.mobile import _load_for_lite_interpreter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ACCENTOR = os.path.join(ROOT, "app", "src", "main", "assets", "silero", "accentor.ptl")
VOWELS = "аеёіоуыэюя"

WORDS = {
    "iphone": "айфон", "ipad": "айпад", "android": "андроід", "google": "гугл",
    "youtube": "ютуб", "telegram": "тэлеграм", "whatsapp": "вацап", "viber": "вайбер",
    "facebook": "фэйсбук", "instagram": "інстаграм", "tiktok": "цікток", "twitter": "твітэр",
    "windows": "віндоўз", "linux": "лінукс", "excel": "эксэль", "word": "ворд",
    "wifi": "вайфай", "bluetooth": "блутуз", "sms": "эс-эм-эс", "pdf": "пэ-дэ-эф",
    "gps": "джы-пі-эс", "tv": "тэ-вэ", "ok": "окей", "film": "фільм",
    "filmbell": "фільмбел", "netflix": "нэтфлікс", "amazon": "амазон",
    "microsoft": "майкрасофт", "email": "імэйл", "online": "анлайн",
    "internet": "інтэрнэт", "site": "сайт", "web": "вэб", "podcast": "подкаст",
    "chrome": "хром", "chromium": "хроміум", "photoshop": "фоташоп", "zoom": "зум",
    "spotify": "спатыфай", "github": "гітхаб", "chatgpt": "чэтджыпіці", "ai": "эй-ай",
    "blog": "блог", "chat": "чат", "link": "лінк", "live": "лайв", "team": "цім",
    "video": "відэа", "audio": "аўдыё", "photo": "фота", "media": "медыя",
    "forum": "форум", "login": "логін", "password": "пароль", "download": "даўнлоўд",
}

# слова с одной гласной: акцентор их не метит, ставим руками
MANUAL = {
    "гугл": "г+угл", "блог": "бл+ог", "чат": "ч+ат", "лінк": "л+інк", "лайв": "л+айв",
    "цім": "ц+ім", "форум": "ф+орум", "зум": "з+ум", "ворд": "в+орд", "фільм": "ф+ільм",
    "хром": "хр+ом", "сайт": "с+айт", "вэб": "в+эб", "шоп": "ш+оп", "фота": "ф+ота",
}
# ручные поправки к акцентору: где он ошибается или где метка лишняя
OVERRIDES = {
    "тэлеграм": "тэлегр+ам", "эс-эм-эс": "эс-эм-эс", "пэ-дэ-эф": "пэ-дэ-эф",
    "джы-пі-эс": "джы-пі-эс", "тэ-вэ": "тэ-вэ", "окей": "ок+ей", "блутуз": "блут+уз",
    "айпад": "айп+ад", "фэйсбук": "фэйсб+ук",
}


def mark(acc, word):
    if "+" in word:
        return word
    if word in OVERRIDES:
        return OVERRIDES[word]
    if word in MANUAL:
        return MANUAL[word]
    vowels = [i for i, c in enumerate(word) if c in VOWELS]
    if len(vowels) < 2:
        return word
    with torch.no_grad():
        row = acc([word])[0][0]
    best = max(range(min(len(vowels), len(row))), key=lambda j: float(row[j]))
    p = vowels[best]
    return word[:p] + "+" + word[p:]


def main():
    acc = _load_for_lite_interpreter(ACCENTOR)
    out = {}
    for en, be in WORDS.items():
        marked = mark(acc, be)
        out[en] = marked
        flag = "" if marked != be else "   ← гласная одна, нет метки"
        print(f'        "{en}" to "{marked}",{flag}')
    print(f"\nвсего слов: {len(out)}")


if __name__ == "__main__":
    sys.exit(main())
