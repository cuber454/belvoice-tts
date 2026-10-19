"""Разбор белорусского словаря ударений bel.dic: формат, объём, сверка с акцентором.

Формат файла: <слово>\t<слово с ´ после ударной гласной>\t<число>\t<число>
Проверяет: сколько записей, где акут стоит не по правилам, и как часто словарь
расходится с нейросетевым акцентором (accentor.ptl). Пишет:
  - build/bel-stress.txt        — словарь в формате приложения: «слово слово+с_плюсом»
  - build/bel-stress-diff.txt   — только расхождения с акцентором (кандидат в системный словарь)
"""
import os
import random
import sys
import time

import torch
from torch.jit.mobile import _load_for_lite_interpreter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, "bel.dic")
OUT = os.path.join(ROOT, "build")
ACCENTOR = os.path.join(ROOT, "app", "src", "main", "assets", "silero", "accentor.ptl")
SAMPLE = 0          # 0 — сверять всё, иначе только выборку

VOWELS = "аеёіоуыэюя"


def parse():
    print(f"читаю {SRC}")
    words = []          # (word, stressed_with_plus)
    bad = []            # строки, где не разобрались
    dup = 0
    seen = set()
    t0 = time.time()
    with open(SRC, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.rstrip("\r\n")
            if not line:
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                if len(bad) < 5:
                    bad.append(line)
                continue
            word = parts[0].strip().lower()
            if " " in word:          # ключ словаря ударений — одно слово
                continue
            marked = parts[1].strip()
            if "´" not in marked:
                if len(bad) < 5:
                    bad.append(line)
                continue
            pos = marked.index("´")
            # акут идёт ПОСЛЕ ударной гласной; проверяем, что перед ним гласная
            if pos == 0 or marked[pos - 1] not in VOWELS:
                if len(bad) < 5:
                    bad.append(line)
                continue
            clean = marked.replace("´", "")
            if clean != word:
                if len(bad) < 5:
                    bad.append(line)
                continue
            stressed = clean[: pos - 1] + "+" + clean[pos - 1:]
            if word in seen:
                dup += 1
                continue
            seen.add(word)
            words.append((word, stressed))
    print(f"записей: {len(words):,} (дублей пропущено {dup:,}), время {time.time() - t0:.0f} c")
    if bad:
        print("первые непонятные строки:")
        for b in bad:
            print("   ", b[:120])
    return words


def expected(acc, words):
    """Позиции ударения по акцентору: индекс 'ударной гласной среди гласных'."""
    print(f"сверяю с акцентором на выборке {SAMPLE:,} слов")
    random.seed(42)
    pick = words if SAMPLE <= 0 or len(words) <= SAMPLE else random.sample(words, SAMPLE)
    texts = [w for w, _ in pick]
    out = []
    B = 1000
    t0 = time.time()
    for i in range(0, len(texts), B):
        batch = texts[i:i + B]
        with torch.no_grad():
            probs = acc(batch)[0]
        for w, row in zip(batch, probs):
            vowels = [k for k, c in enumerate(w) if c in VOWELS]
            if not vowels:
                out.append(None)
                continue
            best, bestp = 0, float("-inf")
            for j in range(min(len(vowels), len(row))):
                v = float(row[j])
                if v > bestp:
                    bestp, best = v, j
            out.append(vowels[best])
    print(f"акцентор: {time.time() - t0:.0f} c")
    return dict(zip(texts, out))


def main():
    os.makedirs(OUT, exist_ok=True)
    words = parse()

    lite = _load_for_lite_interpreter(ACCENTOR)
    pred = expected(lite, words)

    diff, agree, miss = [], 0, 0
    for word, stressed in words:
        idx = stressed.index("+")
        dict_pos = idx
        if not any(c in VOWELS for c in word):
            miss += 1
            continue
        p = pred.get(word)
        if p is None:
            continue
        if p == dict_pos:
            agree += 1
        else:
            diff.append((word, dict_pos, p))

    total = agree + len(diff)
    print(f"\nсверено слов: {total:,}")
    print(f"совпало:       {agree:,} ({100.0 * agree / max(1, total):.1f} %)")
    print(f"расхождений:   {len(diff):,} ({100.0 * len(diff) / max(1, total):.1f} %)")
    if miss:
        print(f"без гласных:   {miss:,}")

    print("\nпримеры расхождений (слово — по словарю / по акцентору):")
    for word, dpos, ppos in diff[:20]:
        dp = word[:dpos] + "+" + word[dpos:]
        pp = word[:ppos] + "+" + word[ppos:]
        print(f"   {word:16s} {dp:18s} | {pp}")

    def dump(path, rows):
        with open(path, "w", encoding="utf-8") as f:
            f.write("# поправки ударений из bel.dic (акут → «+» перед ударной гласной)\n")
            for w, s in rows:
                f.write(f"{w} {s}\n")
        print(f"записано: {path} — {len(rows):,} строк, {os.path.getsize(path) / 1048576:.1f} МБ")

    dump(os.path.join(OUT, "bel-stress.txt"), words)
    only = {w for w, _, _ in diff}
    dump(os.path.join(OUT, "bel-stress-diff.txt"), [(w, s) for w, s in words if w in only])

    print("\nпроверка известных слов:")
    lookup = dict(words)
    for w in ("малако", "дзякуй", "дзякуе", "вучыць", "красы", "замак", "мука", "шчасця", "здароўя", "паясы"):
        print(f"   {w:10s} {lookup.get(w, '— нет в словаре')}")


if __name__ == "__main__":
    sys.exit(main())
