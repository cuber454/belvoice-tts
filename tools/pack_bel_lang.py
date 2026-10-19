"""Пак белорусского языка для RuVoice: берёт готовый пак (pack.json + tts_mel.ptl + backbone.pte +
head.ptl) и добавляет к нему то, что делает язык свойством пака, а не сборки приложения:

  * поле `lang` в pack.json (be);
  * `accentor.ptl` — акцентор этого языка (ExecuTorch, MIT, silero-stress);
  * `stress.txt` — поправки ударений (слово → слово с «+»), их читает BelStress.

Модель Silero (MIT) остаётся в паке без пересборки — байты те же, что у пакета `ruvoice-pack-cis_be`,
только к ним добавлены два файла языка. Формат пака (format 2) не меняется: приложение, которое
про `lang` не знает, читает такой пак как раньше.

Запуск:
  python tools/pack_bel_lang.py <входной.zip> <выходной.zip> [--accentor файл] [--stress файл]
"""
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MODEL = ("tts_mel.ptl", "backbone.pte", "head.ptl")
ACCENTOR = os.path.join(ROOT, "app", "src", "main", "assets", "silero", "accentor.ptl")
STRESS = os.path.join(ROOT, "app", "src", "main", "assets", "dicts", "stress", "Системный.txt")
TITLE = "Беларускія галасы Silero v5 CIS: 31 голас, у тым ліку тры родныя беларускія (bel_larisa, bel_anatoliy, bel_dmitriy)"


def main():
    args = sys.argv[1:]
    if len(args) < 2:
        sys.exit(__doc__)
    src, dst = args[0], args[1]
    acc = args[args.index("--accentor") + 1] if "--accentor" in args else ACCENTOR
    stress = args[args.index("--stress") + 1] if "--stress" in args else STRESS
    with zipfile.ZipFile(src) as zf:
        names = set(zf.namelist())
        if "pack.json" not in names or any(m not in names for m in MODEL):
            sys.exit(f"{src}: это не пак RuVoice (нужны pack.json и {', '.join(MODEL)})")
        pack = json.loads(zf.read("pack.json").decode("utf-8"))
        pack["lang"] = "be"
        pack["title"] = TITLE
        # файлы модели кладём как есть: они уже сжаты (модель — 80+ МБ, повторное сжатие ничего не даёт)
        with zipfile.ZipFile(dst, "w") as out:
            out.writestr("pack.json", json.dumps(pack, ensure_ascii=False), compress_type=zipfile.ZIP_DEFLATED)
            for m in MODEL:
                out.writestr(m, zf.read(m), compress_type=zipfile.ZIP_STORED)
            out.write(acc, "accentor.ptl", compress_type=zipfile.ZIP_STORED)
            out.write(stress, "stress.txt", compress_type=zipfile.ZIP_DEFLATED)
    print(f"пак: {dst}, {os.path.getsize(dst) / 1048576:.1f} МБ, голосов {len(pack['speakers'])}, "
          f"акцентор {os.path.getsize(acc) / 1048576:.1f} МБ, поправок {sum(1 for _ in open(stress, encoding='utf-8'))}")


if __name__ == "__main__":
    main()
