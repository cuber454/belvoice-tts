"""Экспорт белорусского акцентора silero-stress (MIT) в accentor.ptl для приложения.

Что делает:
  1) берёт accentor-bel.pt из пакета silero-stress (без pip-обёртки, сырой torch.package);
  2) деквантует таблицу эмбеддингов так же, как silero_stress.load_accentor(lang='bel');
  3) сохраняет модуль в TorchScript Lite (accentor.ptl) — обычный LiteModuleLoader в приложении;
  4) сверяет выход lite-модуля с исходным на белорусских словах и печатает вероятности
     по гласным, чтобы видеть, куда акцентор поставит «+».

Запуск:  python tools/export_bel_accentor.py
"""
import os
import sys

import torch
from torch.jit.mobile import _load_for_lite_interpreter

import silero_stress

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "app/src/main/assets/silero")
PT = os.path.join(os.path.dirname(silero_stress.__file__), "data", "accentor-bel.pt")

WORDS = ["дзякуй", "вучыць", "прыйду", "заўтра", "беларуская", "мовы", "красы",
         "бяздольныя", "узяты", "ткаць", "залатыя", "паясы", "шчасця", "здароўя"]


def main():
    os.makedirs(ASSETS, exist_ok=True)
    print("пакет:", PT, round(os.path.getsize(PT) / 1048576, 1), "МБ")
    ss = torch.package.PackageImporter(PT).load_pickle("accentor_models", "accentor")
    print("тип:", type(ss).__name__, "| есть .accentor:", hasattr(ss, "accentor"))
    acc = ss.accentor if hasattr(ss, "accentor") else ss
    model = acc.model

    # деквантование как в silero_stress.load_accentor: эмбеддинги лежат int8 + scale/zero_point
    qw = model.embedding.weight.data.clone()
    model.embedding.weight.data = model.scale * (qw - model.zero_point)
    print("эмбеддинги:", tuple(model.embedding.weight.shape), "-> float")

    out = model(WORDS)
    out = out if isinstance(out, (tuple, list)) else (out,)
    print("выходов у акцентора:", len(out))
    for i, t in enumerate(out):
        print(f"  выход {i}: " + ("None (у белорусского канала «ё» нет)" if t is None else f"{tuple(t.shape)}"))

    path = os.path.join(ASSETS, "accentor.ptl")
    with torch.no_grad():
        model._save_for_lite_interpreter(path)
    print("сохранено:", path, round(os.path.getsize(path) / 1048576, 2), "МБ")

    lite = _load_for_lite_interpreter(path)
    with torch.no_grad():
        lo = lite(WORDS)
        ro = model(WORDS)
    lo = lo if isinstance(lo, (tuple, list)) else (lo,)
    ro = ro if isinstance(ro, (tuple, list)) else (ro,)
    for i, (a, b) in enumerate(zip(lo, ro)):
        if a is None or b is None:
            print(f"сверка выхода {i}: None у обоих — пропуск")
            continue
        d = (a - b).abs().max().item()
        print(f"сверка выхода {i}: maxdiff {d:.2e}")
        assert d < 1e-5, f"выход {i} не совпал"

    # куда ставится ударение: для каждого слова — самая вероятная гласная
    vowels = "аоуіэыяеёю"
    stress = lo[0] if lo[0] is not None else None
    print("\nударение по акцентору:")
    for w, probs in zip(WORDS, stress):
        vs = [i for i, c in enumerate(w) if c in vowels]
        n = min(len(vs), len(probs))
        if not vs:
            print(f"  {w}: нет гласных")
            continue
        k = int(torch.argmax(probs[:n]))
        marked = w[: vs[k]] + "+" + w[vs[k]:]
        print(f"  {w:14s} -> {marked:15s} p={float(probs[k]):.2f}")
    print("\nготово")


if __name__ == "__main__":
    main()
