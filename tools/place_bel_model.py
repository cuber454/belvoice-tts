"""Кладёт белорусскую модель из пака в assets приложения.

Пак `dist/ruvoice-pack-cis_be.zip` собирается `tools/export_pack.py cis_be` (см. README сборки).
Модель в git не хранится (она в .gitignore, как и у автора): после свежего клона её надо положить
этим скриптом, иначе `:app:assembleDebug` соберёт APK без модели.
"""
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PACK = os.path.join(ROOT, "dist", "ruvoice-pack-cis_be.zip")
ASSETS = os.path.join(ROOT, "app/src/full/assets/silero")   # модель только в полной сборке
FILES = ("tts_mel.ptl", "head.ptl", "backbone.pte")


def main():
    if not os.path.isfile(PACK):
        sys.exit(f"нет пака: {PACK}\nсоберите: cd tools && python export_pack.py cis_be")
    os.makedirs(ASSETS, exist_ok=True)
    with zipfile.ZipFile(PACK) as z:
        for name in FILES:
            target = os.path.join(ASSETS, name)
            with z.open(name) as src, open(target, "wb") as dst:
                dst.write(src.read())
            print(f"{name}: {os.path.getsize(target) / 1048576:.1f} МБ -> {target}")


if __name__ == "__main__":
    main()
